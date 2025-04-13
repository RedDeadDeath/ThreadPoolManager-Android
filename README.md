# Java Coroutine-Like Library

Легковесная библиотека для асинхронного программирования в Java, вдохновленная корутинами Kotlin. Использует виртуальные потоки (Project Loom) для эффективного выполнения асинхронных операций.

## Основные возможности

- 🚀 Легковесные виртуальные потоки
- 🔄 Структурированная конкурентность
- ⚡ Различные диспетчеры выполнения
- 🕒 Удобная работа с задержками
- 🛡️ Автоматическое управление ресурсами
- 🔍 Обработка ошибок
- 🔀 Переключение контекстов выполнения

## Быстрый старт

```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    scope.launch(() -> {
        System.out.println("Привет из корутины!");
    });
}
```

## Подробное руководство

### 1. Создание scope

Scope управляет жизненным циклом корутин. Рекомендуется использовать try-with-resources:

```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    // Ваш код здесь
}
```

### 2. Диспетчеры

Библиотека предоставляет несколько диспетчеров:

```java
Dispatchers.Virtual  // Для легковесных виртуальных потоков
Dispatchers.IO      // Для I/O операций
Dispatchers.Default // По умолчанию (виртуальные потоки)
Dispatchers.Main    // Для Android UI потока
Dispatchers.Unconfined // Для немедленного выполнения
```

### 3. Запуск корутин

#### Простой запуск
```java
scope.launch(() -> {
    System.out.println("Выполняется асинхронно");
});
```

#### Запуск с возвратом значения
```java
Coroutine<String> result = scope.async(() -> {
    return "Результат";
});
String value = result.await(); // Ожидание результата
```

### 4. Работа с задержками

#### Простая задержка
```java
Coroutine.delay(1000, scope).await(); // 1 секунда
```

#### Задержка с TimeUnit
```java
Coroutine.delay(2, TimeUnit.SECONDS, scope).await();
```

#### Задержка с Duration
```java
Coroutine.delay(Duration.ofSeconds(3), scope).await();
```

#### Периодические задержки
```java
Coroutine.delayTicks(500, TimeUnit.MILLISECONDS, 5, scope).await();
```

### 5. Обработка ошибок

```java
scope.launch(() -> {
    try {
        // Потенциально опасный код
    } catch (Exception e) {
        System.err.println("Ошибка: " + e.getMessage());
    }
});
```

### 6. Переключение контекстов

```java
CoroutineUtils.withContext(Dispatchers.IO, () -> {
    // Код будет выполнен в IO контексте
    return null;
}, scope).await();
```

### 7. Утилиты

#### Повторные попытки
```java
CoroutineUtils.retry(3, 1000, () -> {
    // Код с повторными попытками
    return "результат";
}, scope);
```

#### Таймаут
```java
CoroutineUtils.withTimeout(5000, () -> {
    // Код с таймаутом
    return "результат";
}, scope);
```

#### Параллельное выполнение
```java
List<Callable<Integer>> tasks = Arrays.asList(
    () -> compute1(),
    () -> compute2()
);
List<Integer> results = CoroutineUtils.parallel(tasks, scope).await();
```

#### Debounce
```java
Function<String, Coroutine<String>> debounced = 
    CoroutineUtils.debounce(500, scope);
debounced.apply("значение").await();
```

#### Throttle
```java
Function<String, Coroutine<String>> throttled = 
    CoroutineUtils.throttle(1000, scope);
throttled.apply("значение").await();
```

### 8. Отмена

```java
Coroutine<?> job = scope.launch(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        // Длительная операция
    }
});

// Отмена конкретной корутины
job.cancel();

// Отмена всего scope
scope.cancel();
```

### 9. Управление ресурсами

```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    // Добавление ресурса для автоматического закрытия
    scope.addResource(someCloseable);
    
    // Ресурс будет автоматически закрыт при выходе из scope
}
```

### 10. Каналы (Channels)

Каналы позволяют организовать безопасное взаимодействие между корутинами:

```java
// Создание буферизованного канала
CoroutineChannel<String> channel = CoroutineChannel.buffered(10);

// Отправитель
scope.launch(() -> {
    for (int i = 0; i < 5; i++) {
        channel.send("Message " + i, scope).await();
        Coroutine.delay(100, scope).await();
    }
    channel.close();
});

// Получатель
scope.launch(() -> {
    channel.consumeEach(message -> {
        System.out.println("Received: " + message);
    }, scope).await();
});
```

