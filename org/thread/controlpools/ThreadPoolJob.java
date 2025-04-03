package org.thread.controlpools;

import android.os.Build;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ThreadPoolJob {
    private final Future<?> future;
    private volatile Consumer<ThreadPoolJob> completionListener;
    private volatile Consumer<Throwable> exceptionHandler;

    private final Set<ThreadPoolJob> children = Collections.newSetFromMap(new WeakHashMap<>());
    private WeakReference<ThreadPoolJob> parentRef;
    private volatile boolean isCancelling;

    public ThreadPoolJob(Future<?> future) {
        this.future = future;
    }

    public ThreadPoolJob launchChild(Runnable task) {
        return launchChild(task, null);
    }

    public ThreadPoolJob launchChild(Runnable task, Consumer<Throwable> exceptionHandler) {
        if (isCancelling || future.isDone()) {
            throw new IllegalStateException("Cannot launch child on completed/cancelled job");
        }

        ThreadPoolJob child = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            child = new ThreadPoolJob(CompletableFuture.runAsync(task, ForkJoinPool.commonPool()));
        }
        child.parentRef = new WeakReference<>(this);
        child.onException(exceptionHandler != null ? exceptionHandler : this.exceptionHandler);

        synchronized (this) {
            children.add(child);
        }

        child.onComplete(j -> {
            synchronized (ThreadPoolJob.this) {
                children.remove(j);
            }
        });

        return child;
    }

    public void awaitAllChildren() throws InterruptedException, ExecutionException {
        List<ThreadPoolJob> childrenCopy;
        synchronized (this) {
            childrenCopy = new ArrayList<>(children);
        }

        for (ThreadPoolJob child : childrenCopy) {
            try {
                child.await();
            } catch (CancellationException ignored) {
                // None
            }
        }
    }

    public boolean cancel() {
        if (isCancelling) return false;
        isCancelling = true;

        List<ThreadPoolJob> childrenCopy;
        synchronized (this) {
            childrenCopy = new ArrayList<>(children);
        }

        for (ThreadPoolJob child : childrenCopy) {
            child.cancel();
        }

        ThreadPoolJob parent = parentRef != null ? parentRef.get() : null;
        if (parent != null) {
            synchronized (parent) {
                parent.children.remove(this);
            }
        }

        boolean cancelled = future.cancel(true);
        if (cancelled && completionListener != null) {
            completionListener.accept(this);
        }

        return cancelled;
    }

    public boolean isDone() {
        if (!future.isDone()) return false;

        synchronized (this) {
            return children.isEmpty();
        }
    }

    public void await() throws InterruptedException, ExecutionException {
        try {
            future.get();
            awaitAllChildren();
        } catch (CancellationException e) {
            throw new ExecutionException("Task was cancelled", e);
        }
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public void await(long timeoutMillis)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            awaitAllChildren();
        } catch (CancellationException e) {
            throw new ExecutionException("Task was cancelled", e);
        }
    }

    public ThreadPoolJob onComplete(Consumer<ThreadPoolJob> listener) {
        this.completionListener = listener;
        if (isDone()) {
            listener.accept(this);
        }
        return this;
    }

    public ThreadPoolJob onException(Consumer<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    public <T> CompletableFuture<T> asCompletableFuture() {
        CompletableFuture<T> result = new CompletableFuture<>();

        onComplete(job -> {
            if (job.isCancelled()) {
                result.cancel(false);
            } else {
                try {
                    result.complete(null);
                } catch (Exception e) {
                    if (exceptionHandler != null) {
                        exceptionHandler.accept(e);
                    }
                    result.completeExceptionally(e);
                }
            }
        });

        return result;
    }

    public boolean isRunning() {
        return !future.isDone() && !future.isCancelled();
    }

    public void waitForCompletion() throws InterruptedException, ExecutionException {
        try {
            future.get();
        } catch (CancellationException e) {
            throw new ExecutionException("Task was cancelled", e);
        }
    }

    public void waitForCompletion(long timeoutMillis) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            throw new ExecutionException("Task was cancelled", e);
        }
    }

    private void awaitChildrenCompletion() throws InterruptedException, ExecutionException {
        synchronized (this) {
            List<ThreadPoolJob> childrenCopy = new ArrayList<>(children);
            for (ThreadPoolJob child : childrenCopy) {
                child.await();
            }
        }
    }

    private void onCompleteInternal(Consumer<ThreadPoolJob> listener) {
        this.completionListener = listener;
        if (isDone()) {
            listener.accept(this);
        }
    }

    private void onExceptionInternal(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
}
