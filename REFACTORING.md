# REFACTORING.md — План рефакторинга Insta360 SDK Demo

## Обзор

| | До | После |
|---|-----|-------|
| Gradle-модули | 1 (app) + пустой lib | 10+ с чистыми границами |
| Размер классов | ViewModel 650 строк, Activity 460 строк | UseCase 30-80 строк |
| DI | Глобальные object-синглтоны | Hilt, любая зависимость заменяется mock'ом |
| Unit-тесты | 2 файла (только математика) | 50+ unit, 5-10 integration |
| R8/ProGuard | Выключен (ViewBindingUtils ломается) | Включён, APK меньше на 20-40% |
| C++/JNI | Нет | `:native` модуль, Eigen, JNI bridge |
| Troubleshooting | println в XLog | Structured logging с UI-просмотром событий |
| Готовность к CV | 0 | Модель данных готова, пайплайн спроектирован |

Ключевой принцип: **каждая фаза завершается компилируемым и полностью работающим приложением**. Никаких «брошенных на полпути» рефакторингов. После каждой фазы можно собрать APK, запустить на устройстве, проверить все функции.

---

## AI Agent Instructions

Эти указания — для ИИ-агентов (Claude, Codex, Copilot), помогающих с рефакторингом. Я (человек) тоже это читаю, поэтому без фанатизма.

### Общие правила для всех фаз

1. **Не трогать Insta360 SDK.** Импорты `com.arashivision.sdkcamera.*` и `com.arashivision.sdkmedia.*` — проприетарный SDK. Можно только оборачивать за интерфейсами, никаких правок SDK-классов.
2. **abiFilter = arm64-v8a только.** Не добавлять x86, x86_64, armeabi-v7a. Камера работает только на arm64.
3. **После каждого шага — компиляция.** `./gradlew assembleDebug` должен проходить. Если не проходит — чини перед следующим шагом.
4. **View `InstaCapturePlayerView` и `SphericalGLSurfaceView`** — это часть SDK/Media3. Не менять их API, только оборачивать.
5. **Комментарии на английском.** В проекте сейчас смесь русского, китайского, английского. Все новые комментарии — английские.
6. **`lateinit` — только когда нет другого выбора.** Предпочитать `by lazy` или nullable + `?: error(...)`.
7. **Magic numbers запрещены.** Каждое число — именованная константа в companion object.

### Фаза 0-1: только структурные изменения

- **НЕ менять логику.** Только формат кода, имена, структуру.
- **НЕ удалять фичи.** Если метод существует — он должен продолжать работать.
- После каждой правки — `assembleDebug` и ручной smoke-тест (открыть все экраны, подключиться к камере если есть).

### Фаза 2: архитектура

- Зависимость feature-модулей: `:feature:* → :domain`, но **НЕ** `:feature:* → :data:*`.
- Репозитории определяются как интерфейсы в `:domain`, реализуются в `:data:*`.
- Hilt-модули — по одному на data-модуль.

### Фаза 3: декомпозиция

- UseCase = один класс, один публичный метод `suspend operator fun invoke(...) -> Result<T>`.
- ViewModel не содержит бизнес-логики, только оркестрацию UseCase'ов и хранение UI-состояния.
- `Quaternion` в `:core:math` — чистая data class, без Android-зависимостей.

### Фаза 4: C++

- C++17, Eigen 3.4.0 (header-only через FetchContent).
- JNI-методы держать в одном файле `jni_bridge.cpp`.
- Kotlin-сторона: `external fun` в отдельном классе `NativeBridge`.
- Feature flag (`BuildConfig.USE_NATIVE_GAZE`) для переключения Kotlin/C++ реализаций.

### Фаза 5: тестирование

- ViewModel тесты: `kotlinx-coroutines-test` + `Turbine` для Flow.
- UseCase тесты: мок-репозитории через MockK.
- C++ тесты: Google Test в `:native/src/test/cpp/`.

---

## Фаза 0: Фундамент (2-3 дня)

**Результат:** приложение собирается и работает идентично текущему, но инструменты качества настроены.

### 0.0 Проверка сборки на старте

```bash
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew clean assembleDebug
```

Убедиться, что приложение собирается **до** начала любых правок.

### 0.1 Убрать хардкод-креды из build.gradle.kts

**Проблема:** `storePassword = "insta360"`, `keyAlias = "insta360"`, `keyPassword = "insta360"` открытым текстом в `app/build.gradle.kts:40-42`.

**Решение:** вынести в `local.properties` (уже в `.gitignore`):

```properties
# local.properties
signing.storePassword=insta360
signing.keyAlias=insta360
signing.keyPassword=insta360
```

В `build.gradle.kts`:
```kotlin
val localProps = java.util.Properties().apply {
    rootProject.file("local.properties").inputStream().use { load(it) }
}
signingConfigs {
    create("release") {
        storeFile = file("G:\\camerasdk\\sdkdemo2\\app\\sdk.jks")
        storePassword = localProps.getProperty("signing.storePassword")
        keyAlias = localProps.getProperty("signing.keyAlias")
        keyPassword = localProps.getProperty("signing.keyPassword")
    }
}
```

### 0.2 Инструменты качества кода

