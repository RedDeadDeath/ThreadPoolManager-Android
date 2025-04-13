package org.thread.controlpools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CoroutineUtils {
    
    /**
     * Аналог withContext из Kotlin корутин
     */
    public static <T> Coroutine<T> withContext(CoroutineContext context, Callable<T> block, CoroutineScope scope) {
        return scope.plus(context).async(block);
    }

    /**
     * Аналог withTimeout из Kotlin корутин
     */
    public static <T> Coroutine<T> withTimeout(long timeoutMillis, Callable<T> block, CoroutineScope scope) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Coroutine<T> coroutine = new Coroutine<>(future, scope);

        // Запускаем основную задачу
        scope.launch(() -> {
            try {
                T result = block.call();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        // Запускаем таймер
        scope.launch(() -> {
            try {
                Thread.sleep(timeoutMillis);
                if (!future.isDone()) {
                    future.completeExceptionally(new TimeoutException("Operation timed out after " + timeoutMillis + "ms"));
                }
            } catch (InterruptedException ignored) {
            }
        });

        return coroutine;
    }

    /**
     * Аналог retry из Kotlin корутин
     */
    public static <T> Coroutine<T> retry(int attempts, long delayMillis, Callable<T> block, CoroutineScope scope) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Coroutine<T> coroutine = new Coroutine<>(future, scope);

        scope.launch(() -> {
            Exception lastException = null;
            for (int i = 0; i < attempts; i++) {
                try {
                    T result = block.call();
                    future.complete(result);
                    return;
                } catch (Exception e) {
                    lastException = e;
                    if (i < attempts - 1) {
                        Coroutine.delay(delayMillis, scope).await();
                    }
                }
            }
            future.completeExceptionally(lastException);
        });

        return coroutine;
    }

    /**
     * Аналог debounce из Flow
     */
    public static <T> Function<T, Coroutine<T>> debounce(long timeoutMillis, CoroutineScope scope) {
        ScheduledFuture<?>[] scheduled = new ScheduledFuture[1];
        CompletableFuture<T>[] pendingFuture = new CompletableFuture[1];
        
        return value -> {
            CompletableFuture<T> future = new CompletableFuture<>();
            pendingFuture[0] = future;
            
            if (scheduled[0] != null) {
                scheduled[0].cancel(false);
            }
            
            scheduled[0] = ThreadPoolManager.getScheduler().schedule(() -> {
                if (future == pendingFuture[0]) {
                    future.complete(value);
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
            
            return new Coroutine<>(future, scope);
        };
    }

    /**
     * Аналог parallel из Kotlin корутин
     */
    public static <T> Coroutine<List<T>> parallel(List<Callable<T>> tasks, CoroutineScope scope) {
        List<Coroutine<T>> coroutines = new ArrayList<>();
        for (Callable<T> task : tasks) {
            coroutines.add(scope.async(task));
        }

        return scope.async(() -> {
            List<T> results = new ArrayList<>();
            for (Coroutine<T> coroutine : coroutines) {
                results.add(coroutine.await());
            }
            return results;
        });
    }

    /**
     * Аналог race из Promise.race
     */
    public static <T> Coroutine<T> race(List<Callable<T>> tasks, CoroutineScope scope) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Coroutine<T> coroutine = new Coroutine<>(future, scope);

        for (Callable<T> task : tasks) {
            scope.launch(() -> {
                try {
                    T result = task.call();
                    if (!future.isDone()) {
                        future.complete(result);
                    }
                } catch (Exception e) {
                    if (!future.isDone()) {
                        future.completeExceptionally(e);
                    }
                }
            });
        }

        return coroutine;
    }

    /**
     * Аналог throttle из Flow
     */
    public static <T> Function<T, Coroutine<T>> throttle(long timeoutMillis, CoroutineScope scope) {
        long[] lastEmissionTime = {0};
        
        return value -> {
            CompletableFuture<T> future = new CompletableFuture<>();
            long currentTime = System.currentTimeMillis();
            long timeSinceLastEmission = currentTime - lastEmissionTime[0];
            
            if (timeSinceLastEmission >= timeoutMillis) {
                lastEmissionTime[0] = currentTime;
                future.complete(value);
            } else {
                long delay = timeoutMillis - timeSinceLastEmission;
                Coroutine.delay(delay, scope).onComplete(ignored -> {
                    lastEmissionTime[0] = System.currentTimeMillis();
                    future.complete(value);
                });
            }
            
            return new Coroutine<>(future, scope);
        };
    }

    /**
     * Аналог interval из RxJava
     */
    public static Coroutine<Void> interval(long periodMillis, Runnable action, CoroutineScope scope) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean running = new AtomicBoolean(true);

        scope.launch(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    action.run();
                    Thread.sleep(periodMillis);
                } catch (InterruptedException e) {
                    running.set(false);
                    Thread.currentThread().interrupt();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    running.set(false);
                }
            }
        });

        return new Coroutine<>(future, scope);
    }
} 