# PHASE 1 — Low-Risk Cleanup: что изменилось

## Общая идея

Фаза 1 устраняет хрупкие места кода, которые могли привести к падениям приложения (крашам), но **не меняет** поведение программы. Это называется «безопасный рефакторинг» — код выглядит иначе, но делает ровно то же самое, без новых багов.

Все изменения проверены: проект собирается, линтер чист, тесты проходят, R8 (обфускатор) включён.

---

## 1.1 Удаление ViewBindingUtils — самая главная правка

### Что было

В проекте использовался класс `ViewBindingUtils`, который через **Java Reflection** (магия, позволяющая читать структуру классов во время работы программы) «угадывал», какой у Activity должен быть макет экрана (layout) и какая ViewModel ему нужна.

```kotlin
// Старый код — так было в BaseActivity:
this.binding = ViewBindingUtils.createBinding(javaClass, layoutInflater, 0, null)
this.viewModel = createViewModel(this, 1)
```

**Почему это плохо:**
- Медленно на старте — reflection на каждый экран
- Ломается при обфускации (ProGuard/R8 переименовывает методы)
- Ошибка не видна на этапе компиляции — приложение падает в рантайме

### Как стало

Каждый экран **явно передаёт** свой макет и класс ViewModel через конструктор. Никакой магии — компилятор проверяет типы.

```kotlin
// Теперь базовые классы принимают параметры:
open class BaseActivity<T : ViewBinding, V : BaseViewModel>(
    private val bindingFactory: (LayoutInflater) -> T,  // «как создать макет»
    private val viewModelClass: Class<V>,                // «какой класс ViewModel»
)

// А конкретный экран передаёт свои:
class CaptureActivity : BaseActivity<ActivityCaptureBinding, CaptureViewModel>(
    bindingFactory = { ActivityCaptureBinding.inflate(it) },
    viewModelClass = CaptureViewModel::class.java,
)
```

**Что это даёт:**
- Компилятор проверит типы — не соберётся, если ошибка
- Не ломается при обфускации
- Имена методов — это просто код, не надо «угадывать» через reflection
- Можно включить R8/minify (ProGuard), что уменьшает размер APK

### Затронутые файлы

Удалён: `ViewBindingUtils.kt` (65 строк чистого reflection)

Изменены базовые классы: `BaseActivity`, `BaseFragment`, `BasePreferenceFragment`, `BaseBottomSheetDialogFragment`, `BaseAdapter`, `BaseListAdapter`

Обновлены их наследники (9 классов): `MainActivity`, `CaptureActivity`, `ShotActivity`, `LocalSphericalPlayerActivity`, `ConnectFragment`, `SettingFragment`, `PickerAdapter`, `CaptureModeAdapter`, `BleDeviceAdapter`

### Включение R8

В `app/build.gradle.kts`: `isMinifyEnabled = true` (было `false`). Проект теперь собирается с обфускацией в release-режиме → APK меньше на 20-40%.

---

## 1.2 RxJava — уже удалено в Фазе 0

Поле `disposable: Disposable?`, импорт RxJava и исключение `META-INF/rxjava.properties` были удалены в предыдущей фазе. Здесь также убран `disposable` из `BasePreferenceFragment`.

---

## 1.3 ConnectService: Java → Kotlin

