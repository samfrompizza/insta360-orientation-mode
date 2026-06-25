# PROBLEMS.md — Слабые места проекта

## 1. Reflection-хак для ViewBinding + ViewModel (КРИТИЧНО)

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/util/ViewBindingUtils.kt:14-47`

Через Java Reflection выдирается generic-параметр из `BaseActivity<T, V>`, затем через `getMethod("inflate", ...)` вызывается `inflate()`, а ViewModel создаётся через `ViewModelProvider(owner)[tClass]`.

**Почему это плохо:**
- Медленно на старте — reflection на каждый экран
- Ломается при обфускации (ProGuard/R8) — имена методов `inflate` будут переименованы
- Теряется type safety — если generic-параметр не ViewBinding, ошибка будет в рантайме, а не на компиляции
- Абсолютно не нужно — в Kotlin есть `by viewModels()` и стандартные ViewBinding-делегаты

**Как надо:**
```kotlin
class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val viewModel: MainViewModel by viewModels()
}
```

---

## 2. BaseActivity — God Object (СЕРЬЁЗНО)

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/base/BaseActivity.kt`

Базовый класс знает про:
- ImmersionBar (иммерсивный режим)
- LoadingView с очередью и Handler'ом (`MIN_LOADING_TIME`, магические коды `1000`/`1001`)
- Toast'ы (4 перегрузки + `lastToast` через `InstaApp.instance.lastActivity`)
- Обработку BaseEvent'ов: батарея, SD-карта, хранилище (бизнес-логика)
- Lifecycle-логирование каждого коллбека
- `lifecycleScope.cancel()` в `onDestroy` — агрессивно убивает ВСЕ корутины

**Нарушение:** Single Responsibility Principle. Базовый класс должен быть тонким. Toast'ы, loading, иммерсивный режим — это утилиты, их место в отдельных компонентах.

**Конкретный баг:** `companion object { private var isCharging }` (строка 32) — глобальная переменная на все Activity. Если открыты два экрана, один перезатрёт состояние другого.

---

## 3. BaseFragment тащит RxJava ради одного поля

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/base/BaseFragment.kt:26`

```kotlin
protected open var disposable: Disposable? = null
```

В проекте используется Kotlin Coroutines для всего (flows, `lifecycleScope`, `viewModelScope`). RxJava (`io.reactivex.disposables.Disposable`) нужен только ради этого одного nullable поля, которое очищается в `onDestroyView()`. Ни в одном фрагменте оно реально не используется.
Это мёртвая зависимость, которая тянет RxJava в граф сборки без необходимости.

---

## 4. CaptureViewModel — God Object (649 строк) (КРИТИЧНО)

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/ui/capture/CaptureViewModel.kt`

Один класс одновременно делает ВСЁ:
- Имплементит `IPreviewStatusListener` и `ICaptureStatusListener`
- Управляет 5-шаговой инициализацией камеры (`initCapture()`)
- Переключает режимы съёмки (`switchCaptureMode()`)
- Управляет live-стримингом (`startLive()`, `stopLive()`)
- Управляет записью: 12 режимов start + 12 режимов stop + фото (`startRecord()`, `stopRecord()`, `takePhotos()`)
- Управляет preview-стримом (`openPreviewStream()`, `closePreviewStream()`, `reopenPreviewStream()`)
- Обрабатывает параметры окна (`cameraPreviewStreamParamsChanged()`, `shouldUpdateWindowCrop()`, `createWindowCropInfo()`)

**Нарушено:** Single Responsibility Principle, минимум **5 разных ответственностей** в одном классе. При любом изменении в SDK ты вынужден лезть в этот монолит.

Особенно показательна `onCaptureFinishEnd()` (строка 516-539): бизнес-логика с проверкой на «баг камеры с H264/H265» и условным переоткрытием стрима зашита прямо во ViewModel. Это должно быть в отдельном UseCase/Interactor.

---

## 5. `openPreviewStreamListener` — callback hell внутри корутин

