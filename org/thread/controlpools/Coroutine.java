package org.thread.controlpools;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Consumer;

public class Coroutine<T> {
    private final CompletableFuture<T> future;
    private final CoroutineScope scope;
    private volatile boolean isCancelled = false;

    Coroutine(CompletableFuture<T> future, CoroutineScope scope) {
        this.future = future;
        this.scope = scope;
    }

    public T await() throws Exception {
        try {
            return future.get();
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    public void cancel() {
        isCancelled = true;
        future.cancel(true);
    }

    public boolean isCancelled() {
        return isCancelled || future.isCancelled();
    }

    public boolean isCompleted() {
        return future.isDone();
    }

    public Coroutine<T> onComplete(Consumer<T> action) {
        future.thenAccept(action);
        return this;
    }

    public <R> Coroutine<R> then(Function<T, R> transform) {
        CompletableFuture<R> newFuture = future.thenApply(transform);
        return new Coroutine<>(newFuture, scope);
    }

    public Coroutine<T> onError(Consumer<Throwable> action) {
        future.exceptionally(throwable -> {
            action.accept(throwable);
            return null;
        });
        return this;
    }

    public static <T> Coroutine<T> completed(T value, CoroutineScope scope) {
        CompletableFuture<T> future = CompletableFuture.completedFuture(value);
        return new Coroutine<>(future, scope);
    }

    /**
     * Улучшенная версия delay с поддержкой различных временных единиц
     */
    public static Coroutine<Void> delay(long amount, TimeUnit unit, CoroutineScope scope) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Thread virtualThread = Thread.startVirtualThread(() -> {
            try {
                unit.sleep(amount);
                future.complete(null);
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
                Thread.currentThread().interrupt();
            }
        });
        return new Coroutine<>(future, scope);
    }

    /**
     * Удобный метод для delay в миллисекундах
     */
    public static Coroutine<Void> delay(long millis, CoroutineScope scope) {
        return delay(millis, TimeUnit.MILLISECONDS, scope);
    }

    /**
     * Удобный метод для delay с использованием Duration
     */
    public static Coroutine<Void> delay(Duration duration, CoroutineScope scope) {
        return delay(duration.toMillis(), TimeUnit.MILLISECONDS, scope);
    }

    /**
     * Метод для создания периодических задержек
     */
    public static Coroutine<Void> delayTicks(long period, TimeUnit unit, int ticks, CoroutineScope scope) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Thread virtualThread = Thread.startVirtualThread(() -> {
            try {
                for (int i = 0; i < ticks && !Thread.currentThread().isInterrupted(); i++) {
                    unit.sleep(period);
                }
                future.complete(null);
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
                Thread.currentThread().interrupt();
            }
        });
        return new Coroutine<>(future, scope);
    }
} 