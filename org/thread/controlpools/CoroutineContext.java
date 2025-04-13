package org.thread.controlpools;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class CoroutineContext {
    private final Executor dispatcher;
    private final Consumer<Throwable> exceptionHandler;
    private final String name;

    private CoroutineContext(Builder builder) {
        this.dispatcher = builder.dispatcher;
        this.exceptionHandler = builder.exceptionHandler;
        this.name = builder.name;
    }

    public Executor getDispatcher() {
        return dispatcher;
    }

    public Consumer<Throwable> getExceptionHandler() {
        return exceptionHandler;
    }

    public String getName() {
        return name;
    }

    public CoroutineContext plus(CoroutineContext other) {
        return new Builder()
                .setDispatcher(other.dispatcher != null ? other.dispatcher : this.dispatcher)
                .setExceptionHandler(other.exceptionHandler != null ? other.exceptionHandler : this.exceptionHandler)
                .setName(other.name != null ? other.name : this.name)
                .build();
    }

    public static class Builder {
        private Executor dispatcher;
        private Consumer<Throwable> exceptionHandler;
        private String name;

        public Builder setDispatcher(Executor dispatcher) {
            this.dispatcher = dispatcher;
            return this;
        }

        public Builder setExceptionHandler(Consumer<Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public CoroutineContext build() {
            if (dispatcher == null) {
                dispatcher = Dispatchers.getDefault();
            }
            if (exceptionHandler == null) {
                exceptionHandler = Throwable::printStackTrace;
            }
            return new CoroutineContext(this);
        }
    }
} 