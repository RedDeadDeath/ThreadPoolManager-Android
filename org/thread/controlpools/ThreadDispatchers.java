package org.thread.controlpools;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadDispatchers {
    private static final int MIN_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static volatile ExecutorService defaultExecutor;
    private static final Map<WeakReference<Object>, List<CancellableTask>> scopeTasks = new ConcurrentHashMap<>();

    // IO dispatcher (unbounded thread pool with improved control)
    public static final Executor IO = new ControlledCachedThreadPool();

    // Main thread dispatcher
    public static final Executor MAIN = new HandlerExecutor(Looper.getMainLooper());

    // Default dispatcher (CPU-bound tasks)
    public static synchronized ExecutorService DEFAULT(Context context) {
        if (defaultExecutor == null || defaultExecutor.isShutdown()) {
            defaultExecutor = createDefaultExecutor(context);
        }
        return defaultExecutor;
    }

    private static ExecutorService createDefaultExecutor(Context context) {
        int poolSize = calculateOptimalPoolSize(context);
        return new ControlledThreadPoolExecutor(
                poolSize,
                poolSize,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private int counter = 0;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "CPU-Thread-" + (counter++));
                        t.setPriority(Thread.NORM_PRIORITY);
                        t.setUncaughtExceptionHandler((thread, e) ->
                                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, e));
                        return t;
                    }
                });
    }

    // Calculate optimal pool size based on device capabilities
    private static int calculateOptimalPoolSize(Context context) {
        if (context == null || isWeakDevice(context)) {
            return MIN_POOL_SIZE;
        }
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return Math.max(MIN_POOL_SIZE, Math.min(MAX_POOL_SIZE, availableProcessors));
    }

    public static boolean isWeakDevice(Context context) {
        if (context == null) return true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (availableProcessors <= 2) {
            return true;
        }

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return true;
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long TWO_GB_IN_BYTES = 2L * 1024 * 1024 * 1024;
        return memoryInfo.totalMem <= TWO_GB_IN_BYTES || memoryInfo.lowMemory;
    }

    public static class CancellableTask implements Runnable, Cancellable {
        private final Runnable task;
        private final AtomicBoolean isCancelled = new AtomicBoolean(false);
        private final AtomicBoolean isCompleted = new AtomicBoolean(false);
        private final List<Runnable> onCompleteCallbacks = new ArrayList<>();
        private final List<Consumer<Throwable>> onErrorCallbacks = new ArrayList<>();
        private final List<Runnable> onCancelCallbacks = new ArrayList<>();

        public CancellableTask(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            if (isCancelled.get()) return;

            try {
                task.run();
                if (!isCancelled.get()) {
                    isCompleted.set(true);
                    notifyComplete();
                }
            } catch (Exception e) {
                notifyError(e);
            }
        }

        @Override
        public void cancel() {
            if (isCancelled.compareAndSet(false, true)) {
                notifyCancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return isCancelled.get();
        }

        public boolean isCompleted() {
            return isCompleted.get();
        }

        public CancellableTask onComplete(Runnable callback) {
            if (isCompleted.get()) {
                callback.run();
            } else {
                synchronized (onCompleteCallbacks) {
                    onCompleteCallbacks.add(callback);
                }
            }
            return this;
        }

        public CancellableTask onError(Consumer<Throwable> callback) {
            synchronized (onErrorCallbacks) {
                onErrorCallbacks.add(callback);
            }
            return this;
        }

        public CancellableTask onCancel(Runnable callback) {
            if (isCancelled.get()) {
                callback.run();
            } else {
                synchronized (onCancelCallbacks) {
                    onCancelCallbacks.add(callback);
                }
            }
            return this;
        }

        private void notifyComplete() {
            synchronized (onCompleteCallbacks) {
                for (Runnable callback : onCompleteCallbacks) {
                    callback.run();
                }
            }
        }

        private void notifyError(Throwable e) {
            synchronized (onErrorCallbacks) {
                for (Consumer<Throwable> callback : onErrorCallbacks) {
                    callback.accept(e);
                }
            }
        }

        private void notifyCancel() {
            synchronized (onCancelCallbacks) {
                for (Runnable callback : onCancelCallbacks) {
                    callback.run();
                }
            }
        }
    }

    public interface Consumer<T> {
        void accept(T t);
    }

    public interface Cancellable {
        void cancel();
        boolean isCancelled();
    }

    private static class ControlledThreadPoolExecutor extends ThreadPoolExecutor {
        ControlledThreadPoolExecutor(int corePoolSize, int maxPoolSize, long keepAliveTime,
                                     TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                     ThreadFactory threadFactory) {
            super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        @Override
        public Future<?> submit(Runnable task) {
            if (task instanceof CancellableTask) {
                return super.submit(task);
            }
            CancellableTask cancellableTask = new CancellableTask(task);
            return super.submit(cancellableTask);
        }
    }

    private static class ControlledCachedThreadPool implements Executor {
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "IO-Thread-" + System.currentTimeMillis());
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((thread, e) ->
                    Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, e));
            return t;
        });

        @Override
        public void execute(Runnable command) {
            if (command instanceof CancellableTask) {
                executor.execute(command);
            } else {
                executor.execute(new CancellableTask(command));
            }
        }
    }

    private static class HandlerExecutor implements Executor {
        private final Handler handler;

        HandlerExecutor(Looper looper) {
            this.handler = new Handler(looper);
        }

        @Override
        public void execute(Runnable command) {
            if (command == null) {
                throw new IllegalArgumentException("Runnable cannot be null");
            }
            handler.post(command);
        }
    }

    public static CancellableTask Default(Object scope, Runnable task) {
        CancellableTask cancellableTask = new CancellableTask(task);
        trackTask(scope, cancellableTask);
        DEFAULT(null).submit(cancellableTask);
        return cancellableTask;
    }

    public static CancellableTask IO(Object scope, Runnable task) {
        CancellableTask cancellableTask = new CancellableTask(task);
        trackTask(scope, cancellableTask);
        IO.execute(cancellableTask);
        return cancellableTask;
    }

    public static CancellableTask Main(Object scope, Runnable task) {
        CancellableTask cancellableTask = new CancellableTask(task);
        trackTask(scope, cancellableTask);
        MAIN.execute(cancellableTask);
        return cancellableTask;
    }


    private static void trackTask(Object scope, CancellableTask task) {
        if (scope == null) return;

        WeakReference<Object> weakScope = new WeakReference<>(scope);

        scopeTasks.compute(weakScope, (key, tasks) -> {
            if (tasks == null) {
                tasks = new ArrayList<>();
            }
            tasks.add(task);

            tasks.removeIf(t -> t.isCompleted() || t.isCancelled());
            return tasks;
        });

        task.onComplete(() -> untrackTask(weakScope, task))
                .onCancel(() -> untrackTask(weakScope, task));
    }

    private static void untrackTask(WeakReference<Object> weakScope, CancellableTask task) {
        if (weakScope.get() == null) return;

        scopeTasks.computeIfPresent(weakScope, (key, tasks) -> {
            tasks.remove(task);
            return tasks.isEmpty() ? null : tasks;
        });
    }

    public static void cancelScope(Object scope) {
        List<CancellableTask> tasks = scopeTasks.remove(new WeakReference<>(scope));
        if (tasks != null) {
            for (CancellableTask task : tasks) {
                task.cancel();
            }
        }
    }

    public static CancellableTask executeWithCallback(Runnable task, Runnable onComplete) {
        CancellableTask cancellableTask = new CancellableTask(task);
        cancellableTask.onComplete(onComplete);
        DEFAULT(null).submit(cancellableTask);
        return cancellableTask;
    }

    public static synchronized void close() {
        if (defaultExecutor != null && !defaultExecutor.isShutdown()) {
            defaultExecutor.shutdownNow();
        }
        scopeTasks.clear();
    }
}