Добавить `ktlint` и `detekt` в корневой `build.gradle.kts`:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}
```

Создать `.editorconfig` с правилами форматирования, `detekt-config.yml` с базовыми правилами.

Цель: `./gradlew ktlintCheck detekt` проходит чисто.

### 0.3 Удаление мёртвого кода

- `BaseFragment.disposable: Disposable?` (строка 26) — удалить поле и RxJava-импорт.
- `META-INF/rxjava.properties` исключение из `build.gradle.kts:28` — удалить.
- `RecordResolution.CAPTURE_3840_1920_100FPS` в `InstaApp.kt:46` — удалить (висящая строка, не используется).
- Пустой модуль `:lib` — удалить из `settings.gradle.kts` или оставить с документированным назначением.

### 0.4 Обновление зависимостей

| Зависимость | Сейчас | Цель | Осторожно |
|--------|--------|------|-----------|
| Media3 | 1.5.1 | 1.6.1+ | Проверить совместимость `SphericalGLSurfaceView` |
| Kotlin | 2.0.21 | 2.1.x | |
| AGP | 8.12.3 | 8.13.x | |
| Gradle | 8.13 | 8.14+ | |
| compileSdk/targetSdk | 35 | 36 | Проверить foreground service API |

Insta360 SDK (`1.8.1_build_06`) — **не обновлять** без подтверждённой совместимости.

### 0.5 Проверка после Фазы 0

```bash
./gradlew clean assembleDebug    # Должен собраться
./gradlew ktlintCheck            # Должен пройти чисто
./gradlew detekt                 # Допустимы warnings, не failures
```

Ручной тест: установить APK на устройство, открыть все экраны.

---

## Фаза 1: Low-Risk Cleanup (4-5 дней)

**Результат:** приложение работает идентично, но код чистый и безопасный. R8 можно включить.

### 1.1 Убрать ViewBindingUtils (приоритет №1)

**Файл:** `app/src/main/java/com/arashivision/sdk/demo/util/ViewBindingUtils.kt`

Сейчас Java Reflection создаёт ViewBinding и ViewModel. Проблемы: медленно, хрупко, ломается при R8/ProGuard.

**Замена в BaseActivity:**
```kotlin
// Было (строка 68-70):
this.binding = ViewBindingUtils.createBinding(javaClass, layoutInflater, 0, null)
setContentView(this.binding.root)
this.viewModel = createViewModel(this, 1)

// Стало (через конструктор):
abstract class BaseActivity<T : ViewBinding, V : BaseViewModel>(
    private val bindingFactory: (LayoutInflater) -> T
) : AppCompatActivity() {
    protected val binding: T by lazy { bindingFactory(layoutInflater) }
    protected val viewModel: V by viewModels()
}

// Конкретный класс:
class CaptureActivity : BaseActivity<ActivityCaptureBinding, CaptureViewModel>(
    bindingFactory = { ActivityCaptureBinding.inflate(it) }
)
```

**Замена в BaseFragment:**
```kotlin
abstract class BaseFragment<T : ViewBinding, V : BaseViewModel>(
    private val bindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> T
) : Fragment() {
    private var _binding: T? = null
    protected val binding: T get() = _binding!!
    protected val viewModel: V by viewModels()
}
```

После замены — **удалить `ViewBindingUtils.kt` полностью**.

**Проверка:** все экраны открываются без креша. Конструкторы с `bindingFactory` не должны вызывать проблем с restore (ViewModel переживает recreate Activity, это нормально).

**Бонус:** включить R8 в release:
```kotlin
isMinifyEnabled = true  // было false
```

### 1.2 Убрать RxJava

- Удалить `import io.reactivex.disposables.Disposable` из `BaseFragment.kt:17`
- Удалить поле `disposable` (строка 26)
- Удалить очистку в `onDestroyView()` (строки 93-95)
- Удалить `META-INF/rxjava.properties` из `build.gradle.kts:28`
- Проверить: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i rxjava` — пусто

### 1.3 ConnectService: Java → Kotlin

Файл: `app/src/main/java/com/arashivision/sdk/demo/service/ConnectService.java` (74 строки)

Тривиальный перенос. Заодно исправить Android 14+ (API 34) foreground service:

```kotlin
class ConnectService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Camera connection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Insta360 Camera")
            .setContentText("Connected to camera")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
```

### 1.4 BaseEvent → sealed interface

**Файл:** `BaseEvent.kt`

```kotlin
// Было:
interface BaseEvent { ... }

// Стало:
sealed interface BaseEvent {
    object CameraBatteryLowEvent : BaseEvent
    data class CameraSDCardStateChangedEvent(val enabled: Boolean) : BaseEvent
    data class CameraBatteryUpdateEvent(val batteryLevel: Int, val isCharging: Boolean) : BaseEvent
    data class CameraStorageChangedEvent(val freeSpace: Long, val totalSpace: Long) : BaseEvent
    data class CameraStatusChangedEvent(val enable: Boolean, val connectType: Int) : BaseEvent
}
```

- Вынести `EventStatus` enum в отдельный файл `EventStatus.kt` в `ui/capture/`.
- Заменить все `when(event) { ... else -> {} }` на exhaustive `when` без else.
- `CameraStatusChangedEvent` переименовать поля: `enable → enabled` (единый стиль).