#### Различные типы каналов

```java
// Неограниченный канал
CoroutineChannel<Integer> unlimited = CoroutineChannel.unlimited();

// Буферизованный канал с ограниченной емкостью
CoroutineChannel<String> buffered = CoroutineChannel.buffered(5);
```

#### Отправка и получение

```java
// Асинхронная отправка
channel.send(value, scope).await();

// Асинхронное получение
String value = channel.receive(scope).await();

// Попытка отправки без блокировки
boolean sent = channel.trySend(value);

// Попытка получения без блокировки
String value = channel.tryReceive();
```

### 11. Flow API

Flow API предоставляет реактивный способ работы с потоками данных:

```java
// Создание потока
CoroutineFlow<Integer> flow = CoroutineFlow.range(1, 10);

// Преобразование потока
flow.map(x -> x * 2)
   .filter(x -> x > 5)
   .onEach(x -> System.out.println("Processing: " + x))
   .collect(value -> {
       System.out.println("Result: " + value);
   }, scope).await();
```

#### Операторы Flow

```java
// Преобразование
flow.map(String::length)
    .filter(len -> len > 5)
    .distinctUntilChanged()
    .buffer(10)
    .collect(System.out::println, scope);

// Сбор в список
List<Integer> results = flow.toList(scope).await();
```

#### Создание потоков

```java
// Из коллекции
CoroutineFlow<String> flow1 = CoroutineFlow.from(Arrays.asList("a", "b", "c"));

// Из варгов
CoroutineFlow<Integer> flow2 = CoroutineFlow.of(1, 2, 3, 4, 5);

// Диапазон чисел
CoroutineFlow<Integer> flow3 = CoroutineFlow.range(1, 100);
```

### 12. Комбинирование возможностей

#### Channels + Flow

```java
CoroutineChannel<String> channel = CoroutineChannel.buffered(10);
CoroutineFlow<String> flow = new CoroutineFlow<>(collector -> {
    while (!channel.isClosed()) {
        String value = channel.receive(scope).await();
        collector.emit(value);
    }
});

// Использование
flow.map(String::toUpperCase)
    .filter(s -> s.length() > 5)
    .collect(System.out::println, scope);
```

#### Параллельная обработка с Flow

```java
CoroutineFlow.range(1, 1000)
    .buffer(100) // Буферизация для параллельной обработки
    .map(x -> heavyComputation(x))
    .collect(result -> {
        System.out.println("Processed: " + result);
    }, scope);
```

### 13. Расширенные паттерны

#### Producer-Consumer с каналами

```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    CoroutineChannel<Task> taskChannel = CoroutineChannel.buffered(100);
    
    // Producer
    scope.launch(() -> {
        for (int i = 0; i < 1000; i++) {
            taskChannel.send(new Task(i), scope).await();
        }
        taskChannel.close();
    });

    // Multiple consumers
    for (int i = 0; i < 5; i++) {
        scope.launch(() -> {
            taskChannel.consumeEach(task -> {
                processTask(task);
            }, scope).await();
        });
    }
}
```

#### Реактивные обновления UI

```java
CoroutineFlow<String> searchFlow = new CoroutineFlow<>(collector -> {
    // Имитация поиска
    for (String result : searchResults) {
        collector.emit(result);
        Coroutine.delay(100, scope).await();
    }
});

searchFlow
    .debounce(300) // Предотвращение частых обновлений
    .distinctUntilChanged() // Только при изменении данных
    .onEach(result -> {
        // Обновление UI
        updateSearchResults(result);
    })
    .collect(scope);
```

## Лучшие практики

1. **Используйте try-with-resources** для автоматического закрытия ресурсов:
   ```java
   try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
       // Ваш код
   }
   ```

2. **Выбирайте правильный диспетчер**:
   - `Virtual` - для большинства задач
   - `IO` - для операций ввода/вывода
   - `Main` - для UI операций в Android

3. **Обрабатывайте ошибки** внутри корутин:
   ```java
   scope.launch(() -> {
       try {
           riskyOperation();
       } catch (Exception e) {
           handleError(e);
       }
   });
   ```

4. **Используйте структурированную конкурентность**:
   ```java
   scope.launch(() -> {
       // Дочерние корутины
       scope.launch(() -> { /* ... */ });
       scope.launch(() -> { /* ... */ });
   });
   ```

