package org.thread.controlpools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class CoroutineFlow<T> {
    private final FlowEmitter<T> emitter;

    private CoroutineFlow(FlowEmitter<T> emitter) {
        this.emitter = emitter;
    }

    public static <T> CoroutineFlow<T> from(Iterable<T> items) {
        return new CoroutineFlow<>(collector -> {
            for (T item : items) {
                collector.emit(item);
            }
        });
    }

    public static <T> CoroutineFlow<T> of(T... items) {
        return new CoroutineFlow<>(collector -> {
            for (T item : items) {
                collector.emit(item);
            }
        });
    }

    public static CoroutineFlow<Integer> range(int start, int count) {
        return new CoroutineFlow<>(collector -> {
            for (int i = 0; i < count; i++) {
                collector.emit(start + i);
            }
        });
    }

    public <R> CoroutineFlow<R> map(Function<T, R> transform) {
        return new CoroutineFlow<>(collector -> 
            emitter.emit(new FlowCollector<T>() {
                @Override
                public void emit(T value) throws Exception {
                    collector.emit(transform.apply(value));
                }
            })
        );
    }

    public CoroutineFlow<T> filter(Predicate<T> predicate) {
        return new CoroutineFlow<>(collector ->
            emitter.emit(new FlowCollector<T>() {
                @Override
                public void emit(T value) throws Exception {
                    if (predicate.test(value)) {
                        collector.emit(value);
                    }
                }
            })
        );
    }

    public Coroutine<List<T>> toList(CoroutineScope scope) {
        return scope.async(() -> {
            List<T> result = new ArrayList<>();
            collect(value -> result.add(value), scope).await();
            return result;
        });
    }

    public Coroutine<Void> collect(FlowCollector<T> collector, CoroutineScope scope) {
        return scope.launch(() -> emitter.emit(collector));
    }

    public CoroutineFlow<T> onEach(FlowCollector<T> action) {
        return new CoroutineFlow<>(collector ->
            emitter.emit(new FlowCollector<T>() {
                @Override
                public void emit(T value) throws Exception {
                    action.emit(value);
                    collector.emit(value);
                }
            })
        );
    }

    public CoroutineFlow<T> buffer(int capacity) {
        return new CoroutineFlow<>(collector -> {
            CoroutineChannel<T> channel = new CoroutineChannel<>(capacity);
            CompletableFuture<?> producer = CompletableFuture.runAsync(() -> {
                try {
                    emitter.emit(value -> channel.trySend(value));
                } catch (Exception e) {
                    channel.close();
                }
            });

            while (!channel.isClosed() || producer.isDone()) {
                T value = channel.tryReceive();
                if (value != null) {
                    collector.emit(value);
                }
            }
        });
    }

    public CoroutineFlow<T> distinctUntilChanged() {
        return new CoroutineFlow<>(collector -> {
            T[] previous = (T[]) new Object[1];
            emitter.emit(new FlowCollector<T>() {
                @Override
                public void emit(T value) throws Exception {
                    if (previous[0] == null || !previous[0].equals(value)) {
                        previous[0] = value;
                        collector.emit(value);
                    }
                }
            });
        });
    }

    @FunctionalInterface
    public interface FlowEmitter<T> {
        void emit(FlowCollector<T> collector) throws Exception;
    }

    @FunctionalInterface
    public interface FlowCollector<T> {
        void emit(T value) throws Exception;
    }
} 