### 1.5 Магические числа → именованные константы

Создать в каждом классе companion object с константами:

| Файл | Значение | Константа |
|------|----------|-----------|
| BaseActivity.kt:33 | `100L` | `MIN_LOADING_DISPLAY_MS` |
| BaseActivity.kt:95 | `0.8f` | `STORAGE_WARNING_THRESHOLD` |
| GyroOrientationController.kt:47 | `0.12f` | `SLERP_SMOOTHING_ALPHA` |
| GyroOrientationController.kt:50 | `1.2f` | `DEFAULT_SENSITIVITY` |
| GyroOrientationController.kt:51 | `0.04f` | `YAW_SENSITIVITY_FACTOR` |
| GyroOrientationController.kt:52 | `0.02f` | `PITCH_SENSITIVITY_FACTOR` |
| GyroOrientationController.kt:45 | `8L` | `SENSOR_RATE_LIMIT_MS` |
| CaptureActivity.kt:64 | `1300` | `FLING_THRESHOLD` |
| CaptureActivity.kt:65 | `180` | `ITEM_TRANSITION_TIME_MS` |
| CaptureShutterButton.kt | `500L` | `LONG_TOUCH_THRESHOLD_MS` |
| VrManager.kt | `3.0f` | `VR_IPD_YAW_OFFSET_DEG` |
| VrManager.kt / LocalVrManager.kt | `33ms` | `VR_FRAME_COPY_INTERVAL_MS` |
| LocalSphericalPlayerActivity.kt | `200L` | `DETECTION_UPDATE_INTERVAL_MS` |
| LocalSphericalPlayerActivity.kt | `60f`, `45f` | `DEFAULT_HFOV_DEG`, `DEFAULT_VFOV_DEG` |

### 1.6 CaptureConst.kt: строки в ресурсы

`CaptureConst.kt` (307 строк) — функция `getCaptureSettingValueName()` содержит ~250 строк хардкод-строк. Перенести в `strings.xml`:

```xml
<string name="capture_setting_ev_value">%1$s</string>
<string name="capture_setting_exposure_value">1/%1$s</string>
```

В коде остаётся mapping `enum → @StringRes` + форматирование. При добавлении нового языка — только strings.xml.

### 1.7 Исправить launch modes в AndroidManifest.xml

```xml
<!-- Было -->
<activity android:name=".ui.main.MainActivity" android:launchMode="singleInstance" />
<activity android:name=".ui.capture.CaptureActivity" android:launchMode="singleInstance" />

<!-- Стало -->
<activity android:name=".ui.main.MainActivity" android:launchMode="singleTop" />
<activity android:name=".ui.capture.CaptureActivity" android:launchMode="singleTop" />
```

Проверить: навигация Back работает как в обычном Android-приложении.

### 1.8 `lateinit` → безопасные альтернативы

- `BaseActivity.viewModel` → `protected` + `by viewModels()` (после шага 1.1 это решится автоматически)
- `CaptureViewModel.cameraOfflineData` → удалить `lateinit`, сделать nullable:
  ```kotlin
  private var _cameraOfflineData: CameraOfflineData? = null
  val cameraOfflineData: CameraOfflineData
      get() = _cameraOfflineData ?: error("Camera not initialized. Call initCapture() first.")
  ```
- `CaptureActivity.gyroController` / `vrManager` → `by lazy { ... }`

### 1.9 Проверка после Фазы 1

```bash
./gradlew clean assembleDebug
./gradlew ktlintCheck detekt
./gradlew testDebugUnitTest
```

Ручной smoke-тест: все экраны, подключение к камере (если доступна), capture/playback.

---

## Фаза 2: Clean Architecture + DI (7-10 дней)

**Результат:** модульная архитектура, Hilt DI, разделение на слои. Приложение работает как прежде, но код организован по Clean Architecture. Новые фичи добавляются в изолированных модулях.

### 2.1 Архитектурные слои

```
┌─────────────────────────────────────────┐
│  :app                                   │  Application + навигация
├───────────┬───────────┬─────────────────┤
│ :feature  │ :feature  │ :feature         │  UI + ViewModel
│ :capture  │ :player   │ :connect         │  НЕ знают про SDK
│           │           │                  │  Только Android + domain
├───────────┴───────────┴─────────────────┤
│  :domain                                │  Use Cases + Repository interfaces
│  Нулевые зависимости от Android          │  Чистый Kotlin/JVM
│  CameraRepository interface              │
│  MediaRepository interface               │
│  GazeRepository interface                │
├───────────┬───────────┬─────────────────┤
│ :data     │ :data     │ :data            │  Repository implementations
│ :camera   │ :sensor   │ :detection       │  SDK-зависимости ЗДЕСЬ
│           │           │                  │  Единственные места с import sdkcamera
├───────────┼───────────┼─────────────────┤
│ :core     │ :core     │ :core            │  Чистые вычисления
│ :math     │ :sensors  │ :detection       │  → кандидаты на C++
│           │           │                  │  Без Android-зависимостей
└───────────┴───────────┴─────────────────┘
```