5. **Отменяйте неиспользуемые корутины**:
   ```java
   Coroutine<?> job = scope.launch(() -> /* ... */ });
   if (condition) {
       job.cancel();
   }
   ```

## Примеры использования

### Пример 1: Асинхронная загрузка данных
```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.IO)) {
    scope.launch(() -> {
        // Имитация загрузки
        Coroutine.delay(1000, scope).await();
        System.out.println("Данные загружены");
    });
}
```

### Пример 2: Параллельные вычисления
```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    List<Callable<Integer>> tasks = Arrays.asList(
        () -> heavyComputation1(),
        () -> heavyComputation2()
    );
    List<Integer> results = CoroutineUtils.parallel(tasks, scope).await();
}
```

### Пример 3: Периодические задачи
```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    CoroutineUtils.interval(1000, () -> {
        System.out.println("Tick: " + System.currentTimeMillis());
    }, scope);
}
```

## Требования

- Java 21 или выше (для поддержки виртуальных потоков)
- Android API 21 или выше (для Android-специфичных функций)

## Лицензия

MIT License 

## Улучшения и расширенные возможности

### 1. Виртуальные потоки (Project Loom)

Библиотека теперь использует виртуальные потоки Java (Project Loom) вместо обычных потоков:

```java
// Создание тысяч легковесных корутин
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    for (int i = 0; i < 10_000; i++) {
        scope.launch(() -> {
            // Каждая корутина использует виртуальный поток
            Coroutine.delay(100, scope).await();
            processData();
        });
    }
}
```

### 2. Улучшенная система задержек

#### Поддержка различных временных единиц
```java
// Миллисекунды
Coroutine.delay(100, scope).await();

// Секунды
Coroutine.delay(2, TimeUnit.SECONDS, scope).await();

// Java Duration
Coroutine.delay(Duration.ofMinutes(1), scope).await();
```

#### Периодические задержки с отслеживанием тиков
```java
// 5 тиков с интервалом 500мс
Coroutine.delayTicks(500, TimeUnit.MILLISECONDS, 5, scope).await();
```

### 3. Автоматическое управление ресурсами

#### Автозакрытие ресурсов
```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    // Добавление ресурсов для автоматического закрытия
    scope.addResource(new FileInputStream("file.txt"));
    scope.addResource(new BufferedReader(new FileReader("data.txt")));
    
    // Все ресурсы будут автоматически закрыты при выходе из scope
}
```

#### Shutdown Hook
```java
// Библиотека автоматически регистрирует shutdown hook
// для корректного завершения всех корутин при остановке JVM
Runtime.getRuntime().addShutdownHook(new Thread(Dispatchers::shutdown));
```

### 4. Расширенные утилиты

#### Flow-подобные операторы
```java
// Debounce - пропускает повторные вызовы в течение указанного времени
Function<String, Coroutine<String>> debounced = 
    CoroutineUtils.debounce(500, scope);

// Throttle - ограничивает частоту вызовов
Function<String, Coroutine<String>> throttled = 
    CoroutineUtils.throttle(1000, scope);
```

#### Интервальные операции
```java
// Периодическое выполнение с возможностью отмены
Coroutine<Void> ticker = CoroutineUtils.interval(1000, () -> {
    System.out.println("Tick");
}, scope);

// Отмена после 5 секунд
Coroutine.delay(5000, scope).await();
ticker.cancel();
```

### 5. Улучшенная обработка ошибок

#### Глобальный обработчик ошибок
```java
CoroutineContext context = new CoroutineContext.Builder()
    .setDispatcher(Dispatchers.Virtual)
    .setExceptionHandler(e -> {
        logger.error("Глобальная ошибка в корутине", e);
        metrics.incrementErrorCount();
    })
    .build();
```

#### Структурированная обработка ошибок
```java
scope.launch(() -> {
    try {
        riskyOperation();
    } catch (SpecificException e) {
        // Обработка конкретной ошибки
        handleSpecificError(e);
    } catch (Exception e) {
        // Общая обработка
        handleGenericError(e);
    } finally {
        // Очистка ресурсов
        cleanup();
    }
});
```

### 6. Оптимизации производительности

- Использование виртуальных потоков вместо пула потоков
- Эффективное управление памятью через WeakReference
- Автоматическая очистка завершенных задач
- Оптимизированные диспетчеры для разных типов задач

### 7. Интеграция с Android

