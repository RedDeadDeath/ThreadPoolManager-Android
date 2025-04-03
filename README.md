/*
 * ================================
 * MANUAL FOR THREADPOOLMANAGER WITH SCOPE
 * ================================
 *
 * 1. **Include `ThreadPoolManager` and `ThreadPoolManagerScope` in your project**
 *
 * Place the classes in suitable packages like `org.thread.controlpools` or another package you prefer.
 *
 * 2. **Creating an instance of `ThreadPoolManagerScope`**
 *
 * A `ThreadPoolManagerScope` defines a scoped instance of `ThreadPoolManager`, managing its lifecycle
 * and ensuring that the thread pool only exists during the specified scope.
 *
 * 2.1 **Using Default Parameters for Scope:**
 *
 * ```java
 * ThreadPoolManagerScope scope = new ThreadPoolManagerScope();
 * ThreadPoolManager threadPoolManager = scope.getThreadPoolManager();
 * ```
 *
 * 2.2 **Using Custom Parameters for Scope:**
 *
 * ```java
 * int corePoolSize = 2;  // Number of initial threads
 * int maxPoolSize = 5;   // Maximum number of threads
 * long keepAliveTime = 60L;  // Time for idle threads to live (in seconds)
 * int queueCapacity = 20;  // Capacity of the task queue
 *
 * ThreadPoolManagerScope scope = new ThreadPoolManagerScope(corePoolSize, maxPoolSize, keepAliveTime, queueCapacity);
 * ThreadPoolManager threadPoolManager = scope.getThreadPoolManager();
 * ```
 *
 * 3. **Using `ThreadPoolManagerScope` to Submit Tasks**
 *
 * Once the `ThreadPoolManager` instance is obtained from `ThreadPoolManagerScope`, you can submit tasks as usual.
 * The difference is that the thread pool will automatically be shut down once the scope ends (when the object goes out of scope or is explicitly closed).
 *
 * 3.1 **Submitting a Runnable task:**
 *
 * ```java
 * Runnable task = new Runnable() {
 *     @Override
 *     public void run() {
 *         // Your task code
 *         Log.i("ThreadPoolManager", "Task is executing");
 *     }
 * };
 *
 * Job job = threadPoolManager.submitTask(task);
 * ```
 *
 * 3.2 **Submitting a Callable task (with result):**
 *
 * ```java
 * Callable<String> task = new Callable<String>() {
 *     @Override
 *     public String call() throws Exception {
 *         // Your task code
 *         return "Task Result";
 *     }
 * };
 *
 * Job job = threadPoolManager.submitTask(task);
 *
 * try {
 *     String result = job.await();  // Blocking call to get result
 *     Log.i("ThreadPoolManager", "Result: " + result);
 * } catch (Exception e) {
 *     Log.e("ThreadPoolManager", "Error executing task: " + e.getMessage());
 * }
 * ```
 *
 * 4. **Automatic Cleanup when Scope Ends**
 *
 * One of the key features of using `ThreadPoolManagerScope` is that when the scope ends (e.g., when the scope object is no longer referenced or explicitly closed), the `ThreadPoolManager` is automatically shut down, ensuring that all resources are cleaned up.
 *
 * 4.1 **Ending the scope explicitly:**
 *
 * ```java
 * scope.close();  // Explicitly end the scope and shut down the thread pool
 * Log.i("ThreadPoolManager", "Thread pool shut down automatically at the end of the scope");
 * ```
 *
 * 5. **Managing Thread Pool Lifecycle with Scope**
 *
 * You can manage the lifecycle of the thread pool without having to manually shut it down when using `ThreadPoolManagerScope`. This makes it easier to handle lifecycle-related concerns in your application.
 *
 * 5.1 **Creating and using the scope:**
 *
 * ```java
 * try (ThreadPoolManagerScope scope = new ThreadPoolManagerScope()) {
 *     ThreadPoolManager threadPoolManager = scope.getThreadPoolManager();
 *     // Submit tasks and perform other operations
 * }
 * // When the try-with-resources block ends, the thread pool is automatically shut down
 * Log.i("ThreadPoolManager", "Thread pool shut down after the scope ended");
 * ```
 *
 * 6. **Using Periodic Tasks within Scope**
 *
 * You can also use periodic tasks within the scope, ensuring that periodic tasks are automatically cleaned up once the scope ends.
 *
 * 6.1 **Submitting a periodic task:**
 *
 * ```java
 * Runnable periodicTask = new Runnable() {
 *     @Override
 *     public void run() {
 *         // Code to be executed periodically
 *         Log.i("ThreadPoolManager", "Periodic task executed");
 *     }
 * };
 *
 * // Schedule the task within the scope with an initial delay of 0 seconds and a period of 10 seconds
 * ScheduledFuture<?> scheduledFuture = threadPoolManager.submitPeriodicTask(periodicTask, 0L, 10L, TimeUnit.SECONDS);
 * ```
 *
 * 7. **Handling Scope-based State Listeners**
 *
 * You can attach state listeners to the scope to monitor the thread pool's state. This ensures that the state is tracked for the thread pool within the scope's lifecycle.
 *
 * 7.1 **Adding a listener to the scope:**
 *
 * ```java
 * scope.addStateListener(new ThreadPoolManagerScope.ScopeStateListener() {
 *     @Override
 *     public void onScopeStarted() {
 *         Log.i("ThreadPoolManager", "Thread pool scope started");
 *     }
 *
 *     @Override
 *     public void onScopeEnded() {
 *         Log.i("ThreadPoolManager", "Thread pool scope ended, cleaning up");
 *     }
 * });
 * ```
 *
 * 8. **Waiting for All Tasks to Complete within Scope**
 *
 * If you need to wait for all tasks within the scope to complete before the scope ends, you can use the `awaitTermination` method.
 *
 * 8.1 **Waiting for all tasks to finish within the scope:**
 *
 * ```java
 * try {
 *     boolean terminated = scope.awaitTermination(60, TimeUnit.SECONDS);
 *     if (terminated) {
 *         Log.i("ThreadPoolManager", "All tasks completed");
 *     } else {
 *         Log.i("ThreadPoolManager", "Not all tasks completed in the waiting time");
 *     }
 * } catch (InterruptedException e) {
 *     Log.e("ThreadPoolManager", "Error waiting for termination: " + e.getMessage());
 * }
 * ```
 *
 * ================================
 * END OF MANUAL
 * ================================
 */