### 2.2 Gradle-модули (`settings.gradle.kts`)

```kotlin
rootProject.name = "Insta360SDKDemo"
include(":app")

// Core (zero dependencies)
include(":core:math")          // Quaternion, EquirectangularProjection, PanoramaFovMath
include(":core:sensor-fusion") // Sensor fusion engine
include(":core:detection")     // VideoDetectionModels, Timeline, Parser
include(":core:vr")            // Unified VR manager

// Data (SDK dependencies)
include(":data:camera")        // InstaCameraManager wrapper
include(":data:media")         // Glide, detection sidecar I/O
include(":data:sensor")        // Android SensorManager wrapper

// Domain (no Android, no SDK)
include(":domain")

// Features (UI)
include(":feature:capture")
include(":feature:player")
include(":feature:connect")
include(":feature:shot")
include(":feature:settings")
```

### 2.3 Миграция кода по модулям

| Модуль | Что перенести | Откуда |
|--------|---------------|--------|
| `:core:math` | `Quaternion` (из GyroOrientationController.kt:310-495), `EquirectangularProjection`, `PanoramaFovMath` | `ui/player/panorama/`, `ui/capture/` |
| `:core:sensor-fusion` | `SensorFusionEngine` (новый класс — логика из `GyroOrientationController.onSensorChanged()`) | Из `GyroOrientationController` |
| `:core:detection` | `VideoDetectionModels`, `VideoDetectionSidecarParser`, `VideoDetectionTimeline` | `ui/player/detection/` |
| `:core:vr` | Unified `VrManager` (объединить `VrManager` + `LocalVrManager`) | `ui/capture/VrManager.kt`, `ui/player/LocalVrManager.kt` |
| `:data:camera` | `CameraOfflineData`, `InstaCameraManagerExt`, SDK-обёртки | `capture/CameraOfflineData.kt`, `ext/InstaCameraManagerExt.kt` |
| `:data:sensor` | `GyroOrientationController` (только Android-прослойка над `SensorFusionEngine`) | `ui/capture/GyroOrientationController.kt` |
| `:domain` | Интерфейсы репозиториев + UseCases | Новые файлы |
| `:feature:capture` | `CaptureActivity`, `CaptureViewModel`, `CaptureEvent`, `CaptureConst` (урезанный) | `ui/capture/` |
| `:feature:player` | `LocalSphericalPlayerActivity`, `LocalSphericalPlayerViewModel`, `DirectionArrowOverlayView` | `ui/player/` |
| `:feature:connect` | `ConnectFragment`, `ConnectViewModel`, `ConnectEvent`, `BleDeviceAdapter` | `ui/connect/` |
| `:feature:shot` | `ShotActivity`, `ShotViewModel`, `ShotEvent` | `ui/shot/` |
| `:feature:settings` | `SettingFragment`, `SettingViewModel`, `SettingEvent` | `ui/setting/` |

### 2.4 Dependency Injection (Hilt)

Добавить Hilt в корневой `build.gradle.kts`:

```kotlin
plugins {
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
```

**Hilt-модули:**

```kotlin
// :data/camera — предоставляет SDK-зависимости
@Module
@InstallIn(SingletonComponent::class)
object CameraDataModule {
    @Provides @Singleton
    fun provideInstaCameraManager(): InstaCameraManager = instaCameraManager

    @Provides @Singleton
    fun provideCameraRepository(manager: InstaCameraManager): CameraRepository =
        Insta360CameraRepository(manager)
}

// :data/sensor
@Module
@InstallIn(SingletonComponent::class)
object SensorDataModule {
    @Provides @Singleton
    fun provideSensorFusionEngine(): SensorFusionEngineInterface =
        KotlinSensorFusionEngine()  // или NativeSensorFusionEngine при флаге
}

// :domain — UseCases
@Module
@InstallIn(ViewModelComponent::class)
object DomainModule {
    @Provides
    fun provideInitializeCameraUseCase(repo: CameraRepository): InitializeCameraUseCase =
        InitializeCameraUseCase(repo)
    // ... остальные UseCases
}
```

**ViewModel с инъекцией:**
```kotlin
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val initializeCamera: InitializeCameraUseCase,
    private val switchCaptureMode: SwitchCaptureModeUseCase,
    private val captureControl: CaptureControlUseCase,
    private val liveStream: LiveStreamUseCase,
    private val previewStream: PreviewStreamUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    fun onAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.InitCapture -> initializeCamera(action)
            is CaptureAction.SwitchMode -> switchMode(action.position)
            is CaptureAction.StartCapture -> startCapture()
        }
    }
}
```

### 2.5 Domain-слой: Use Cases

Извлечь бизнес-логику из `CaptureViewModel` (650 строк) в отдельные UseCase:

