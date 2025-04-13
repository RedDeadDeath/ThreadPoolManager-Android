package org.thread.controlpools;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CoroutineUtilsExample {
    public static void main(String[] args) throws Exception {
        CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Default);

        // Пример использования withContext
        Coroutine<String> ioOperation = CoroutineUtils.withContext(
            Dispatchers.IO,
            () -> {
                System.out.println("Выполняется на IO потоке: " + Thread.currentThread().getName());
                TimeUnit.SECONDS.sleep(1);
                return "IO операция завершена";
            },
            scope
        );
        System.out.println(ioOperation.await());

        // Пример использования withTimeout
        try {
            CoroutineUtils.withTimeout(500, () -> {
                TimeUnit.SECONDS.sleep(1);
                return "Эта операция не успеет выполниться";
            }, scope).await();
        } catch (TimeoutException e) {
            System.out.println("Операция прервана по таймауту: " + e.getMessage());
        }

        // Пример использования retry
        Coroutine<String> retryOperation = CoroutineUtils.retry(3, 1000, () -> {
            double random = Math.random();
            if (random > 0.8) {
                return "Успешно с " + random;
            }
            throw new RuntimeException("Ошибка с " + random);
        }, scope);
        
        try {
            System.out.println("Результат retry: " + retryOperation.await());
        } catch (Exception e) {
            System.out.println("Все попытки retry завершились неудачей: " + e.getMessage());
        }

        // Пример использования parallel
        List<Callable<Integer>> tasks = Arrays.asList(
            () -> {
                TimeUnit.MILLISECONDS.sleep(100);
                return 1;
            },
            () -> {
                TimeUnit.MILLISECONDS.sleep(200);
                return 2;
            },
            () -> {
                TimeUnit.MILLISECONDS.sleep(300);
                return 3;
            }
        );

        List<Integer> results = CoroutineUtils.parallel(tasks, scope).await();
        System.out.println("Параллельные результаты: " + results);

        // Пример использования race
        Coroutine<String> raceResult = CoroutineUtils.race(Arrays.asList(
            () -> {
                TimeUnit.MILLISECONDS.sleep(200);
                return "Первая задача";
            },
            () -> {
                TimeUnit.MILLISECONDS.sleep(100);
                return "Вторая задача";
            }
        ), scope);
        System.out.println("Победитель гонки: " + raceResult.await());

        // Пример использования debounce
        Function<String, Coroutine<String>> debouncedFunction = CoroutineUtils.debounce(500, scope);
        
        // Эмулируем быстрые последовательные вызовы
        debouncedFunction.apply("1");
        Thread.sleep(100);
        debouncedFunction.apply("2");
        Thread.sleep(100);
        Coroutine<String> lastCall = debouncedFunction.apply("3");
        
        System.out.println("Debounced результат: " + lastCall.await());

        // Пример использования interval
        Coroutine<Void> intervalJob = CoroutineUtils.interval(1000, () -> {
            System.out.println("Tick: " + System.currentTimeMillis());
        }, scope);

        // Даем интервалу поработать 3 секунды
        Thread.sleep(3000);
        intervalJob.cancel();

        // Пример использования throttle
        Function<String, Coroutine<String>> throttledFunction = CoroutineUtils.throttle(1000, scope);
        
        // Эмулируем быстрые последовательные вызовы
        throttledFunction.apply("A").await();
        throttledFunction.apply("B"); // Этот будет пропущен
        Thread.sleep(100);
        throttledFunction.apply("C"); // И этот тоже
        Coroutine<String> lastThrottled = throttledFunction.apply("D");
        
        System.out.println("Throttled результат: " + lastThrottled.await());

        scope.cancel();
    }
} 