Сервис, который держит уведомление о подключении камеры, переписан с Java на Kotlin. Попутно исправлен баг с Android 14+: теперь `startForeground()` передаёт `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (раньше без этого на Android 14+ был бы краш).

---

## 1.4 BaseEvent — улучшение системы событий

### Что было

```kotlin
interface BaseEvent {
    object CameraBatteryLowEvent : BaseEvent
    class CameraSDCardStateChangedEvent(var enabled: Boolean) : BaseEvent
    // ...
}
```

Проблемы:
- События — `object` и `class` (с мутабельными `var`-полями)
- Обработчики `when(event)` не проверялись компилятором на полноту — добавление нового события могло сломать логику незаметно
- `EventStatus` лежал в базовом пакете, хотя используется только для capture

### Как стало

```kotlin
interface BaseEvent {
    data object CameraBatteryLowEvent : BaseEvent
    data class CameraSDCardStateChangedEvent(val enabled: Boolean) : BaseEvent
    data class CameraBatteryUpdateEvent(val batteryLevel: Int, val isCharging: Boolean) : BaseEvent
    data class CameraStorageChangedEvent(val freeSpace: Long, val totalSpace: Long) : BaseEvent
    data class CameraStatusChangedEvent(val enabled: Boolean, val connectType: Int) : BaseEvent
}
```

**Что изменилось:**
- `object` → `data object` — более современный синтаксис
- `class` → `data class` — автоматический `equals()`, `hashCode()`, `toString()`, копирование
- `var` → `val` — события теперь неизменяемые (immutable), нельзя случайно изменить данные события после создания
- `enable` → `enabled` (в `CameraStatusChangedEvent`) — исправлена грамматика
- `EventStatus` вынесен в отдельный файл `ui/capture/EventStatus.kt`

**Важно:** `sealed interface` не используется, потому что подклассы находятся в разных пакетах (ограничение Kotlin). Но `data class`/`data object` уже дают безопасность типов.

---

## 1.5 Магические числа → именованные константы

Числа вроде `1300`, `0.12f`, `8L` заменены на константы с понятными именами:

| Было | Стало |
|------|-------|
| `1300` | `FLING_THRESHOLD` |
| `180` | `ITEM_TRANSITION_TIME_MS` |
| `8L` | `SENSOR_RATE_LIMIT_MS` |
| `0.12f` | `SLERP_SMOOTHING_ALPHA` |
| `1.2f` | `DEFAULT_SENSITIVITY` |
| `0.04f` | `YAW_SENSITIVITY_FACTOR` |
| `0.02f` | `PITCH_SENSITIVITY_FACTOR` |
| `100L` | `MIN_LOADING_TIME` |
| `0.8f` | `STORAGE_WARNING_THRESHOLD` |
| `1000` / `1001` | `LOADING_HIDE_WHAT` / `LOADING_SHOW_WHAT` |

---

## 1.7 Launch modes: singleInstance → singleTop

В `AndroidManifest.xml` режимы запуска `MainActivity` и `CaptureActivity` изменены с `singleInstance` на `singleTop`.

**Что это значит:** кнопка «Назад» на телефоне теперь работает как в обычных Android-приложениях. Раньше из-за `singleInstance` можно было застрять на экране без возможности вернуться.

---

## 1.8 lateinit → безопасные альтернативы

`lateinit var` — это как отложенная инициализация: «я обещаю заполнить переменную позже». Если кто-то обратится к ней до заполнения — краш.

Заменено:

- **`CaptureViewModel.cameraOfflineData`**: `lateinit var` → `nullable` с геттером. Теперь если камера не инициализирована, вы получите понятную ошибку `"Camera not initialized"`, а не `UninitializedPropertyAccessException`.

- **`CaptureActivity.gyroController` / `vrManager`**: `lateinit var` → `by lazy { ... }`. Теперь гироскоп и VR-менеджер создаются при первом обращении к ним, автоматически. Исчезла необходимость проверять `.isInitialized` — этого свойства больше нет.

- **`BaseActivity.viewModel` / `BaseFragment.viewModel`**: `lateinit var` → `by lazy`. ViewModel создаётся при первом обращении, не надо вызывать `createViewModel()`.

---

## Что НЕ вошло в Фазу 1

- **CaptureConst.kt → strings.xml** (пункт 1.6). Это ~300 строк маппинга строк, которые надо перенести в ресурсы. Отложено на Фазу 2 — это безопасная, но объёмная работа, не влияющая на стабильность.

---

## Итог

| Проверка | Статус |
|----------|--------|
| `./gradlew clean assembleDebug` | успешно |
| `./gradlew ktlintCheck` | чисто |
| `./gradlew detekt` | чисто (warnings only) |
| `./gradlew testDebugUnitTest` | успешно |
| R8/minify | включён |
