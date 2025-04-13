package org.thread.controlpools;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Dispatchers {
    private static volatile Executor defaultDispatcher;
    private static volatile Executor ioDispatcher;
    private static volatile Executor mainDispatcher;
    private static volatile Executor unconfined;
    private static volatile Executor virtualDispatcher;
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);

    private Dispatchers() {}

    public static Executor getDefault() {
        if (defaultDispatcher == null) {
            synchronized (Dispatchers.class) {
                if (defaultDispatcher == null) {
                    defaultDispatcher = VirtualThreadDispatcher.createVirtualThreadPerTaskExecutor();
                }
            }
        }
        return defaultDispatcher;
    }

    public static Executor getIO() {
        if (ioDispatcher == null) {
            synchronized (Dispatchers.class) {
                if (ioDispatcher == null) {
                    ioDispatcher = VirtualThreadDispatcher.createVirtualThreadPerTaskExecutor();
                }
            }
        }
        return ioDispatcher;
    }

    public static Executor getMain() {
        if (mainDispatcher == null) {
            synchronized (Dispatchers.class) {
                if (mainDispatcher == null) {
                    mainDispatcher = new MainThreadExecutor();
                }
            }
        }
        return mainDispatcher;
    }

    public static Executor getVirtual() {
        if (virtualDispatcher == null) {
            synchronized (Dispatchers.class) {
                if (virtualDispatcher == null) {
                    virtualDispatcher = VirtualThreadDispatcher.getInstance();
                }
            }
        }
        return virtualDispatcher;
    }

    public static Executor getUnconfined() {
        if (unconfined == null) {
            synchronized (Dispatchers.class) {
                if (unconfined == null) {
                    unconfined = Runnable::run;
                }
            }
        }
        return unconfined;
    }

    public static CoroutineContext Default = new CoroutineContext.Builder()
            .setDispatcher(getDefault())
            .setName("Default")
            .build();

    public static CoroutineContext IO = new CoroutineContext.Builder()
            .setDispatcher(getIO())
            .setName("IO")
            .build();

    public static CoroutineContext Main = new CoroutineContext.Builder()
            .setDispatcher(getMain())
            .setName("Main")
            .build();

    public static CoroutineContext Virtual = new CoroutineContext.Builder()
            .setDispatcher(getVirtual())
            .setName("Virtual")
            .build();

    public static CoroutineContext Unconfined = new CoroutineContext.Builder()
            .setDispatcher(getUnconfined())
            .setName("Unconfined")
            .build();

    public static void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            if (defaultDispatcher instanceof ExecutorService) {
                ((ExecutorService) defaultDispatcher).shutdown();
            }
            if (ioDispatcher instanceof ExecutorService) {
                ((ExecutorService) ioDispatcher).shutdown();
            }
        }
    }

    // Регистрируем shutdown hook для автоматического закрытия
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Dispatchers::shutdown));
    }
} 