/*
 * ================================
 * MANUAL FOR THREADPOOLMANAGER
 * ================================
 *
 * 1. **Include `ThreadPoolManager` in your project**
 *
 * Place the class in a suitable package like `org.thread.controlpools` or another package you prefer.
 *
 * 2. **Creating an instance of `ThreadPoolManager`**
 *
 * You can create an instance of `ThreadPoolManager` with default or customized parameters.
 * Choose based on your requirements and device specifications.
 *
 * 2.1 **Using Default Parameters:**
 *
 * ```java
 * ThreadPoolManager threadPoolManager = new ThreadPoolManager();
 * ```
 *
 * 2.2 **Using Custom Parameters:**
 *
 * ```java
 * int corePoolSize = 2;  // Number of initial threads
 * int maxPoolSize = 5;   // Maximum number of threads
 * long keepAliveTime = 60L;  // Time for idle threads to live (in seconds)
 * int queueCapacity = 20;  // Capacity of the task queue
 *
 * ThreadPoolManager threadPoolManager = new ThreadPoolManager(corePoolSize, maxPoolSize, keepAliveTime, queueCapacity);
 * ```
 *
 * 3. **Submitting tasks to the thread pool**
 *
 * Once the `ThreadPoolManager` instance is created, you can submit tasks to the pool using `submitTask`.
 *
 * 3.1 **Submitting a Runnable task:**
 *
 * ```java
 * Runnable task = new Runnable() {
 *     @Override
 *     public void run() {
 *         // Your task code that should run in the background thread
 *         Log.i("ThreadPoolManager", "Task is executing");
 *     }
 * };
 *
 * // Submit the task to the thread pool
 * threadPoolManager.submitTask(task);
 * ```
 *
 * 3.2 **Submitting a Callable task (with result):**
 *
 * ```java
 * Callable<String> task = new Callable<String>() {
 *     @Override
 *     public String call() throws Exception {
 *         // Your task code
 *         return "Task Result";
 *     }
 * };
 *
 * // Submit the task and get the result
 * Future<String> future = threadPoolManager.submitTask(task);
 *
 * // Retrieve the result (this is a blocking call)
 * try {
 *     String result = future.get();
 *     Log.i("ThreadPoolManager", "Result: " + result);
 * } catch (Exception e) {
 *     Log.e("ThreadPoolManager", "Error executing task: " + e.getMessage());
 * }
 * ```
 *
 * 4. **Pausing and Resuming the Thread Pool**
 *
 * If you need to pause the execution of all tasks in the pool (e.g., temporarily), you can use the `pause` and `resume` methods.
 *
 * 4.1 **Pausing the pool:**
 *
 * ```java
 * threadPoolManager.pause();
 * Log.i("ThreadPoolManager", "Thread pool paused");
 * ```
 *
 * 4.2 **Resuming the pool:**
 *
 * ```java
 * threadPoolManager.resume();
 * Log.i("ThreadPoolManager", "Thread pool resumed");
 * ```
 *
 * 5. **Shutting down the Thread Pool**
 *
 * If you want to shut down the thread pool, which will terminate all running tasks and stop accepting new ones, use the `shutdown` method.
 *
 * 5.1 **Shutting down the pool:**
 *
 * ```java
 * threadPoolManager.shutdown();
 * Log.i("ThreadPoolManager", "Thread pool shut down");
 * ```
 *
 * 6. **Using Periodic Tasks**
 *
 * If you need to run a task at regular intervals, you can use the `submitPeriodicTask` method.
 *
 * 6.1 **Example of a periodic task:**
 *
 * ```java
 * Runnable periodicTask = new Runnable() {
 *     @Override
 *     public void run() {
 *         // Code to be executed periodically
 *         Log.i("ThreadPoolManager", "Periodic task executed");
 *     }
 * };
 *
 * // Schedule the task with an initial delay of 0 seconds and a period of 10 seconds
 * ScheduledFuture<?> scheduledFuture = threadPoolManager.submitPeriodicTask(periodicTask, 0L, 10L, TimeUnit.SECONDS);
 * ```
 *
 * 7. **Getting Information about the Thread Pool**
 *
 * You can monitor various parameters of the thread pool, such as the number of active tasks or the size of the queue.
 *
 * 7.1 **Getting the number of active tasks:**
 *
 * ```java
 * int activeTaskCount = threadPoolManager.getActiveTaskCount();
 * Log.i("ThreadPoolManager", "Active tasks: " + activeTaskCount);
 * ```
 *
 * 7.2 **Getting the size of the task queue:**
 *
 * ```java
 * int queueSize = threadPoolManager.getQueueSize();
 * Log.i("ThreadPoolManager", "Queue size: " + queueSize);
 * ```
 *
 * 8. **Implementing a Listener for Monitoring the Pool's State**
 *
 * If you want to monitor changes in the state of the thread pool (e.g., when it is paused or shut down), you can use the `ThreadPoolStateListener`.
 *
 * 8.1 **Adding a state listener:**
 *
 * ```java
 * threadPoolManager.addStateListener(new ThreadPoolManager.ThreadPoolStateListener() {
 *     @Override
 *     public void onPoolPaused() {
 *         Log.i("ThreadPoolManager", "Thread pool paused");
 *     }
 *
 *     @Override
 *     public void onPoolResumed() {
 *         Log.i("ThreadPoolManager", "Thread pool resumed");
 *     }
 *
 *     @Override
 *     public void onPoolShutDown() {
 *         Log.i("ThreadPoolManager", "Thread pool shut down");
 *     }
 * });
 * ```
 *
 * 9. **Dynamically Changing the Thread Pool Size**
 *
 * You can change the size of the thread pool during runtime to optimize its performance on different devices.
 *
 * 9.1 **Changing the core pool size:**
 *
 * ```java
 * threadPoolManager.setCorePoolSize(3);
 * ```
 *
 * 9.2 **Changing the maximum pool size:**
 *
 * ```java
 * threadPoolManager.setMaxPoolSize(6);
 * ```
 *
 * 10. **Waiting for All Tasks to Complete**
 *
 * If you need to wait for all tasks to complete before proceeding, use the `awaitTermination` method.
 *
 * 10.1 **Waiting for all tasks to finish:**
 *
 * ```java
 * try {
 *     boolean terminated = threadPoolManager.awaitTermination(60, TimeUnit.SECONDS);
 *     if (terminated) {
 *         Log.i("ThreadPoolManager", "All tasks completed");
 *     } else {
 *         Log.i("ThreadPoolManager", "Not all tasks completed in the waiting time");
 *     }
 * } catch (InterruptedException e) {
 *     Log.e("ThreadPoolManager", "Error waiting for termination: " + e.getMessage());
 * }
 * ```
 *
 * ================================
 * END OF MANUAL
 * ================================
 */