**Файл:** `CaptureViewModel.kt:40, 392-403`

```kotlin
private var openPreviewStreamListener: ((Boolean) -> Unit)? = null
```

`openPreviewStream()` использует `suspendCancellableCoroutine`, но сохраняет лямбду в поле класса и потом вызывает её из `onOpened()`/`onError()`. Это хрупко: если `onOpened` вызовется дважды (баг SDK), лямбда уже null и `resume` не вызовется — корутина зависнет навсегда.

**Как надо:** `callbackFlow { ... }` или `suspendCoroutine` без сохранения мутабельного состояния в поле.

---

## 6. GyroOrientationController — нарушение слоёв (КРИТИЧНО)

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/ui/capture/GyroOrientationController.kt`

### 6a. Контроллер сенсора знает про UI
`applyOrientation` callback вызывается прямо из `onSensorChanged()` (строка 230/245). Контроллер сенсора не должен знать, куда уходят его данные. Это должен быть `StateFlow<GazeState>`, который UI сам подписывает.

### 6b. Quaternion внутри класса на 200 строк
Строки 310-495 — полноценный класс кватерниона (умножение, нормализация, SLERP, Euler, Shepperd), вложенный прямо в контроллер. Это utility-тип, он должен жить отдельно. Хуже того — **второй такой же класс** (`UnitQuaternion`) лежит в `EquirectangularProjection.kt` с теми же операциями (conjugate, normalize, rotate, fromAxisAngle). **Два дублирующих класса кватернионов в одном проекте!**

### 6c. Глобальное мутабельное состояние
```kotlin
companion object {
    var sensivity: Float = 1.2f   // опечатка: sensitivity
    private val yawFactor = 0.04f
    private val pitchFactor = 0.02f
    var invertYaw = false
    var invertPitch = true
}
```
Все эти переменные — глобальное мутабельное состояние, изменяемое извне через companion object. Делает поведение гироскопа непредсказуемым и непотокобезопасным.

---

## 7. CaptureActivity.tryApplyOrientationToPlayer() — reflection на продакшене

**Файл:** `CaptureActivity.kt:428-445`

Для применения yaw/pitch к SDK-плееру используется `getMethod("setYaw")` / `getMethod("setPitch")` через Java reflection. Если SDK переименует методы — приложение молча сломается в рантайме. SDK-плеер должен либо реализовывать публичный интерфейс, либо SDK должен предоставлять документированное API для управления ориентацией.

---

## 8. Дублирование VrManager и LocalVrManager (~80%)

**Файлы:** `VrManager.kt` (615 строк), `LocalVrManager.kt` (339 строк)

Оба класса делают одно и то же:
- PixelCopy-луп на 30fps для копирования правого глаза в левый ImageView
- VR settings dialog (scale, spacing, sensitivity) — идентичный код
- `applyOrientation` с IPD offset 3°
- Скрытие/показ UI при входе/выходе из VR

Разница только в источнике кадра: `VrManager` работает с `InstaCapturePlayerView` (SDK-плеер), `LocalVrManager` — с ExoPlayer SurfaceView. Оба класса можно было свести к одному параметризованному через общий интерфейс источника.

---

## 9. Синглтоны повсюду — тестировать невозможно

Весь проект завязан на глобальное мутабельное состояние:
- `InstaApp.instance` — глобальный `lateinit var` на Application
- `UsbMgr` — `object`
- `NetworkManager` — `object`
- `Pref` — `object`, читает SharedPreferences
- `instaCameraManager` (через экстеншн) — глобальный getter SDK-менеджера

**Следствие:** ни один класс нельзя протестировать изолированно — все зависимости жёстко зашиты. Нет Dependency Injection (Dagger/Hilt/Koin). Любой unit-тест потребует реальную камеру.

---

## 10. BaseEvent — не sealed (ПРОБЛЕМА)

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/base/BaseEvent.kt`

```kotlin
interface BaseEvent { ... }  // должно быть sealed interface
```