| UseCase | Ответственность | ~Строк |
|---------|----------------|--------|
| `InitializeCameraUseCase` | 5-шаговая инициализация (check sensor → fetch options → init config → create offline data → open stream) | 80 |
| `SwitchCaptureModeUseCase` | Переключение режима + валидация позиции | 30 |
| `CaptureControlUseCase` | `startCapture()` → диспатч на `takePhotos()` / `startRecord()` / `startLive()` / `stopRecord()` | 50 |
| `LiveStreamUseCase` | RTMP-валидация, `startLive()` / `stopLive()`, ILiveStatusListener обёртка | 60 |
| `UpdatePreviewParamsUseCase` | WindowCrop, offset, resolution — проверка изменений и создание объектов | 60 |
| `HandleCaptureCompletionUseCase` | Пост-обработка после съёмки: переоткрытие стрима для старых flow, H265 workaround | 40 |
| `PreviewStreamUseCase` | `openPreviewStream()`, `reopenPreviewStream()`, listener → coroutine bridge | 40 |

Каждый UseCase = один класс с методом `suspend operator fun invoke(...) -> Result<T>`.

**Преимущество troubleshooting:** каждый UseCase логирует вход/выход/ошибку. В structured logging видно: `InitializeCameraUseCase → checkSensor FAILED` — понятно, где проблема.

### 2.6 UDF (Unidirectional Data Flow)

```
UI (Activity/Fragment)
  ↓ dispatch(action)
ViewModel
  ↓ invoke(useCase)
UseCase
  ↓ call(repository)
Repository
  ↓ call(SDK)
Insta360 SDK

Обратно:
SDK callback → Repository → UseCase.Result → ViewModel.state.update → UI recompose
```

**Состояние экрана:**
```kotlin
data class CaptureUiState(
    val isInitializing: Boolean = false,
    val initStep: InitStep? = null,
    val currentCaptureMode: CaptureMode? = null,
    val captureModeList: List<CaptureMode> = emptyList(),
    val isRecording: Boolean = false,
    val isLiveStreaming: Boolean = false,
    val recordTimeMs: Long = 0L,
    val errorMessage: String? = null
)
```

### 2.7 Проверка после Фазы 2

```bash
./gradlew clean assembleDebug    # Должен собраться с новой модульной структурой
./gradlew testDebugUnitTest      # Все unit-тесты (старые + новые)
```

Ручной тест: **ВСЕ** функции приложения — connect, capture, playback, VR, settings, shot. Всё должно работать как прежде.

---

## Фаза 3: God Object Decomposition (10-14 дней)

**Результат:** все классы ≤ 400 строк. Убрано дублирование VrManager. Quaternion в отдельном модуле. Приложение работает.

### 3.1 GyroOrientationController → разделение

**Шаг 1: Quaternion в `:core:math`**

Вынести `Quaternion` data class (строки 310-495 `GyroOrientationController.kt`) в `:core:math` как `Quaternion.kt`.

Одновременно удалить дубликат `UnitQuaternion` из `EquirectangularProjection.kt` — заменить все использования на `Quaternion` из `:core:math`.

Интерфейс Quaternion:
```kotlin
data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {
    fun magnitude(): Float
    fun normalize(): Quaternion
    fun conjugate(): Quaternion
    fun multiply(other: Quaternion): Quaternion
    fun dot(other: Quaternion): Float
    fun toEulerAngles(previousYaw: Float?, previousPitch: Float?, previousRoll: Float?): Triple<Float, Float, Float>

    companion object {
        fun fromRotationMatrix(mat: FloatArray): Quaternion  // Shepperd
        fun slerp(q1: Quaternion, q2: Quaternion, t: Float): Quaternion
        operator fun Quaternion.minus/plus/times  // операторы
    }
}
```

**Шаг 2: SensorFusionEngine в `:core:sensor-fusion`**

Создать чистый Kotlin-класс **без Android-зависимостей**:

```kotlin
class SensorFusionEngine(
    private val smoothingAlpha: Float = SLERP_SMOOTHING_ALPHA,
    private val yawSensitivity: Float = YAW_FACTOR * DEFAULT_SENSITIVITY,
    private val pitchSensitivity: Float = PITCH_FACTOR * DEFAULT_SENSITIVITY,
    private val invertYaw: Boolean = false,
    private val invertPitch: Boolean = true
) {
    private val _gazeState = MutableStateFlow(GazeState())
    val gazeState: StateFlow<GazeState> = _gazeState.asStateFlow()

    fun update(rotationMatrix: FloatArray, displayRotation: Int): GazeState
    fun calibrate()
    fun setSensitivity(value: Float)
}

data class GazeState(
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val rawYawDeg: Float = 0f,
    val rawPitchDeg: Float = 0f,
    val isCalibrated: Boolean = false
)
```

Логика remap CoordinateSystem и расчёта Euler — внутри `SensorFusionEngine`. Тестируется на JVM без Android.

**Шаг 3: GyroOrientationController — тонкая Android-прослойка**

```kotlin
class GyroOrientationController(
    context: Context,
    private val getDisplayRotation: () -> Int,
    private val engine: SensorFusionEngine = SensorFusionEngine()
) : SensorEventListener {
    private val sensorManager: SensorManager = ...
    private val rotationVectorSensor: Sensor? = ...

    fun start() { sensorManager.registerListener(...) }
    fun stop() { sensorManager.unregisterListener(...) }
    fun calibrate() { engine.calibrate() }

    override fun onSensorChanged(event: SensorEvent) {
        val rotMat = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotMat, event.values)
        engine.update(rotMat, getDisplayRotation())
    }

    // Делегирование свойств engine
    val gazeState: StateFlow<GazeState> get() = engine.gazeState
}
```

