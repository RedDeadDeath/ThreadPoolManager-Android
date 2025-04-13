package org.thread.controlpools;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.StatFs;
import android.util.Log;

public class ResourceOptimizer {
    private static final String TAG = "ResourceOptimizer";
    private static final int MIN_MEMORY_CLASS = 128; // MB
    private static final long MIN_FREE_STORAGE = 500 * 1024 * 1024; // 500MB
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    
    private final Context context;
    private final ActivityManager activityManager;
    private boolean isLowMemoryDevice;
    private boolean isLowStorageDevice;
    private int memoryClass;
    private int maxPoolSize;
    private int bufferSize;

    public ResourceOptimizer(Context context) {
        this.context = context;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        analyzeDevice();
    }

    private void analyzeDevice() {
        // Проверка памяти
        memoryClass = activityManager.getMemoryClass();
        isLowMemoryDevice = memoryClass <= MIN_MEMORY_CLASS;

        // Проверка хранилища
        StatFs stat = new StatFs(context.getFilesDir().getPath());
        long availableBytes = stat.getAvailableBytes();
        isLowStorageDevice = availableBytes < MIN_FREE_STORAGE;

        // Определение оптимальных параметров
        adjustParameters();
        
        Log.i(TAG, String.format("Device analysis: Memory Class: %dMB, Low Memory: %b, Low Storage: %b",
                memoryClass, isLowMemoryDevice, isLowStorageDevice));
    }

    private void adjustParameters() {
        // Базовые параметры
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        
        // Настройка размера пула потоков
        if (isLowMemoryDevice) {
            maxPoolSize = Math.min(2, availableProcessors);
            bufferSize = DEFAULT_BUFFER_SIZE / 2;
        } else if (memoryClass < 256) {
            maxPoolSize = Math.min(availableProcessors / 2, 4);
            bufferSize = DEFAULT_BUFFER_SIZE;
        } else {
            maxPoolSize = availableProcessors;
            bufferSize = DEFAULT_BUFFER_SIZE * 2;
        }

        // Дополнительные корректировки для старых устройств
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            maxPoolSize = Math.min(maxPoolSize, 2);
            bufferSize = Math.min(bufferSize, DEFAULT_BUFFER_SIZE);
        }
    }

    public void optimizeThread() {
        if (isLowMemoryDevice) {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    public CoroutineContext getOptimizedContext() {
        return new CoroutineContext.Builder()
                .setDispatcher(isLowMemoryDevice ? Dispatchers.IO : Dispatchers.Virtual)
                .setName("Optimized")
                .setExceptionHandler(e -> Log.e(TAG, "Error in optimized context", e))
                .build();
    }

    public <T> CoroutineChannel<T> createOptimizedChannel() {
        return CoroutineChannel.buffered(getOptimalBufferSize());
    }

    public int getOptimalBufferSize() {
        return bufferSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public boolean isLowMemoryDevice() {
        return isLowMemoryDevice;
    }

    public boolean isLowStorageDevice() {
        return isLowStorageDevice;
    }

    // Оптимизированные настройки для Flow
    public <T> CoroutineFlow<T> optimizeFlow(CoroutineFlow<T> flow) {
        return flow.buffer(getOptimalBufferSize());
    }

    // Рекомендации по оптимизации
    public OptimizationRecommendations getRecommendations() {
        return new OptimizationRecommendations(
            isLowMemoryDevice,
            isLowStorageDevice,
            maxPoolSize,
            bufferSize,
            memoryClass
        );
    }

    public static class OptimizationRecommendations {
        public final boolean shouldUseMinimalProcessing;
        public final boolean shouldLimitStorage;
        public final int recommendedPoolSize;
        public final int recommendedBufferSize;
        public final int memoryClass;

        OptimizationRecommendations(
            boolean shouldUseMinimalProcessing,
            boolean shouldLimitStorage,
            int recommendedPoolSize,
            int recommendedBufferSize,
            int memoryClass
        ) {
            this.shouldUseMinimalProcessing = shouldUseMinimalProcessing;
            this.shouldLimitStorage = shouldLimitStorage;
            this.recommendedPoolSize = recommendedPoolSize;
            this.recommendedBufferSize = recommendedBufferSize;
            this.memoryClass = memoryClass;
        }
    }
} 