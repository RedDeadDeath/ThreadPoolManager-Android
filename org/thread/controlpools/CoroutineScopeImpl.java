package org.thread.controlpools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoroutineScopeImpl implements CoroutineScope, AutoCloseable {
    private final CoroutineContext context;
    private final List<Coroutine<?>> activeCoroutines = new ArrayList<>();
    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final List<AutoCloseable> resources = new ArrayList<>();

    public CoroutineScopeImpl(CoroutineContext context) {
        this.context = context;
    }

    @Override
    public <T> Coroutine<T> launch(Callable<T> task) {
        checkActive();
        CompletableFuture<T> future = new CompletableFuture<>();
        Coroutine<T> coroutine = new Coroutine<>(future, this);

        Thread virtualThread = Thread.startVirtualThread(() -> {
            try {
                if (!isActive.get()) {
                    future.completeExceptionally(new CancellationException("Scope was cancelled"));
                    return;
                }

                T result = task.call();
                future.complete(result);
            } catch (Throwable e) {
                handleException(e);
                future.completeExceptionally(e);
            }
        });

        trackCoroutine(coroutine);
        return coroutine;
    }

    @Override
    public Coroutine<Void> launch(Runnable task) {
        return launch(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> Coroutine<T> async(Callable<T> task) {
        return launch(task);
    }

    @Override
    public void cancel() {
        if (isActive.compareAndSet(true, false)) {
            synchronized (activeCoroutines) {
                for (Coroutine<?> coroutine : activeCoroutines) {
                    coroutine.cancel();
                }
                activeCoroutines.clear();
            }
            closeResources();
        }
    }

    @Override
    public void close() {
        cancel();
    }

    @Override
    public boolean isActive() {
        return isActive.get();
    }

    @Override
    public CoroutineScope plus(CoroutineContext additionalContext) {
        return new CoroutineScopeImpl(context.plus(additionalContext));
    }

    @Override
    public CoroutineContext getContext() {
        return context;
    }

    public void addResource(AutoCloseable resource) {
        synchronized (resources) {
            resources.add(resource);
        }
    }

    private void closeResources() {
        synchronized (resources) {
            for (AutoCloseable resource : resources) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                }
            }
            resources.clear();
        }
    }

    private void trackCoroutine(Coroutine<?> coroutine) {
        if (!isActive.get()) {
            coroutine.cancel();
            return;
        }

        synchronized (activeCoroutines) {
            activeCoroutines.add(coroutine);
        }

        Thread.startVirtualThread(() -> {
            try {
                coroutine.await();
            } catch (Exception ignored) {
            } finally {
                synchronized (activeCoroutines) {
                    activeCoroutines.remove(coroutine);
                }
            }
        });
    }

    private void handleException(Throwable e) {
        var handler = context.getExceptionHandler();
        if (handler != null) {
            handler.accept(e);
        }
    }

    private void checkActive() {
        if (!isActive.get()) {
            throw new IllegalStateException("Scope is not active");
        }
    }
} 