### 3.2 VrManager + LocalVrManager → Unified VR Engine

**Абстракция источника кадра:**
```kotlin
// :core:vr/VrSourceView.kt
interface VrSourceView {
    fun captureFrame(): Bitmap?
    fun applyOrientation(yawDeg: Float, pitchDeg: Float)
    fun onVrModeChanged(enabled: Boolean)
}
```

**Две реализации:**
```kotlin
// :feature:capture/CapturePlayerVrSource.kt
class CapturePlayerVrSource(
    private val playerView: InstaCapturePlayerView
) : VrSourceView { ... }

// :feature:player/ExoPlayerVrSource.kt
class ExoPlayerVrSource(
    private val sphericalView: View
) : VrSourceView { ... }
```

**Единый VR Manager:**
```kotlin
// :core:vr/UnifiedVrManager.kt
class UnifiedVrManager(
    private val context: Context,
    private val rootContainer: ViewGroup,
    private val vrSource: VrSourceView,
    private val calibrateGyro: () -> Unit,
    private val getGyroYaw: () -> Float,
    private val getGyroPitch: () -> Float
) {
    val isVrMode: Boolean
    fun enable()
    fun disable()
    fun showSettingsDialog()
    fun applyOrientation(yawDeg: Float, pitchDeg: Float)
    fun destroy()
}
```

Один класс ~400 строк вместо двух на 950. PixelCopy-луп, VR settings dialog, applyOrientation — общий код.

### 3.3 CaptureActivity: экстракция UI-компонентов

Вынести в кастомные View:

```kotlin
// :feature:capture/CaptureModeCarousel.kt
class CaptureModeCarousel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : DiscreteScrollView(context, attrs) {
    // Инкапсулирует CaptureModeAdapter + ScaleTransformer + FadingEdgeDecoration + fling/slide настройки
    fun setModes(modes: List<String>, currentIndex: Int)
}

// :feature:capture/CaptureSettingsSheet.kt
class CaptureSettingsSheet(...) {
    // Инкапсулирует PickerView + логику показа/скрытия + VR settings
    fun show(settings: List<PickData>)
    fun hide()
}

// :feature:capture/CapturePreviewController.kt
class CapturePreviewController(
    private val playerView: InstaCapturePlayerView,
    private val viewModel: CaptureViewModel
) {
    fun displayPreviewStream(lifecycle: Lifecycle)
    fun replay()
    fun destroy()
}
```

После экстракции `CaptureActivity` ~150 строк (только оркестрация).

### 3.4 InstaApp → тонкий entry point

```kotlin
@HiltAndroidApp
class InstaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Только инициализация, которую нельзя сделать в Hilt-модулях
        // (например, то, что требует Context до Hilt)
    }
}
```

`UsbMgr.init()`, `InstaCameraSDK.init()`, `InstaMediaSDK.init()`, `XLogUtils.init()`, `LogManager`, `NetworkManager` — уходят в `@Singleton @Provides` Hilt-модули.

### 3.5 Проверка после Фазы 3

```bash
./gradlew clean assembleDebug    # Все модули собираются
./gradlew testDebugUnitTest      # Тесты :core:math, :core:sensor-fusion, :domain
```

Ручной тест: все функции, включая VR (capture и player) — должны работать идентично.

---

## Фаза 4: C++ / JNI Foundation (14-21 дней)

**Результат:** `:native` модуль с CMake и Eigen. Quaternion + Euler работают на C++. Feature flag для переключения реализаций. Бенчмарки подтверждают производительность.

### 4.1 Модуль `:native`

```
:native/
├── build.gradle.kts
├── CMakeLists.txt
├── src/main/cpp/
│   ├── jni_bridge.cpp          // JNI entry point
│   ├── core/
│   │   ├── quaternion.h/cpp    // Eigen::Quaternionf
│   │   ├── euler.h/cpp         // Euler angle extraction
│   │   └── projection.h/cpp    // Equirectangular projection
│   ├── sensors/
│   │   └── sensor_fusion.h/cpp // SLERP + remap
│   └── cv/                     // Будущий CV-пайплайн
│       └── README.md           // Документация для CV-интеграции
├── src/test/cpp/
│   ├── CMakeLists.txt
│   ├── quaternion_test.cpp
│   └── projection_test.cpp
└── src/main/java/
    └── com/arashivision/sdk/demo/native/
        ├── NativeBridge.kt     // external fun declarations
        └── NativeSensorFusionEngine.kt  // JNI wrapper
```

### 4.2 `build.gradle.kts` для :native

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arashivision.sdk.demo.native"
    compileSdk = 35
    ndkVersion = "25.2.9519653"
    defaultConfig {
        minSdk = 29
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O2")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
}
```

### 4.3 CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("insta360_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Eigen (header-only)
include(FetchContent)
FetchContent_Declare(eigen
    GIT_REPOSITORY https://gitlab.com/libeigen/eigen.git
    GIT_TAG 3.4.0
)
FetchContent_MakeAvailable(eigen)

add_library(${CMAKE_PROJECT_NAME} SHARED
    jni_bridge.cpp
    core/quaternion.cpp
    core/euler.cpp
    core/projection.cpp
    sensors/sensor_fusion.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(${CMAKE_PROJECT_NAME} Eigen3::Eigen android log)
```

