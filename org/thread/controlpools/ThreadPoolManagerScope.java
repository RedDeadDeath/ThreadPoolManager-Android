package org.thread.controlpools;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ThreadPoolManagerScope implements AutoCloseable {
    private final ThreadPoolManager threadPoolManager;
    private final Set<ThreadPoolJob> activeJobs = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private volatile Consumer<Throwable> globalExceptionHandler;
    private final List<ThreadPoolManagerScope> childScopes = Collections.synchronizedList(new ArrayList<>());
    private ThreadPoolManagerScope parentScope;



    public ThreadPoolManagerScope(ThreadPoolManager threadPoolManager, ThreadPoolManagerScope parentScope) {
        if (threadPoolManager == null) {
            throw new IllegalArgumentException("ThreadPoolManager cannot be null");
        }
        this.threadPoolManager = threadPoolManager;
        this.parentScope = parentScope;

        if (parentScope != null) {
            parentScope.addChildScope(this);
        }

        startAutoCleanup();
    }

    public boolean isActive() {
        return isActive.get() && (parentScope == null || parentScope.isActive());
    }

    public ThreadPoolJob launch(Runnable task) {
        return launch(task, null);
    }

    public ThreadPoolJob launch(Runnable task, Consumer<Throwable> exceptionHandler) {
        if (!isActive()) {
            throw new IllegalStateException("Scope is not active");
        }

        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        ThreadPoolJob job = threadPoolManager.execute(() -> {
            if (!isActive()) {
                throw new CancellationException("Scope was cancelled");
            }

            try {
                task.run();
            } catch (Throwable e) {
                handleException(e, exceptionHandler);
                try {
                    throw e;
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        trackJob(job);
        return job;
    }

    public <T> CompletableFuture<T> async(Callable<T> task) {
        return async(task, null);
    }

    public <T> CompletableFuture<T> async(Callable<T> task, Consumer<Throwable> exceptionHandler) {
        if (!isActive()) {
            CompletableFuture<T> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new CancellationException("Scope was cancelled"));
            return failedFuture;
        }

        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        ThreadPoolJob job = threadPoolManager.execute(() -> {
            if (!isActive()) {
                future.completeExceptionally(new CancellationException("Scope was cancelled"));
                return;
            }

            try {
                T result = task.call();
                future.complete(result);
            } catch (Throwable e) {
                handleException(e, exceptionHandler);
                future.completeExceptionally(e);
            }
        });

        trackJob(job);
        return future;
    }

    public ThreadPoolManagerScope createChildScope() {
        return new ThreadPoolManagerScope(threadPoolManager, this);
    }

    @Override
    public void close() {
        if (isActive.compareAndSet(true, false)) {

            synchronized (childScopes) {
                childScopes.forEach(ThreadPoolManagerScope::close);
                childScopes.clear();
            }

            cancelAllJobs();

            if (parentScope != null) {
                parentScope.removeChildScope(this);
                parentScope = null;
            }
        }
    }

    public void cancelAllJobs() {
        synchronized (activeJobs) {
            for (ThreadPoolJob job : activeJobs) {
                try {
                    job.cancel();
                } catch (Exception e) {
                    //
                }
            }
            activeJobs.clear();
        }
    }

    private void addChildScope(ThreadPoolManagerScope child) {
        if (!isActive()) {
            child.close();
        } else {
            synchronized (childScopes) {
                childScopes.add(child);
            }
        }
    }

    private void removeChildScope(ThreadPoolManagerScope child) {
        synchronized (childScopes) {
            childScopes.remove(child);
        }
    }

    private void trackJob(ThreadPoolJob job) {
        if (!isActive()) {
            job.cancel();
            return;
        }

        synchronized (activeJobs) {
            activeJobs.add(job);
        }

        // Add completion handler
        job.onComplete(result -> {
            synchronized (activeJobs) {
                activeJobs.remove(job);
            }
        });


        try {
            job.getClass().getMethod("onCancel", Runnable.class).invoke(job, (Runnable) () -> {
                synchronized (activeJobs) {
                    activeJobs.remove(job);
                }
            });
        } catch (Exception e) {

            job.onException(ex -> {
                if (ex instanceof CancellationException) {
                    synchronized (activeJobs) {
                        activeJobs.remove(job);
                    }
                }
            });
        }
    }


    private void handleException(Throwable e, Consumer<Throwable> handler) {
        if (handler != null) {
            handler.accept(e);
        } else if (globalExceptionHandler != null) {
            globalExceptionHandler.accept(e);
        }
    }

    private void startAutoCleanup() {
        threadPoolManager.execute(() -> {
            while (isActive() && !threadPoolManager.getExecutorService().isShutdown()) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                    threadPoolManager.removeCompletedTasks();

                    synchronized (activeJobs) {
                        activeJobs.removeIf(job -> job.isDone() || job.isCancelled());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}