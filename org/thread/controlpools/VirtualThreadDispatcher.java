package org.thread.controlpools;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadDispatcher implements Executor {
    private static final VirtualThreadDispatcher INSTANCE = new VirtualThreadDispatcher();
    
    private VirtualThreadDispatcher() {}
    
    public static VirtualThreadDispatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public void execute(Runnable command) {
        Thread.startVirtualThread(command);
    }

    public static ExecutorService createVirtualThreadPerTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
} 