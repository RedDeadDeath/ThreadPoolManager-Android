package org.thread.controlpools;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoroutineChannel<T> implements AutoCloseable {
    private final BlockingQueue<T> queue;
    private final int capacity;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public CoroutineChannel(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public static <T> CoroutineChannel<T> unlimited() {
        return new CoroutineChannel<>(Integer.MAX_VALUE);
    }

    public static <T> CoroutineChannel<T> buffered(int capacity) {
        return new CoroutineChannel<>(capacity);
    }

    public Coroutine<Void> send(T value, CoroutineScope scope) {
        return scope.launch(() -> {
            if (isClosed.get()) {
                throw new ChannelClosedException("Channel is closed");
            }
            queue.put(value);
        });
    }

    public Coroutine<T> receive(CoroutineScope scope) {
        return scope.async(() -> {
            if (isClosed.get() && queue.isEmpty()) {
                throw new ChannelClosedException("Channel is closed and empty");
            }
            return queue.take();
        });
    }

    public boolean trySend(T value) {
        if (isClosed.get()) {
            return false;
        }
        return queue.offer(value);
    }

    public T tryReceive() {
        return queue.poll();
    }

    public Coroutine<Void> consumeEach(Consumer<T> consumer, CoroutineScope scope) {
        return scope.launch(() -> {
            while (!isClosed.get() || !queue.isEmpty()) {
                T value = queue.take();
                consumer.accept(value);
            }
        });
    }

    @Override
    public void close() {
        isClosed.set(true);
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public int getCapacity() {
        return capacity;
    }

    public interface Consumer<T> {
        void accept(T value) throws Exception;
    }

    public static class ChannelClosedException extends RuntimeException {
        public ChannelClosedException(String message) {
            super(message);
        }
    }
} 