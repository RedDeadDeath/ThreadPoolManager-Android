package org.thread.controlpools;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Расширенные утилиты для работы с корутинами
 */
public class CoroutineExtensions {

    /**
     * Выполнение операции с повторными попытками и экспоненциальной задержкой
     */
    public static <T> Coroutine<T> retryWithBackoff(
            int maxAttempts,
            long initialDelayMs,
            double backoffMultiplier,
            Supplier<T> operation,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            long delay = initialDelayMs;
            Exception lastError = null;
            
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return operation.get();
                } catch (Exception e) {
                    lastError = e;
                    if (attempt < maxAttempts) {
                        Coroutine.delay(delay, scope).await();
                        delay = (long) (delay * backoffMultiplier);
                    }
                }
            }
            throw new RuntimeException("Failed after " + maxAttempts + " attempts", lastError);
        });
    }

    /**
     * Выполнение операции с таймаутом и fallback значением
     */
    public static <T> Coroutine<T> withTimeoutOrElse(
            long timeout,
            Supplier<T> operation,
            T fallbackValue,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            try {
                return CoroutineUtils.withTimeout(timeout, operation, scope).await();
            } catch (TimeoutException e) {
                return fallbackValue;
            }
        });
    }

    /**
     * Периодическое выполнение с динамическим интервалом
     */
    public static Coroutine<Void> dynamicInterval(
            Function<Integer, Long> intervalProvider,
            Consumer<Integer> action,
            int maxIterations,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            for (int i = 0; i < maxIterations && !Thread.currentThread().isInterrupted(); i++) {
                action.accept(i);
                long interval = intervalProvider.apply(i);
                Coroutine.delay(interval, scope).await();
            }
            return null;
        });
    }

    /**
     * Выполнение операции только при выполнении условия
     */
    public static <T> Coroutine<T> executeIf(
            Predicate<CoroutineScope> condition,
            Supplier<T> operation,
            T defaultValue,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            if (condition.test(scope)) {
                return operation.get();
            }
            return defaultValue;
        });
    }

    /**
     * Создание цепочки асинхронных операций с общим контекстом
     */
    public static <T> CoroutineChain<T> chainOperations(CoroutineScope scope) {
        return new CoroutineChain<>(scope);
    }

    /**
     * Параллельное выполнение с ограничением одновременных операций
     */
    public static <T> Coroutine<List<T>> parallelLimited(
            Collection<Supplier<T>> tasks,
            int maxConcurrent,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            CoroutineChannel<Supplier<T>> channel = CoroutineChannel.buffered(tasks.size());
            CoroutineChannel<T> resultChannel = CoroutineChannel.buffered(tasks.size());

            // Отправка задач в канал
            scope.launch(() -> {
                for (Supplier<T> task : tasks) {
                    channel.send(task, scope).await();
                }
                channel.close();
            });

            // Запуск обработчиков
            for (int i = 0; i < Math.min(maxConcurrent, tasks.size()); i++) {
                scope.launch(() -> {
                    while (!channel.isClosed() || channel.size() > 0) {
                        Supplier<T> task = channel.receive(scope).await();
                        T result = task.get();
                        resultChannel.send(result, scope).await();
                    }
                });
            }

            // Сбор результатов
            List<T> results = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                results.add(resultChannel.receive(scope).await());
            }
            return results;
        });
    }

    /**
     * Выполнение операции с автоматическим освобождением ресурсов
     */
    public static <R extends AutoCloseable, T> Coroutine<T> withResource(
            Supplier<R> resourceSupplier,
            Function<R, T> operation,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            try (R resource = resourceSupplier.get()) {
                return operation.apply(resource);
            }
        });
    }

    /**
     * Выполнение операции с отслеживанием прогресса
     */
    public static <T> Coroutine<T> withProgress(
            Supplier<T> operation,
            Consumer<Double> progressCallback,
            CoroutineScope scope
    ) {
        return scope.async(() -> {
            ProgressTracker tracker = new ProgressTracker(progressCallback);
            try {
                tracker.start();
                return operation.get();
            } finally {
                tracker.complete();
            }
        });
    }

    /**
     * Выполнение операции с кэшированием результата
     */
    public static <K, V> CachedCoroutine<K, V> cached(
            Function<K, V> operation,
            Duration cacheDuration,
            CoroutineScope scope
    ) {
        return new CachedCoroutine<>(operation, cacheDuration, scope);
    }

    /**
     * Выполнение операции с циклическим повтором при ошибке
     */
    public static <T> Coroutine<T> circuitBreaker(
            Supplier<T> operation,
            int maxFailures,
            Duration resetTimeout,
            CoroutineScope scope
    ) {
        return new CircuitBreaker<>(operation, maxFailures, resetTimeout, scope).execute();
    }

    /**
     * Выполнение операции с ограничением скорости
     */
    public static <T> RateLimiter<T> rateLimited(
            int maxRequests,
            Duration window,
            CoroutineScope scope
    ) {
        return new RateLimiter<>(maxRequests, window, scope);
    }

    /**
     * Вспомогательный класс для отслеживания прогресса
     */
    private static class ProgressTracker {
        private final Consumer<Double> progressCallback;
        private final long startTime;
        private volatile boolean completed;

        public ProgressTracker(Consumer<Double> progressCallback) {
            this.progressCallback = progressCallback;
            this.startTime = System.currentTimeMillis();
            this.completed = false;
        }

        public void start() {
            Thread progressThread = new Thread(() -> {
                while (!completed) {
                    try {
                        Thread.sleep(100);
                        long elapsed = System.currentTimeMillis() - startTime;
                        progressCallback.accept(Math.min(1.0, elapsed / 1000.0));
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            progressThread.setDaemon(true);
            progressThread.start();
        }

        public void complete() {
            completed = true;
            progressCallback.accept(1.0);
        }
    }

    /**
     * Класс для кэширования результатов корутин
     */
    public static class CachedCoroutine<K, V> {
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
        private final Function<K, V> operation;
        private final Duration cacheDuration;
        private final CoroutineScope scope;

        private CachedCoroutine(Function<K, V> operation, Duration cacheDuration, CoroutineScope scope) {
            this.operation = operation;
            this.cacheDuration = cacheDuration;
            this.scope = scope;
        }

        public Coroutine<V> get(K key) {
            return scope.async(() -> {
                CacheEntry<V> entry = cache.get(key);
                if (entry != null && !entry.isExpired()) {
                    return entry.value;
                }
                V value = operation.apply(key);
                cache.put(key, new CacheEntry<>(value, cacheDuration));
                return value;
            });
        }

        private static class CacheEntry<V> {
            final V value;
            final long expirationTime;

            CacheEntry(V value, Duration ttl) {
                this.value = value;
                this.expirationTime = System.currentTimeMillis() + ttl.toMillis();
            }

            boolean isExpired() {
                return System.currentTimeMillis() > expirationTime;
            }
        }
    }

    /**
     * Класс для ограничения скорости выполнения операций
     */
    public static class RateLimiter<T> {
        private final Queue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();
        private final int maxRequests;
        private final Duration window;
        private final CoroutineScope scope;

        public RateLimiter(int maxRequests, Duration window, CoroutineScope scope) {
            this.maxRequests = maxRequests;
            this.window = window;
            this.scope = scope;
        }

        public Coroutine<T> execute(Supplier<T> operation) {
            return scope.async(() -> {
                long now = System.currentTimeMillis();
                long windowStart = now - window.toMillis();

                // Очистка старых записей
                while (!requestTimestamps.isEmpty() && requestTimestamps.peek() < windowStart) {
                    requestTimestamps.poll();
                }

                // Проверка лимита
                if (requestTimestamps.size() >= maxRequests) {
                    long oldestTimestamp = requestTimestamps.peek();
                    long waitTime = oldestTimestamp + window.toMillis() - now;
                    if (waitTime > 0) {
                        Coroutine.delay(waitTime, scope).await();
                    }
                }

                requestTimestamps.offer(System.currentTimeMillis());
                return operation.get();
            });
        }
    }

    /**
     * Класс для реализации паттерна Circuit Breaker
     */
    private static class CircuitBreaker<T> {
        private final Supplier<T> operation;
        private final int maxFailures;
        private final Duration resetTimeout;
        private final CoroutineScope scope;
        private int failures;
        private long lastFailureTime;
        private State state;

        private enum State {
            CLOSED, OPEN, HALF_OPEN
        }

        public CircuitBreaker(Supplier<T> operation, int maxFailures, Duration resetTimeout, CoroutineScope scope) {
            this.operation = operation;
            this.maxFailures = maxFailures;
            this.resetTimeout = resetTimeout;
            this.scope = scope;
            this.failures = 0;
            this.state = State.CLOSED;
        }

        public Coroutine<T> execute() {
            return scope.async(() -> {
                while (true) {
                    switch (state) {
                        case CLOSED:
                            try {
                                T result = operation.get();
                                failures = 0;
                                return result;
                            } catch (Exception e) {
                                failures++;
                                if (failures >= maxFailures) {
                                    state = State.OPEN;
                                    lastFailureTime = System.currentTimeMillis();
                                }
                                throw e;
                            }
                        case OPEN:
                            if (System.currentTimeMillis() - lastFailureTime > resetTimeout.toMillis()) {
                                state = State.HALF_OPEN;
                                continue;
                            }
                            throw new CircuitBreakerOpenException();
                        case HALF_OPEN:
                            try {
                                T result = operation.get();
                                state = State.CLOSED;
                                failures = 0;
                                return result;
                            } catch (Exception e) {
                                state = State.OPEN;
                                lastFailureTime = System.currentTimeMillis();
                                throw e;
                            }
                    }
                }
            });
        }
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException() {
            super("Circuit breaker is open");
        }
    }
} 