#### Главный поток Android
```java
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Main)) {
    // Фоновая операция
    scope.launch(() -> {
        // Загрузка данных в фоне
        String data = loadData();
        
        // Обновление UI в главном потоке
        scope.launch(() -> {
            updateUI(data);
        });
    });
}
```

#### Lifecycle-aware корутины
```java
public class MainActivity extends AppCompatActivity {
    private final CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Main);

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scope.cancel(); // Автоматическая отмена всех корутин
    }
}
```

## Сравнение с Kotlin корутинами

| Функциональность | Java Coroutine-Like | Kotlin Coroutines |
|------------------|---------------------|-------------------|
| Синтаксис | Использует лямбды | Использует suspend функции |
| Производительность | Виртуальные потоки | Continuations |
| Scope | Явный через try-with-resources | Структурированный через builders |
| Диспетчеры | Virtual, IO, Main, etc. | Default, IO, Main, etc. |
| Flow | Базовые операторы | Полная поддержка Flow API |
| Интеграция с языком | Через API | Встроенная поддержка |

## Оптимизация для слабых устройств

Библиотека предоставляет встроенную систему оптимизации для работы на устройствах с ограниченными ресурсами:

### 1. Автоматическая адаптация

```java
// Инициализация оптимизатора
ResourceOptimizer optimizer = new ResourceOptimizer(context);

// Получение оптимизированного контекста
CoroutineContext optimizedContext = optimizer.getOptimizedContext();

// Создание scope с оптимизированным контекстом
try (CoroutineScope scope = new CoroutineScopeImpl(optimizedContext)) {
    // Ваш код будет автоматически оптимизирован
}
```

### 2. Оптимизация каналов и потоков

```java
// Оптимизированный канал
CoroutineChannel<String> channel = optimizer.createOptimizedChannel();

// Оптимизация Flow
CoroutineFlow<Integer> flow = CoroutineFlow.range(1, 1000);
CoroutineFlow<Integer> optimizedFlow = optimizer.optimizeFlow(flow);
```

### 3. Адаптивные настройки

- Автоматическое определение возможностей устройства
- Динамическая настройка размеров буферов
- Оптимизация использования потоков
- Снижение приоритетов фоновых задач

### 4. Рекомендации по оптимизации

```java
OptimizationRecommendations recommendations = optimizer.getRecommendations();
if (recommendations.shouldUseMinimalProcessing) {
    // Использовать минимальную обработку
    // Ограничить параллельные операции
}

if (recommendations.shouldLimitStorage) {
    // Ограничить использование памяти
    // Использовать меньшие буферы
}
```

### 5. Автоматические оптимизации

- Уменьшение размера пула потоков на слабых устройствах
- Снижение приоритета фоновых операций
- Оптимизация буферов для экономии памяти
- Использование IO диспетчера вместо Virtual на слабых устройствах
- Автоматическая очистка неиспользуемых ресурсов

### 6. Пример использования для слабых устройств

```java
ResourceOptimizer optimizer = new ResourceOptimizer(context);
CoroutineContext optimizedContext = optimizer.getOptimizedContext();

try (CoroutineScope scope = new CoroutineScopeImpl(optimizedContext)) {
    // Создание оптимизированного канала
    CoroutineChannel<Data> channel = optimizer.createOptimizedChannel();
    
    // Запуск обработки с учетом ограничений устройства
    scope.launch(() -> {
        // Оптимизация текущего потока
        optimizer.optimizeThread();
        
        // Обработка данных с учетом рекомендаций
        OptimizationRecommendations rec = optimizer.getRecommendations();
        int batchSize = rec.shouldUseMinimalProcessing ? 10 : 50;
        
        processDataInBatches(channel, batchSize);
    });
}
```

### 7. Рекомендации по использованию

1. **Всегда используйте оптимизатор на Android**:
   ```java
   ResourceOptimizer optimizer = new ResourceOptimizer(context);
   ```

2. **Учитывайте рекомендации**:
   ```java
   if (optimizer.isLowMemoryDevice()) {
       // Использовать облегченный режим
   }
   ```

3. **Оптимизируйте размеры буферов**:
   ```java
   int optimalSize = optimizer.getOptimalBufferSize();
   CoroutineChannel<T> channel = CoroutineChannel.buffered(optimalSize);
   ```

4. **Ограничивайте параллелизм**:
   ```java
   int maxThreads = optimizer.getMaxPoolSize();
   // Использовать не более maxThreads параллельных операций
   ```