### 4.4 JNI-мост с feature flag

```kotlin
// :native/NativeBridge.kt — только external fun
object NativeBridge {
    init { System.loadLibrary("insta360_native") }

    external fun nativeQuaternionFromRotationMatrix(mat: FloatArray): FloatArray  // returns [w,x,y,z]
    external fun nativeQuaternionSlerp(q1: FloatArray, q2: FloatArray, t: Float): FloatArray
    external fun nativeQuaternionToEuler(q: FloatArray, prevYaw: Float, prevPitch: Float, prevRoll: Float): FloatArray
    external fun nativeEulerFromForwardVector(remappedMat: FloatArray, displayRotation: Int): FloatArray
}

// :data/sensor/NativeSensorFusionEngine.kt
class NativeSensorFusionEngine : SensorFusionEngineInterface {
    private val nativePtr: Long = nativeCreate()
    override fun update(rotationVector: FloatArray, displayRotation: Int): GazeState
    override fun calibrate() = nativeCalibrate(nativePtr)
    override fun release() = nativeRelease(nativePtr)

    private external fun nativeCreate(): Long
    private external fun nativeUpdate(ptr: Long, rot: FloatArray, displayRot: Int): GazeState
    private external fun nativeCalibrate(ptr: Long)
    private external fun nativeRelease(ptr: Long)

    protected fun finalize() { release() }
}

// Feature flag в Hilt-модуле
@Provides @Singleton
fun provideSensorFusionEngine(): SensorFusionEngineInterface {
    return if (BuildConfig.USE_NATIVE_GAZE) NativeSensorFusionEngine()
           else KotlinSensorFusionEngine()
}
```

Feature flag позволяет:
- Тестировать обе реализации side-by-side
- Бенчмаркать производительность
- Быстро откатиться на Kotlin при баге в C++

### 4.5 Бенчмарки (AndroidX Benchmark)

Добавить модуль `:benchmark`:

| Бенчмарк | Описание | Целевая метрика |
|----------|----------|-----------------|
| Quaternion SLERP ×1000 | Сравнение Kotlin vs C++ | C++ в 3-10× быстрее |
| Euler extraction ×1000 | —//— | C++ в 3-10× быстрее |
| Sensor fusion 1 кадр | Полный пайплайн от rotation vector до GazeState | < 1ms (бюджет сенсора ~8ms) |
| JSON detection parsing | Парсинг файла на 1000 кадров | C++ с simdjson в 5-20× быстрее |

### 4.6 C++ Unit-тесты (Google Test)

```cpp
// src/test/cpp/quaternion_test.cpp
#include <gtest/gtest.h>
#include "core/quaternion.h"

TEST(QuaternionTest, FromRotationMatrix_Identity_ReturnsIdentity) {
    float mat[9] = {1,0,0, 0,1,0, 0,0,1};
    Quaternion q = Quaternion::fromRotationMatrix(mat);
    EXPECT_NEAR(q.w, 1.0f, 1e-6f);
    EXPECT_NEAR(q.x, 0.0f, 1e-6f);
}

TEST(QuaternionTest, Slerp_Midpoint_ReturnsAverage) {
    // q1 = identity, q2 = 90° around Z
    // slerp(q1, q2, 0.5) → 45° around Z
}
```

### 4.7 План интеграции Computer Vision (будущее)

Пайплайн (все на C++):
```
Камера (SDK) → Surface → GPU texture
                            ↓
                C++ CV Engine:
                1. FrameGrabber     — захват кадра из текстуры
                2. ImagePreprocessor — resize + normalize под модель
                3. TFLiteEngine     — инференс .tflite модели
                4. NMSFilter        — Non-Maximum Suppression
                5. ObjectTracker    — Kalman filter трекинг
                            ↓
                JNI → Kotlin:
                - VideoDetectionTimeline (уже есть в :core:detection)
                - DirectionArrowOverlayView (уже есть)
                - EquirectangularProjection (уже есть в :core:math)
```

**Что уже готово:**
- `VideoDetectionModels` — модель данных детекций (переиспользовать)
- `VideoDetectionSidecarParser` — парсер JSON (можно переписать на C++ с simdjson)
- `VideoDetectionTimeline` — поиск по времени (binary search)
- `DirectionArrowOverlayView` — отрисовка стрелок
- `PanoramaFovMath.resolveTargetQuat()` — проверка FOV
- `EquirectangularProjection` — конвертация координат

### 4.8 Проверка после Фазы 4

```bash
./gradlew clean assembleDebug            # :native собирается
./gradlew :native:connectedCheck         # Google Test на устройстве
./gradlew :benchmark:connectedCheck      # Бенчмарки
```

Ручной тест: переключение feature flag (USE_NATIVE_GAZE=true/false), поведение гироскопа не меняется.

---

## Фаза 5: Тестирование и производительность (5-7 дней)

**Результат:** покрытие тестами, структурированное логирование, профилирование. Приложение готово к production.