Это `interface`, а не `sealed interface`. Компилятор не может проверить exhaustive `when` — везде приходится добавлять `else -> {}`. Если добавится новый тип события, ни одна `when`-ветка не подсветится ошибкой — баги будут молчаливыми и их не отловить на компиляции.

---

## 11. CaptureEvent — смесь пяти ответственностей

**Файл:** `CaptureEvent.kt`

Один sealed-подобный класс содержит события для:
1. Init capture (шаги инициализации)
2. Switch capture mode
3. Camera preview stream params
4. Camera capture (статус, время, счётчик, ошибки)
5. Camera live (RTMP, push started/finished/error)

**Нарушение:** Interface Segregation Principle. Каждая группа событий должна быть отдельным sealed-классом. Потребители (Activity) вынуждены обрабатывать все типы, даже те, что к ним не относятся.

---

## 12. Проблемы Android-специфичные

### 12a. `singleInstance` launch mode
`MainActivity` и `CaptureActivity` объявлены как `singleInstance` в манифесте. Это означает, что каждое Activity живёт в своей отдельной task. System back перестаёт работать нормально — пользователь застревает на экране.

### 12b. `ConnectService.startForeground()` на Android 14+
С Android 14 `startForeground()` без указания `foregroundServiceType` в вызове — это краш. В AndroidManifest тип указан (`connectedDevice`), но в коде Java `startForeground(id, notification)` вызывается без второго параметра с типом. На 14+ нужна перегрузка с `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.

### 12c. WiFi-пароли в SharedPreferences открытым текстом
`ConnectViewModel` сохраняет пароль от камеры через `SPUtils.putString()` в открытое хранилище без шифрования. Это уязвимость безопасности.

### 12d. `bindProcessToNetwork(null)` без проверки ошибок
В `CaptureViewModel.initCameraSupportConfig()` дважды вызывается `bindProcessToNetwork(null)` после HTTP-запроса, но возвращаемое значение не проверяется. Если анбинд не удался — процесс остаётся привязан к сети камеры, и следующий HTTP-запрос уйдёт в никуда.

---

## 13. `lateinit` на всём — риск UninitializedPropertyAccessException

- `BaseActivity.viewModel` (строка 40) — **публичный lateinit**, любой код снаружи может дёрнуть до инициализации
- `BaseFragment.viewModel` — то же самое
- `CaptureViewModel.cameraOfflineData` (строка 44) — `lateinit var` с `private set`, инициализируется глубоко внутри `initCapture()`. Весь код до этого шага рискует крашнуться
- `CaptureActivity.gyroController`, `vrManager` — lateinit, полагаются на порядок инициализации в `initView()`

**Как надо:** использовать `by lazy`, `Delegates.notNull()` или передавать зависимости через конструктор.

---

## 14. CaptureConst.kt — 307 строк маппинга строк в коде

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/ui/capture/CaptureConst.kt`

~300 строк when-блоков, которые маппят enum'ы SDK на человекочитаемые строки. Всё это должно лежать в `strings.xml` с привязкой к ресурсам по идентификаторам, а не быть захардкожено в Kotlin-коде. При добавлении нового языка нужно переписывать код, а не добавлять перевод.

---

## 15. InstaApp — проблема с `attachBaseContext`

**Файл:** `InstaApp.kt:56-59`

```kotlin
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    instance = this  // instance установлен ДО super.onCreate и ДО инициализации SDK!
}
```

`instance` выставлен до `onCreate()`. Это значит, что любой код, который дёрнет `InstaApp.instance` между `attachBaseContext` и `onCreate`, увидит Application без инициализированных SDK. Потенциальная гонка данных.

---

## 16. ConnectService — Java в Kotlin-проекте

**Файл:** `ConnectService.java`

Весь проект на Kotlin, но foreground service написан на Java. Никакой причины для этого нет — это несогласованность, затрудняющая поддержку.

---

## 17. Lib-модуль пустой

`lib/src/main/java/` и `lib/src/main/kotlin/` — пустые директории. Модуль `:lib` заявлен как «pure JVM library», но в нём нет ни строчки кода. Мёртвый модуль в графе сборки.

