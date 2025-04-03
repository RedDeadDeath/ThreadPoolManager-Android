package org.thread.controlpools;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPoolManager {
    private static final String TAG = "ThreadPoolManager";
    private static final int DEFAULT_CORE_POOL_SIZE = 1;
    private static final int DEFAULT_MAX_POOL_SIZE = 4;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 30L;
    private static final int DEFAULT_QUEUE_CAPACITY = 10;

    private PausableThreadPoolExecutor pausableExecutor;
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final List<WeakReference<ThreadPoolStateListener>> stateListeners = new ArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;

    public ThreadPoolManager(Context context) {
        initDefaultPool(context);
    }

    public ThreadPoolManager(int corePoolSize, int maxPoolSize, long keepAliveTime, int queueCapacity) {
        initCustomPool(corePoolSize, maxPoolSize, keepAliveTime, queueCapacity);
    }

    public ExecutorService getExecutorService() {
        return pausableExecutor;
    }

    public void removeCompletedTasks() {
        Iterator<Runnable> iterator = pausableExecutor.getQueue().iterator();
        while (iterator.hasNext()) {
            Runnable task = iterator.next();
            if (task instanceof Future && ((Future<?>) task).isDone()) {
                iterator.remove();
            }
        }
    }

    private void initDefaultPool(Context context) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        corePoolSize = Math.max(DEFAULT_CORE_POOL_SIZE, availableProcessors / 2);
        maxPoolSize = Math.max(DEFAULT_MAX_POOL_SIZE, availableProcessors);
        queueCapacity = DEFAULT_QUEUE_CAPACITY;

        if (isWeakDevice(context)) {
            corePoolSize = 1;
            maxPoolSize = 2;
            Log.i(TAG, "Weak device detected, reducing pool size.");
        }

        initCustomPool(corePoolSize, maxPoolSize, DEFAULT_KEEP_ALIVE_TIME, queueCapacity);
    }

    private void initCustomPool(int corePoolSize, int maxPoolSize, long keepAliveTime, int queueCapacity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            maxPoolSize = Math.min(maxPoolSize, 2);
            keepAliveTime = 60L;
        }

        this.queueCapacity = queueCapacity;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        ThreadFactory threadFactory = new PriorityThreadFactory(Thread.NORM_PRIORITY);

        this.pausableExecutor = new PausableThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory);
    }

    private boolean isWeakDevice(Context context) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long totalMemory = memoryInfo.totalMem;

        long FOUR_GB_IN_BYTES = 4L * 1024 * 1024 * 1024;
        long NINE_GB_IN_BYTES = 9L * 1024 * 1024 * 1024;
        long SIXTEEN_GB_IN_BYTES = 16L * 1024 * 1024 * 1024;

        boolean isLowMemoryDevice = totalMemory <= FOUR_GB_IN_BYTES;
        boolean isMediumMemoryDevice = totalMemory > FOUR_GB_IN_BYTES && totalMemory <= NINE_GB_IN_BYTES;
        boolean isHighMemoryDevice = totalMemory > NINE_GB_IN_BYTES && totalMemory <= SIXTEEN_GB_IN_BYTES;
        boolean isVeryHighMemoryDevice = totalMemory > SIXTEEN_GB_IN_BYTES;

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isBatterySaverOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                powerManager.isPowerSaveMode();

        boolean isInLowPerformanceMode = isBatterySaverOn || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

        String cpuArch = Build.SUPPORTED_ABIS[0];
        boolean isOldCpuArchitecture = cpuArch.contains("armv7") || cpuArch.contains("x86");

        boolean isOldOS = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

        if (isOldOS || availableProcessors <= 2 || isLowMemoryDevice || isOldCpuArchitecture || isInLowPerformanceMode) {
            return true;
        }

        if (isVeryHighMemoryDevice) {
            return false;
        }

        if (isMediumMemoryDevice || isHighMemoryDevice) {
            return false;
        }

        return false;
    }









    public ThreadPoolJob execute(Runnable task) {
        if (task == null) {
            Log.e(TAG, "Null task submitted");
            return null;
        }

        try {
            if (isShuttingDown.get()) {
                Log.e(TAG, "Thread pool is shutting down, task submission is not allowed.");
                return null;
            }

            Runnable weakTask = new WeakRunnable(task);
            return executeInternal(weakTask);

        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task submission rejected: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error submitting task: " + e.getMessage(), e);
        }

        return null;
    }


    public <T> ThreadPoolJob execute(Callable<T> task) {
        if (task == null) {
            Log.e(TAG, "Null task submitted");
            return null;
        }

        try {
            if (isShuttingDown.get()) {
                Log.e(TAG, "Thread pool is shutting down, task submission is not allowed.");
                return null;
            }

            Callable<T> weakTask = new WeakCallable<>(task);
            return executeInternal(weakTask);

        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task submission rejected: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error submitting task: " + e.getMessage(), e);
        }

        return null;
    }


    private <T> ThreadPoolJob executeInternal(Callable<T> task) {
        try {
            if (isShuttingDown.get()) {
                Log.e(TAG, "Thread pool is shutting down, task cannot be submitted.");
                return null;
            }
            Future<T> future = pausableExecutor.submit(task);
            return new ThreadPoolJob(future);
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task rejected: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error submitting task: " + e.getMessage());
            return null;
        }
    }

    private ThreadPoolJob executeInternal(Runnable task) {
        try {
            if (isShuttingDown.get()) {
                Log.e(TAG, "Thread pool is shutting down, task cannot be submitted.");
                return null;
            }
            Future<?> future = pausableExecutor.submit(task);
            return new ThreadPoolJob(future);
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task rejected: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error submitting task: " + e.getMessage());
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public <T> List<ThreadPoolJob> executeTasks(List<Callable<T>> tasks) {
        List<ThreadPoolJob> jobs = new ArrayList<>();
        if (tasks == null) {
            return jobs;
        }

        try {
            if (isShuttingDown.get()) {
                Log.e(TAG, "Thread pool is shutting down, tasks cannot be submitted.");
                return jobs;
            }

            for (Callable<T> task : tasks) {
                if (task != null) {
                    Callable<T> weakTask = new WeakCallable<>(task);
                    Future<T> future = pausableExecutor.submit(weakTask);
                    jobs.add(new ThreadPoolJob(future));
                }
            }
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task rejected: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error submitting tasks: " + e.getMessage());
        }

        return jobs;
    }

    public void pause() {
        pauseLock.lock();
        try {
            if (pausableExecutor != null) {
                pausableExecutor.pause();
                isPaused.set(true);
                notifyPoolPaused();
                Log.i(TAG, "Thread pool paused");
            }
        } finally {
            pauseLock.unlock();
        }
    }

    public void resume() {
        pauseLock.lock();
        try {
            if (pausableExecutor != null) {
                pausableExecutor.resume();
                isPaused.set(false);
                notifyPoolResumed();
                Log.i(TAG, "Thread pool resumed");
            }
        } finally {
            pauseLock.unlock();
        }
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public void close() {
        if (!isShuttingDown.getAndSet(true)) {
            try {

                clearDeadListeners();

                scheduler.shutdownNow();

                pausableExecutor.shutdown();
                if (!pausableExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    pausableExecutor.shutdownNow();
                }
                notifyPoolShutDown();
                Log.i(TAG, "Thread pool shut down");
            } catch (InterruptedException e) {
                pausableExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public interface ThreadPoolStateListener {
        void onPoolPaused();
        void onPoolResumed();
        void onPoolShutDown();
    }

    public void addStateListener(ThreadPoolStateListener listener) {
        if (listener != null) {
            stateListeners.add(new WeakReference<>(listener));
        }
    }

    public void removeStateListener(ThreadPoolStateListener listener) {
        if (listener != null) {
            stateListeners.removeIf(ref -> ref.get() == null || ref.get() == listener);
        }
    }

    private void clearDeadListeners() {
        stateListeners.removeIf(ref -> ref.get() == null);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void notifyPoolPaused() {
        for (WeakReference<ThreadPoolStateListener> ref : stateListeners) {
            ThreadPoolStateListener listener = ref.get();
            if (listener != null) {
                listener.onPoolPaused();
            }
        }
        clearDeadListeners();
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void notifyPoolResumed() {
        for (WeakReference<ThreadPoolStateListener> ref : stateListeners) {
            ThreadPoolStateListener listener = ref.get();
            if (listener != null) {
                listener.onPoolResumed();
            }
        }
        clearDeadListeners();
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void notifyPoolShutDown() {
        for (WeakReference<ThreadPoolStateListener> ref : stateListeners) {
            ThreadPoolStateListener listener = ref.get();
            if (listener != null) {
                listener.onPoolShutDown();
            }
        }
        stateListeners.clear();
    }

    public static CompletableFuture<Void> delay(long timeMillis) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        scheduler.schedule(() -> future.complete(null), timeMillis, TimeUnit.MILLISECONDS);

        return future;
    }


    public CompletableFuture<Void> executeWithUiUpdate(
            Runnable backgroundTask,
            Runnable uiUpdateTask,
            boolean runOnMainThread
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        execute(() -> {
            try {
                // Выполняем фоновую задачу
                if (backgroundTask != null) {
                    backgroundTask.run();
                }

                // Обновляем UI
                if (uiUpdateTask != null) {
                    if (runOnMainThread) {
                        runOnMainThread(uiUpdateTask);
                    } else {
                        uiUpdateTask.run();
                    }
                }

                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
                Log.e(TAG, "Error in executeWithUiUpdate", e);
            }
        });

        return future;
    }

    private static void runOnMainThread(Runnable uiTask) {
        if (uiTask != null && Looper.getMainLooper() != null) {
            new Handler(Looper.getMainLooper()).post(uiTask);
        }
    }

    private static class WeakRunnable implements Runnable {
        private final WeakReference<Runnable> weakRef;

        WeakRunnable(Runnable task) {
            this.weakRef = new WeakReference<>(task);
        }

        @Override
        public void run() {
            Runnable task = weakRef.get();
            if (task != null) {
                task.run();
            }
        }
    }

    private static class WeakCallable<T> implements Callable<T> {
        private final WeakReference<Callable<T>> weakRef;

        WeakCallable(Callable<T> task) {
            this.weakRef = new WeakReference<>(task);
        }

        @Override
        public T call() throws Exception {
            Callable<T> task = weakRef.get();
            if (task != null) {
                return task.call();
            }
            return null;
        }
    }




}