### 5.1 Пирамида тестов

```
         ╱ ╲
        ╱ E2E╲             2-3 теста: connect → capture → playback
       ╱──────╲
      ╱        ╲
     ╱Integration╲         5-10 тестов: SDK repository, ViewModel
    ╱──────────────╲
   ╱                ╲
  ╱   Unit Tests      ╲    50+ тестов
 ╱──────────────────────╲
```

### 5.2 Unit-тесты

| Модуль | Что | Инструмент |
|--------|-----|------------|
| `:core:math` | `Quaternion.*`, `EquirectangularProjection.*`, `PanoramaFovMath.*` | JUnit 4 |
| `:core:sensor-fusion` | `SensorFusionEngine` — remap для всех 4 поворотов, Euler continuity, SLERP convergence, calibration offset | JUnit 4 |
| `:core:detection` | `VideoDetectionSidecarParser` — все JSON-форматы, `VideoDetectionTimeline` — binary search edge cases, empty timeline, single frame | JUnit 4 |
| `:domain` | Все UseCases с MockK-репозиториями | JUnit 4 + MockK |
| `:feature:*` | ViewModel с fake-репозиториями | JUnit 4 + Turbine |
| `:native` | C++ Quaternion, Projection, Euler | Google Test |

### 5.3 Интеграционные тесты

- `CameraRepository` с реальным SDK (нужна камера) — smoke test без креша
- ViewModel с fake-репозиторием — проверка переходов состояний

### 5.4 Structured Logging

```kotlin
// :core/logging/EventLogger.kt
data class CameraEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val event: String,
    val data: Map<String, String> = emptyMap()
)

object EventLogger {
    private val _events = MutableSharedFlow<CameraEvent>(
        replay = 0,
        extraBufferCapacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    fun log(source: String, event: String, vararg data: Pair<String, String>) {
        _events.tryEmit(CameraEvent(source = source, event = event, data = data.toMap()))
    }
}
```

**Использование в UseCase'ах:**
```kotlin
class InitializeCameraUseCase @Inject constructor(...) {
    suspend operator fun invoke(): Result<Unit> {
        EventLogger.log(TAG, "init_start")
        val sensorOk = checkSensor()
        if (!sensorOk) {
            EventLogger.log(TAG, "check_sensor_failed")
            return Result.failure(...)
        }
        EventLogger.log(TAG, "init_success")
        return Result.success(Unit)
    }
}
```

**Просмотр логов:**
- Debug UI: фрагмент с RecyclerView + SearchView — фильтр по source/event в реальном времени
- Adb: `adb shell am broadcast -a com.arashivision.sdk.demo.DUMP_EVENTS` → выгружает последние 1000 событий
- Файл: опционально писать в XLog для post-mortem анализа

### 5.5 Профилирование

| Инструмент | Что меряем |
|------------|------------|
| Macrobenchmark (AndroidX) | Холодный старт, время подключения к камере, задержка первого кадра preview |
| JankStats (AndroidX) | Дропнутые кадры в VR-режиме (PixelCopy), при скролле режимов |
| Memory Profiler (Android Studio) | Утечки при переключении экранов, переподключении камеры, входе/выходе из VR |
| CPU Profiler | Горячие точки в `onSensorChanged()`, `cameraPreviewStreamParamsChanged()` |

### 5.6 Проверка после Фазы 5 (финальная)

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest          # Все тесты зелёные
./gradlew ktlintCheck detekt         # Чисто
./gradlew :benchmark:connectedCheck  # Бенчмарки проходят
```

Ручной тест: полный цикл использования приложения с камерой.

---

## Дорожная карта

```
Неделя 1-2:   ████████░░░░░░░░░░░░ Фаза 0 + 1
              Фундамент + Low-risk cleanup
              Приложение работает, код чистый

Неделя 3-6:   ░░░░░░░░████████████░░ Фаза 2
              Clean Architecture + DI
              Приложение работает, модульная структура

Неделя 7-11:  ░░░░░░░░░░░░░░░░██████ Фаза 3
              God Object decomposition
              Приложение работает, классы компактные

Неделя 12-16: ░░░░░░░░░░░░░░░░░░░░░ Фаза 4
              C++/JNI foundation
              Приложение работает, есть нативный слой

Неделя 17-18: ░░░░░░░░░░░░░░░░░░░░░ Фаза 5
              Тестирование + профилирование
              Приложение работает, production-ready
```

**Всего: ~18 недель** с C++, **~14 недель** без C++.

---

## Что НЕ менять

| Компонент | Причина |
|-----------|---------|
| Insta360 SDK (`sdkcamera`, `sdkmedia`) | Проприетарный — только обёртки за интерфейсами |
| SDK View (`InstaCapturePlayerView`, `SphericalGLSurfaceView`) | Часть SDK/Media3, API не трогать |
| `libs/glide_transformations.jar` | Внешняя библиотека без исходников — изолировать |
| discrete scrollview (Java) | Vendor UI-компонент — изолировать, не переписывать на Kotlin |
| `abiFilters = arm64-v8a` | Ограничение SDK камеры |
| `namespace = "com.arashivision.sdk.demo"` | Идентификатор приложения — не менять |