---

## 18. Магические числа повсюду

| Значение | Где | Что делает |
|----------|-----|------------|
| `100L` | `BaseActivity.kt:33` | MIN_LOADING_TIME |
| `0.8f` | `BaseActivity.kt:95` | порог свободного места на SD |
| `0.04f` | `GyroOrientationController.kt:51` | yawFactor |
| `0.02f` | `GyroOrientationController.kt:52` | pitchFactor |
| `0.12f` | `GyroOrientationController.kt:47` | smoothingAlpha |
| `1.2f` | `GyroOrientationController.kt:50` | sensivity (default) |
| `50, 10` | `CaptureActivity.kt:110` | vibrate(millis, amplitude) |
| `1300` | `CaptureActivity.kt:64` | fling threshold |
| `180` | `CaptureActivity.kt:65` | item transition time |
| `500ms` | `CaptureShutterButton.kt` | long-touch detection |
| `3.0°` | `VrManager.kt` | IPD yaw offset |
| `30fps` | `VrManager.kt`, `LocalVrManager.kt` | PixelCopy интервал |
| `200ms` | `LocalSphericalPlayerActivity.kt` | detection update interval |
| `60°/45°` | `LocalSphericalPlayerActivity.kt` | FOV по умолчанию |

Все эти значения должны быть именованными константами с документированием их смысла.

---

## 19. Прочие мелочи

- `InstaApp.kt:46` — `RecordResolution.CAPTURE_3840_1920_100FPS` висит в воздухе, нигде не используется (мёртвая строка)
- Комментарии на трёх языках: русский, китайский (`进入拍摄页锁屏`), английский. Должен быть один язык (английский — стандарт индустрии)
- `sensivity` — опечатка, правильно `sensitivity`
- `EventStatus` enum в `BaseEvent.kt` логически относится только к Capture, но лежит в base-пакете
- Тесты покрывают только математику (Quaternion, FOV). Бизнес-логика не протестирована вообще — 0 тестов на ViewModel, Activity, Fragment
- `isFetchingOptions` (CaptureViewModel:41) — булев флаг для защиты от повторного входа, классический признак плохо спроектированного асинхронного кода

---

## Сводка: три главные архитектурные ошибки

| # | Проблема | Последствия |
|---|----------|-------------|
| 1 | **Reflection для ViewBinding/ViewModel** | Хрупко, медленно, ломается при обфускации. Проблема, которой не существует в современном Kotlin |
| 2 | **Отсутствие Dependency Injection** | Всё завязано на глобальные синглтоны (`object`, `companion object`). Тестировать невозможно. Любое изменение в одном месте ломает весь проект |
| 3 | **God Objects повсюду** | ViewModels по 650 строк, Activities по 460 строк, контроллеры по 500 строк. Нарушение SRP, ISP. Любое изменение — риск сломать 5 других фич |

---

## Что делать (порядок приоритета)

1. **Убрать ViewBindingUtils** — заменить на стандартные Kotlin-делегаты
2. **Ввести Hilt/Koin** для DI — убрать глобальные синглтоны
3. **Разделить CaptureViewModel** на 4-5 UseCase/Interactor'ов
4. **Объединить VrManager и LocalVrManager** в один класс
5. **Вынести Quaternion** в отдельный файл, удалить дубликат `UnitQuaternion`
6. **Сделать BaseEvent sealed interface**
7. **Убрать RxJava** — заменить `Disposable` на `Job`
8. **Заменить `lateinit var`** на `by lazy` или конструкторную инъекцию
9. **Вынести строковые маппинги** из `CaptureConst.kt` в `strings.xml`
10. **Добавить шифрование** для WiFi-паролей (EncryptedSharedPreferences)
11. **Исправить `startForeground()`** для Android 14+
12. **Переписать `ConnectService`** на Kotlin
13. **Удалить пустой модуль `:lib`** или наполнить его кодом