5. **Используйте оптимизированные контексты**:
   ```java
   CoroutineContext ctx = optimizer.getOptimizedContext();
   // Создавать scope с оптимизированным контекстом
   ```

## Упрощенный синтаксис

### 1. Быстрый запуск с использованием статических импортов

```java
import static org.thread.controlpools.Coroutines.*;
import static org.thread.controlpools.Dispatchers.*;

// Простой запуск
runAsync(() -> {
    System.out.println("Привет из корутины!");
});

// С возвратом значения
String result = await(() -> "Результат");
```

### 2. Fluent API для конфигурации

```java
// Вместо
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Virtual)) {
    scope.launch(() -> { ... });
}

// Используйте
Coroutines.create()
    .withDispatcher(Virtual)
    .withTimeout(5000)
    .run(() -> {
        // Ваш код здесь
    });
```

### 3. Упрощенные каналы

```java
// Вместо
CoroutineChannel<String> channel = CoroutineChannel.buffered(10);
channel.send("message", scope).await();

// Используйте
Channel<String> channel = Channel.of(10);
channel.send("message");

// Простое получение всех значений
channel.forEach(message -> {
    System.out.println(message);
});
```

### 4. Улучшенный Flow API

```java
// Вместо
CoroutineFlow<Integer> flow = CoroutineFlow.range(1, 10);
flow.map(x -> x * 2)
   .filter(x -> x > 5)
   .collect(System.out::println, scope).await();

// Используйте
Flow.range(1, 10)
    .map(x -> x * 2)
    .filter(x -> x > 5)
    .forEach(System.out::println);
```

### 5. Встроенные утилиты

```java
// Задержка
sleep(1000); // вместо Coroutine.delay(1000, scope).await()

// Повторные попытки
retry(3, () -> {
    // Ваш код здесь
});

// Таймаут
withTimeout(5000, () -> {
    // Ваш код здесь
});
```

### 6. Простая обработка ошибок

```java
// Вместо
scope.launch(() -> {
    try {
        riskyOperation();
    } catch (Exception e) {
        handleError(e);
    }
});

// Используйте
Coroutines.create()
    .withErrorHandler(e -> handleError(e))
    .run(() -> riskyOperation());
```

### 7. Упрощенная оптимизация для слабых устройств

```java
// Вместо
ResourceOptimizer optimizer = new ResourceOptimizer(context);
CoroutineContext optimizedContext = optimizer.getOptimizedContext();
try (CoroutineScope scope = new CoroutineScopeImpl(optimizedContext)) {
    // ...
}

// Используйте
Coroutines.optimized(context)
    .run(() -> {
        // Автоматически оптимизированный код
    });
```

### 8. Параллельное выполнение

```java
// Вместо
List<Callable<Integer>> tasks = Arrays.asList(
    () -> compute1(),
    () -> compute2()
);
List<Integer> results = CoroutineUtils.parallel(tasks, scope).await();

// Используйте
List<Integer> results = Coroutines.parallel(
    () -> compute1(),
    () -> compute2()
);
```

### 9. Периодические задачи

```java
// Вместо
CoroutineUtils.interval(1000, () -> {
    System.out.println("Tick");
}, scope);

// Используйте
Coroutines.every(1000)
    .run(() -> System.out.println("Tick"))
    .stopAfter(Duration.ofMinutes(5));
```

### 10. Комбинирование операций

```java
// Цепочка асинхронных операций
Coroutines.create()
    .thenAsync(() -> loadData())
    .thenAsync(data -> processData(data))
    .thenAsync(result -> saveResult(result))
    .whenComplete(this::updateUI);

// Параллельное выполнение с объединением результатов
Coroutines.zip(
    () -> loadUserData(),
    () -> loadUserPreferences(),
    (data, prefs) -> combineResults(data, prefs)
);
```

### 11. Интеграция с Android

```java
// Вместо явного управления контекстом
try (CoroutineScope scope = new CoroutineScopeImpl(Dispatchers.Main)) {
    scope.launch(() -> updateUI());
}

// Используйте
Coroutines.onMain(() -> updateUI());

// Фоновая загрузка с обновлением UI
Coroutines.background(() -> loadData())
    .thenOnMain(data -> updateUI(data));
```

### 12. Работа с ресурсами

```java
// Автоматическое закрытие ресурсов
Coroutines.withResources(
    () -> new FileInputStream("file.txt"),
    stream -> {
        // Использование ресурса
    }
);
```
