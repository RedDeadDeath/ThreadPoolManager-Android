package org.thread.controlpools;

import java.util.concurrent.TimeUnit;

public class CoroutineExample {
    public static void main(String[] args) throws Exception {
        // Create a scope with default dispatcher
        CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Default);

        // Launch a coroutine
        Coroutine<String> coroutine = scope.launch(() -> {
            System.out.println("Starting long operation on thread: " + Thread.currentThread().getName());
            // Simulate long operation
            TimeUnit.SECONDS.sleep(2);
            return "Operation completed!";
        });

        // Add completion handler
        coroutine.onComplete(result -> {
            System.out.println("Result: " + result);
        });

        // Launch another coroutine with IO dispatcher
        scope.plus(Dispatchers.IO).launch(() -> {
            System.out.println("Performing IO operation on thread: " + Thread.currentThread().getName());
            // Simulate IO operation
            TimeUnit.SECONDS.sleep(1);
            System.out.println("IO operation completed!");
        });

        // Example of using delay
        Coroutine<Void> delayedTask = scope.launch(() -> {
            System.out.println("Before delay");
            Coroutine.delay(1000, scope).await();
            System.out.println("After delay");
            return null;
        });

        // Example of error handling
        scope.launch(() -> {
            throw new RuntimeException("Simulated error");
        }).onError(error -> {
            System.err.println("Error caught: " + error.getMessage());
        });

        // Wait for completion
        coroutine.await();
        delayedTask.await();

        // Clean up
        scope.cancel();
    }

    // Example of async operation that returns a value
    private static Coroutine<Integer> performCalculation(CoroutineScope scope) {
        return scope.async(() -> {
            TimeUnit.MILLISECONDS.sleep(500);
            return 42;
        });
    }

    // Example of combining multiple coroutines
    private static void combinedOperations(CoroutineScope scope) throws Exception {
        Coroutine<Integer> calc1 = performCalculation(scope);
        Coroutine<Integer> calc2 = performCalculation(scope);

        // Wait for both calculations and combine results
        Integer result1 = calc1.await();
        Integer result2 = calc2.await();
        System.out.println("Combined result: " + (result1 + result2));
    }
} 