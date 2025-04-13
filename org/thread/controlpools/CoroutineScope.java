package org.thread.controlpools;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public interface CoroutineScope {
    <T> Coroutine<T> launch(Callable<T> task);
    
    Coroutine<Void> launch(Runnable task);
    
    <T> Coroutine<T> async(Callable<T> task);
    
    void cancel();
    
    boolean isActive();
    
    CoroutineScope plus(CoroutineContext context);
    
    CoroutineContext getContext();
} 