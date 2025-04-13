package org.thread.controlpools;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class VirtualThreadExample {
    public static void main(String[] args) throws Exception {
        // Используем try-with-resources для автоматического закрытия scope
        try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
            
            // Пример использования различных версий delay
            System.out.println("Starting delays...");
            
            // Обычная задержка в миллисекундах
            scope.launch(() -> {
                System.out.println("Starting milliseconds delay");
                Coroutine.delay(1000, scope).await();
                System.out.println("Milliseconds delay completed");
            });

            // Задержка с использованием TimeUnit
            scope.launch(() -> {
                System.out.println("Starting seconds delay");
                Coroutine.delay(2, TimeUnit.SECONDS, scope).await();
                System.out.println("Seconds delay completed");
            });

            // Задержка с использованием Duration
            scope.launch(() -> {
                System.out.println("Starting Duration delay");
                Coroutine.delay(Duration.ofSeconds(3), scope).await();
                System.out.println("Duration delay completed");
            });

            // Периодические задержки
            scope.launch(() -> {
                System.out.println("Starting periodic delays");
                Coroutine.delayTicks(500, TimeUnit.MILLISECONDS, 5, scope).await();
                System.out.println("Periodic delays completed");
            });

            // Пример массового создания виртуальных потоков
            for (int i = 0; i < 1000; i++) {
                final int taskId = i;
                scope.launch(() -> {
                    Coroutine.delay(100, scope).await();
                    System.out.println("Task " + taskId + " completed on thread: " + Thread.currentThread());
                });
            }

            // Пример использования withContext для переключения контекста
            scope.launch(() -> {
                System.out.println("Starting in Virtual context");
                
                // Переключаемся на IO контекст
                CoroutineUtils.withContext(Dispatchers.IO, () -> {
                    System.out.println("Executing in IO context");
                    return null;
                }, scope).await();
                
                // Возвращаемся в Virtual контекст
                System.out.println("Back in Virtual context");
            });

            // Пример обработки ошибок
            scope.launch(() -> {
                try {
                    throw new RuntimeException("Test exception");
                } catch (Exception e) {
                    System.err.println("Caught exception: " + e.getMessage());
                }
            });

            // Даем время на выполнение всех задач
            Thread.sleep(5000);
        } // scope автоматически закроется здесь

        System.out.println("All tasks completed");
    }
} 