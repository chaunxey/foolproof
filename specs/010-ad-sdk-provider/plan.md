# Implementation Plan: 可切换广告 SDK（新增穿山甲模块）

**Feature**: `specs/010-ad-sdk-provider/spec.md`
**Created**: 2026-10-22
**Constitution**: v1.18（§2 技术栈、§3 模块边界、§11 权限与广告）→ 本次需升 **v1.19**
**Status**: 已实施（Phase 0 / Phase 1 完成，待设备验收与合并）

## 1. 技术上下文

| 项 | 值 |
|---|---|
| 语言 / 构建 | Kotlin 2.4.21、AGP 9.4.1、Gradle 9.8.2；compileSdk/targetSdk 37，minSdk 24，Java 11 |
| UI | Jetpack Compose（BOM 2026.09.00）；广告 View 由 SDK 提供，Compose 侧只做 `AndroidView` 包装 |
| DI | 不新增 Koin 绑定：广告沿用进程级 `AdGateProvider` / 新 `AdSdkRegistry`，启动时在 `:app` 注入 |
| 现有广告 | Meta Audience Network 6.22.0（`:app` 直接依赖） |
| 新增广告 | 穿山甲（Pangle / CSJ）**7.8.1.5**（2026-09-24，minSdk 24），**仅 `:ads:pangle` 依赖** |
| 切换 | Gradle 属性 `foolproof.adSdk=meta|pangle`（默认 `meta`） |
| 构建产物 | 每个构建只含一个广告 SDK；AAB/APK 签名与版本号流程不变（不引入 flavor） |

## 2. 模块与依赖

```
:app
 ├── :base / :common / :features:*
 ├── :ads:api                    ← 新增：广告抽象（无具体 SDK）
 └── 二选一（构建开关）
      ├── :ads:meta             ← 新增：Meta Audience Network 实现
      └── :ads:pangle           ← 新增：穿山甲实现
```

依赖方向（拟写入宪法 §3）：

```
app → base, common, features/*, ads:api, 选中的一个 ads provider
feature → base, common          （不变）
ads → base                      （ads:api 用 FeatureId；provider 实现 ads:api）
common → base                   （不变）
base → 无业务依赖               （不变）
```

- `:ads:api` 用 `:base` 的 `AdGate` / `FeatureId`，不依赖 `:common` / feature；
- `:ads:meta`、`:ads:pangle` 只依赖 `:ads:api`（及各自的 SDK）；
- `:app` 永远只 `implementation` 一个 provider，另一个模块虽在 `settings.gradle.kts` 里（可单独编译/单测），但**不进产物**。

## 3. 设计决策

### D1. 为什么拆成 `:ads:api` / `:ads:meta` / `:ads:pangle`

- 现有 `MetaAdGate` / `BannerAd` 里混着 FAN 类型，无法在不引入 FAN 的情况下打包穿山甲；
- Meta 实现必须离开 `:app`，否则切到 `pangle` 时 `:app` 仍卸载 FAN 依赖会编译失败；
- 有独立抽象后，第三个 SDK（如 GroMore）将来只需再加一个 `:ads:xxx` 模块 + 一个工厂文件；
- feature 仍只认 `:base` 的 `AdGate`，符合宪法 §3 与 §12（feature 不 import 广告 SDK）。

### D2. 切换机制：Gradle 属性 + provider 专属源集（推荐）

`gradle.properties`：

```properties
# meta（海外 / 默认）| pangle（国内）
foolproof.adSdk=meta
```

`app/build.gradle.kts`：

```kotlin
val adSdk = providers.gradleProperty("foolproof.adSdk").getOrElse("meta").lowercase()
check(adSdk in setOf("meta", "pangle")) {
    "foolproof.adSdk 只能是 meta 或 pangle，当前：$adSdk"
}

android {
    sourceSets.getByName("main").kotlin.srcDir("src/ad/$adSdk/kotlin")
}

dependencies {
    implementation(project(":ads:api"))
    when (adSdk) {
        "pangle" -> implementation(project(":ads:pangle"))
        else -> implementation(project(":ads:meta"))
    }
}
```

`app/src/ad/meta/kotlin/com/foolproof/ads/AdSdkFactory.kt` 与
`app/src/ad/pangle/.../AdSdkFactory.kt` 各提供同名函数：

```kotlin
fun createAdSdk(usage: AdUsage): AdSdk = MetaAdSdk(usage)   // 或 PangleAdSdk(usage)
```

`:app` 主源集只有 `FoolproofApp` 一处调用 `createAdSdk(...)`，不引用任何具体 SDK 类型，
因此两个 provider 的依赖可以互斥。

**对比 product flavor**：

| | 属性 + 源集（采用） | product flavor |
|---|---|---|
| 命令 | `:app:assembleDebug` 不变 | 变为 `assembleMetaDebug` / `assemblePangleDebug` |
| 变体数量 | 1 套 | 2 倍（编译/测试任务翻倍） |
| 发布脚本 | 不变 | release/AAB 命令全部要改 |
| 切换体验 | 改一行 + sync | Android Studio Build Variants 下拉 |
| 适用范围 | 一次只发一个渠道包 | 需要同时出国内/海外包 |

当前只有一条发布链路、单人开发，选属性方案；将来要同时出两个包，可在其上加 flavor（抽象层不用改）。

### D3. `:ads:api` 契约

```kotlin
enum class AdProviderId { META, PANGLE }

data class AdGateConfig(
    val usesBeforeAd: Int = 5,
    val timeoutMs: Long = 5_000L,
)

interface AdUsage {                       // :app 的 UsageStore 实现（DataStore）
    fun uses(feature: FeatureId): Int
    fun increment(feature: FeatureId)
    fun reset(feature: FeatureId)
}

interface AdBannerSlot {
    val view: View
    fun refresh()
    fun destroy()
}

interface SplashAdSlot {                   // 原生开屏；Meta 不提供
    val view: View
    fun setOnCloseListener(listener: () -> Unit)
    fun destroy()
}

interface AdSdk : AdGate {                 // 直接实现 :base 的 AdGate，feature 侧零改动
    val id: AdProviderId
    val configured: Boolean                // appId + banner + interstitial 都非空
    fun init(app: Application, debug: Boolean)
    fun createBanner(context: Context, onLoaded: (Boolean) -> Unit): AdBannerSlot?
    fun loadSplash(context: Context, onReady: (SplashAdSlot) -> Unit, onUnavailable: () -> Unit)
    override fun maybeGate(activity: Activity, feature: FeatureId, onAllow: () -> Unit)
    override fun onSuccess(feature: FeatureId)
}

object AdSdkRegistry {
    @Volatile var sdk: AdSdk? = null
}
```

- `:ads:api` **不依赖 Compose**（横幅/开屏返回 `View`），Compose 包装仍在 `:app`；
- `AdSdk` 继承 `:base` 的 `AdGate`：`:app` 直接把实例赋给 `AdGateProvider.gate`，
  5 个 feature 的门控调用完全不用改；门控逻辑委托给 D4 的 `CountingAdGate`；
- `AdBannerSlot?` 返回 `null` = 未配置 → `:app` 显示占位；
- `createBanner` 的 `onLoaded(false)` 也显示占位（加载失败不塌陷布局）；
- `loadSplash`：Meta 立即 `onUnavailable()`（走自绘壳）；穿山甲加载成功回调 `onReady`，
  失败/超时/未配置回调 `onUnavailable`，`:app` 负责 2 秒看门狗（见 D7）。

### D4. 门控策略下沉到 `:ads:api`（可 JVM 单测）

把「5 次 / 失败放行 / 关闭清零 / 超时兜底」从 `MetaAdGate` 抽成共享实现，
两个 provider 只提供“怎么加载和展示一个全屏广告”：

```kotlin
interface FullscreenAd { fun show(onClosed: () -> Unit); fun destroy() }
interface FullscreenAdLoader {
    val isConfigured: Boolean
    fun load(onLoaded: (FullscreenAd) -> Unit, onFailed: () -> Unit)
}
interface DelayScheduler { fun postDelayed(delayMs: Long, block: () -> Unit); fun cancel() }

class CountingAdGate(
    private val usage: AdUsage,
    private val config: AdGateConfig = AdGateConfig(),
    private val schedulerFactory: () -> DelayScheduler,   // 默认 Handler 实现；测试注入 Fake
) {
    fun maybeGate(feature: FeatureId, onAllow: () -> Unit, loader: FullscreenAdLoader)
    fun onSuccess(feature: FeatureId) = usage.increment(feature)
}
```

`loader` 在调用点按当前 Activity 创建，`CountingAdGate` 本身不依赖 Android，可直接 JVM 单测。

规则与现状一致，只多一条**超时兜底**（spec FR-014）：

1. `uses < 5` → 放行；
2. 未配置 → 清零 + 放行；
3. 否则加载：成功 → 展示 → 关闭/报错 → 清零 + 放行；
4. 5 秒内既未展示也未失败 → 清零 + 放行；
5. `onAllow` 用 `AtomicBoolean` 保证只回调一次，迟到的 SDK 回调丢弃。

> 这是相对 v1.1.0 唯一的行为增强（Meta 也生效），属于“不卡死功能”的修复，plan review 确认。

### D5. Meta 迁移：只搬家，不改逻辑

- `MetaAdGate` → `:ads:meta` 的 `MetaFullscreenLoader` + `CountingAdGate`；
- `BannerAd` 里的 FAN `AdView` → `MetaBannerSlot`（同样 320×50、60 秒刷新、未加载显示占位）；
- 初始化（`AdSettings.setDebugBuild(debug)` + `AudienceNetworkAds.initialize`）→ `MetaAdSdk.init`；
- `AdsConfig` → `MetaAdsConfig`（三个 placement ID + `configured`）；
- `FeatureId` 计数 key 不变（`uses_<FEATURE>`），切 SDK 不丢计数（spec FR-015）。

### D6. 穿山甲实现映射（以 7.8.1.5 实际 API 为准，实现时对照官方 demo）

| 能力 | Meta（现状） | 穿山甲 |
|---|---|---|
| 初始化 | `AudienceNetworkAds.initialize` + `AdSettings.setDebugBuild` | `TTAdSdk.init(context, TTAdConfig.Builder().appId(..).appName(..).debug(debug).build())` + `TTAdSdk.start(callback)`（6.x 起必须 start；58xx 起 `init` 返回 boolean、`isSdkReady` 可查） |
| 广告入口 | `AdView` / `InterstitialAd` | `TTAdSdk.getAdManager().createAdNative(context)` |
| 横幅 | `AdView(320×50)`，60 秒 `loadAd()` | `loadBannerExpressAd(AdSlot)` → `TTNativeExpressAd.render()` 得到 `View`；刷新 = `destroy()` 后重新 load；`setExpressViewAcceptedSize(320f, 50f)` |
| 门控全屏 | `InterstitialAd` | `loadFullScreenVideoAd(AdSlot)` → `TTFullScreenVideoAd`，`setFullScreenVideoAdInteractionListener` 后 `showFullScreenVideoAd(activity)`；`onAdClose`/`onError` → 放行 |
| 开屏 | 自绘壳 + 横幅 | `loadSplashAd(AdSlot, SplashAdListener)` → `TTSplashAd.getSplashView()` + `setSplashInteractionListener`（`onAdSkip`/`onAdTimeOver` → 关闭）；失败/超时回退自绘壳（D7） |
| 测试模式 | `AdSettings.setDebugBuild(true)` | `TTAdConfig.Builder().debug(true)` + 后台测试代码位 |
| 销毁 | `destroy()` | `destroy()`（横幅换新前必须销毁旧对象） |

补充：

- 7.3.0.8 起 Manifest 不再需要 `TTMultiProvider`，不手工添加；
- 初始化成功回调里再发首个广告请求；初始化失败只记日志并降级（不抛给业务）；
- 可选：`TTAdSdk.getAdManager().requestPermissionIfNecessary(activity)`，
  如 7.8.1.5 仍要求则在 `MainActivity.onResume` 调一次（由 `AdSdk` 暴露 `onActivityAvailable`，
  不满足则跳过，不阻塞）。

### D7. 启动/唤醒广告层：穿山甲原生开屏 + 回退壳（已拍板）

`SplashScheduler` 的触发时机不改（冷启动 / 后台 > 1 小时），只改“展示什么”：

```
SplashAdOverlay(onFinished)
├── 初始 Loading（黑底，与原开屏观感一致）
├── sdk.loadSplash(ctx, onReady, onUnavailable)
├── 2 秒看门狗：仍未 ready → onUnavailable
├── Native：AndroidView(slot.view)；SDK 的 onAdSkip/onAdTimeOver → onFinished
│           另设 6 秒硬上限兜底（SDK 异常不回调时强制结束并 destroy）
└── Shell：现有 5 秒自绘壳（BannerAd + 倒计时 + 3 秒后可跳过）
```

- Meta：`loadSplash` 直接 `onUnavailable()` → 永远 Shell，行为与 v1.1.0 一致；
- 穿山甲：`onReady` 显示原生开屏，倒计时/跳过样式由 SDK 渲染；
- 加载失败/2 秒未就绪/未配置 → Shell（横幅或占位），冷启动不白屏、不卡死；
- 2 秒后即使 SDK 迟到的成功回调也忽略（避免“进入应用后又弹开屏”）；
- 预加载：`PangleAdSdk.init` 时后台预热一次 `loadSplashAd`，命中则冷启动直接原生。

### D8. 依赖落地：官方 Maven 仓库（已核实）

官方集成文档 [SDK接入配置](https://www.csjplatform.com/supportcenter/5397) 的「方式一：maven 集成」给出：

- 仓库：`https://artifact.bytedance.com/repository/pangle`
- 坐标：`com.pangle.cn:ads-sdk-pro:7.8.1.5`（`packaging=aar`，POM 未声明外部依赖）

已验证 `maven-metadata.xml` 的 latest/release = **7.8.1.5**（lastUpdated 2026-10-10），
对应 POM 可访问；不需要下载/入库 AAR。项目启用了 `FAIL_ON_PROJECT_REPOS`，
仓库只能声明在 `settings.gradle.kts`：

```kotlin
maven {
    url = uri("https://artifact.bytedance.com/repository/pangle")
    content { includeGroup("com.pangle.cn") }
}
```

版本登记在 `gradle/libs.versions.toml`：

```toml
pangle = "7.8.1.5"
pangle-ads = { group = "com.pangle.cn", name = "ads-sdk-pro", version.ref = "pangle" }
```

`:ads:pangle` 只写 `implementation(libs.pangle.ads)`，仓库里不落任何二进制。

官方文档还要求手动集成时补 `com.squareup.okhttp3:okhttp:3.12.1`（AAR 不带网络库）；
本项目 `:base` 的 Ktor 已经带 okhttp，先不降级重复引入，运行期初始化失败再对齐版本。

### D9. 权限、合规、R8

- 在 `:app/src/main/AndroidManifest.xml` 用 `tools:node="remove"` 剔除 SDK 带入的敏感权限：
  `READ_PHONE_STATE`、`WRITE_EXTERNAL_STORAGE`、`ACCESS_FINE/COARSE_LOCATION`、
  `READ_CONTACTS`、`SEND_SMS`、`REQUEST_INSTALL_PACKAGES`、`QUERY_ALL_PACKAGES`；
- 保留 `INTERNET` / `ACCESS_NETWORK_STATE` / `AD_ID`；`ACCESS_WIFI_STATE` 等无运行时提示的保留；
- 剔除会影响下载类广告转化与部分归因，属产品取舍（plan review 确认）；
- 合规：未配置 appId 或未到广告请求时不初始化 SDK；个性化推荐开关默认关闭
  （字段以 7.8.1.5 `TTAdConfig` 的 privacy/consent 配置为准，实现时核对文档），
  完整隐私同意弹窗另立 spec；
- R8：provider 实现类由 `:app` 直接 `new`（非反射），不会被裁掉；
  检查 AAR 自带 consumer rules 是否覆盖 `whiteList.txt` 等资源（7.0.1.0 起明确要求），
  缺失则在 `app/src/main/keepRules/` 补规则。

### D10. 配置

```kotlin
// ads/meta/.../MetaAdsConfig.kt（迁移自 AdsConfig）
object MetaAdsConfig {
    const val BANNER_PLACEMENT_ID = ""
    const val INTERSTITIAL_PLACEMENT_ID = ""
    const val SPLASH_PLACEMENT_ID = ""
    val configured get() = BANNER_PLACEMENT_ID.isNotBlank() && INTERSTITIAL_PLACEMENT_ID.isNotBlank()
}

// ads/pangle/.../PangleAdsConfig.kt
object PangleAdsConfig {
    const val APP_ID = ""                  // 穿山甲应用 ID
    const val BANNER_CODE_ID = ""          // 横幅代码位
    const val INTERSTITIAL_CODE_ID = ""    // 全屏/插屏代码位
    const val SPLASH_CODE_ID = ""          // 原生开屏代码位（为空则回退自绘壳）
    val configured get() = APP_ID.isNotBlank() &&
        BANNER_CODE_ID.isNotBlank() && INTERSTITIAL_CODE_ID.isNotBlank()
}
```

- 阈值与超时统一在 `AdGateConfig`（`:ads:api`），不放在 provider 里；
- 参数留空 = 未配置：不初始化、不请求、横幅占位、门控放行（与现状一致）。

## 4. 文件清单

### 新增

```
ads/api/build.gradle.kts
ads/api/src/main/AndroidManifest.xml
ads/api/src/main/java/com/foolproof/ads/api/AdSdk.kt            // AdSdk / AdProviderId / AdBannerSlot / SplashAdSlot / AdSdkRegistry
ads/api/src/main/java/com/foolproof/ads/api/AdGateConfig.kt
ads/api/src/main/java/com/foolproof/ads/api/AdUsage.kt
ads/api/src/main/java/com/foolproof/ads/api/CountingAdGate.kt   // FullscreenAd / FullscreenAdLoader / DelayScheduler
ads/api/src/test/java/com/foolproof/ads/api/CountingAdGateTest.kt

ads/meta/build.gradle.kts
ads/meta/src/main/AndroidManifest.xml
ads/meta/src/main/java/com/foolproof/ads/meta/MetaAdsConfig.kt
ads/meta/src/main/java/com/foolproof/ads/meta/MetaAdSdk.kt
ads/meta/src/main/java/com/foolproof/ads/meta/MetaFullscreenLoader.kt
ads/meta/src/main/java/com/foolproof/ads/meta/MetaBannerSlot.kt

ads/pangle/build.gradle.kts
ads/pangle/src/main/AndroidManifest.xml
ads/pangle/src/main/java/com/foolproof/ads/pangle/PangleAdsConfig.kt
ads/pangle/src/main/java/com/foolproof/ads/pangle/PanglePrivacyController.kt
ads/pangle/src/main/java/com/foolproof/ads/pangle/PangleAdSdk.kt
ads/pangle/src/main/java/com/foolproof/ads/pangle/PangleFullscreenLoader.kt
ads/pangle/src/main/java/com/foolproof/ads/pangle/PangleBannerSlot.kt
ads/pangle/src/main/java/com/foolproof/ads/pangle/PangleSplashSlot.kt
ads/pangle/src/test/java/com/foolproof/ads/pangle/PangleAdsConfigTest.kt

app/src/ad/meta/kotlin/com/foolproof/ads/AdSdkFactory.kt
app/src/ad/pangle/kotlin/com/foolproof/ads/AdSdkFactory.kt
```

### 修改

```
settings.gradle.kts                          + include 三个模块、穿山甲官方 Maven 仓库
gradle/libs.versions.toml                    + pangle 7.8.1.5 / com.pangle.cn:ads-sdk-pro
gradle.properties                            + foolproof.adSdk=meta
app/build.gradle.kts                         + 开关校验、源集、条件依赖
app/src/main/java/com/foolproof/FoolproofApp.kt        setupAds 改为 createAdSdk()
app/src/main/java/com/foolproof/ads/BannerAd.kt        SDK 无关：走 AdSdkRegistry + 占位
app/src/main/java/com/foolproof/ads/UsageStore.kt      实现 AdUsage
app/src/main/java/com/foolproof/ads/SplashScheduler.kt 不变（确认无需改）
app/src/main/AndroidManifest.xml             权限剔除（tools:node="remove"）
base/src/main/java/com/foolproof/base/ads/AdGate.kt    仅注释：实现位置由 :app 改为 :ads:*
.specify/memory/constitution.md             v1.19（§2/§3/§11/§14）
docs/architecture.md / features.md / localization-and-ads.md / handoff.md / development.md
specs/README.md                             + 010 索引
```

### 删除

```
app/src/main/java/com/foolproof/ads/MetaAdGate.kt    → :ads:meta
app/src/main/java/com/foolproof/ads/AdsConfig.kt     → MetaAdsConfig / AdGateConfig
```

> `SplashAdOverlay` 仍在 `app/.../ads/BannerAd.kt`（与 `BannerAd` 同文件），只改内容来源。

## 5. 实施阶段

- **Phase 0 — 骨架与开关（不改行为）**
  1. `settings.gradle.kts` 注册 `:ads:api` / `:ads:meta`（`:ads:pangle` 随 AAR 在 Phase 1 注册）；
  2. `:ads:api` 契约 + `CountingAdGate` + 单测；
  3. `:ads:meta` 迁移 Meta（逻辑照搬，含横幅与开屏壳回退）；
  4. `:app` 接入 registry/factory/源集，`SplashAdOverlay` 改为“加载→原生/回退”结构；
  5. 验证 `:app:assembleDebug` 成功、Meta 行为不变。
- **Phase 1 — 穿山甲接入**
  1. `settings.gradle.kts` 注册 `:ads:pangle` + 官方 Maven 仓库；`libs.versions.toml` 登记
     `com.pangle.cn:ads-sdk-pro:7.8.1.5`，模块编译通过；
  2. 初始化、横幅、全屏门控、原生开屏实现，空配置全降级；
  3. `:app` 补 pangle 源集工厂与 Manifest 权限剔除；
  4. `-Pfoolproof.adSdk=pangle` 构建通过。
- **Phase 2 — 治理与文档**：宪法 v1.19、docs 五处、specs 索引、handoff。
- **Phase 3 — 验证**：单测、双构建、真机/模拟器三条路径、体积与 Manifest 对比。

## 6. 测试与验证

| 层 | 内容 |
|---|---|
| JVM 单测 | `CountingAdGate`：<5 放行 / =5 展示 / 加载失败 / 超时 / 关闭清零 / 只回调一次；`PangleAdsConfig` 空值判定 |
| 构建 | `:app:assembleDebug`（默认 meta）、`-Pfoolproof.adSdk=pangle`、非法值报错 |
| 依赖互斥 | `:app:dependencies --configuration debugRuntimeClasspath` 中 `com.facebook.ads` 与 `com.bytedance.sdk.openadsdk` 互斥 |
| 产物 | 解包 APK，`classes*.dex` 中同样互斥；体积与 v1.1.0 对比 |
| 真机/模拟器 | 三个 tab 横幅、5 次门控、冷启动/后台 1 小时开屏；空配置降级；低网/断网 |
| 回归 | 切回 meta 跑 User Story 5 三条场景 |

命令（Windows）：

```powershell
.\gradlew.bat :ads:api:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDebug -Pfoolproof.adSdk=pangle
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath | Select-String "com.facebook.ads|com.bytedance"
```

## 7. 风险

| 风险 | 缓解 |
|---|---|
| **`ads-sdk-pro` POM 无传递依赖** | 项目已有 okhttp（Ktor）；如初始化失败再按官方文档补 `okhttp:3.12.1` |
| 官方 Maven 仓库不可达（网络/CI） | 仓库集中声明在 settings；必要时加代理或公司镜像，仍不需要落 AAR |
| 穿山甲 7.6.1.2+ **仅 arm64**（7.8.1.5 下线 32 位 so） | 32 位设备无填充但降级为占位；若必须覆盖 32 位，退到 7.5.1.7（plan review 决策） |
| 包体增加（SDK 数 MB） | 只打包选中 provider；发布前记录体积；必要时按 ABI 拆包 |
| SDK 带入敏感权限 | `tools:node="remove"` + `apkanalyzer` 对比 v1.1.0 |
| 合规：未同意隐私政策就采集设备标识 | 未配置/未请求不初始化；个性化推荐默认关；完整同意流程另立 spec |
| R8 裁掉 SDK 必需类/资源 | 确认 AAR consumer rules 与 `whiteList.txt` 资源；缺失补 keep 规则 |
| 原生开屏拖慢冷启动 | 2 秒看门狗 + 6 秒硬上限；未命中直接回退自绘壳，不阻塞进入应用 |
| 两个 SDK 同时进包 | 依赖开关 + 构建断言（非法值直接失败） |
| Meta 迁移引入回归 | 逻辑照搬，只换包名；对照 User Story 5 逐项验证 |
| 超时兜底改变 Meta 时序 | 5 秒阈值可配置；只在“SDK 完全无回调”时触发，正常路径不受影响 |

## 8. Plan Review 结论（2026-10-22 已拍板）

| # | 议题 | 决定 |
|---|---|---|
| 1 | 切换方式 | Gradle 属性 `foolproof.adSdk`（默认 `meta`），`assembleDebug` 等命令不变 |
| 2 | SDK 版本/接入 | 穿山甲 **7.8.1.5**（arm64-only），走官方 Maven `com.pangle.cn:ads-sdk-pro`，**不落 AAR** |
| 3 | 权限 | **剔除全部敏感权限**（`READ_PHONE_STATE` / `REQUEST_INSTALL_PACKAGES` / `QUERY_ALL_PACKAGES` / 定位 / 通讯录 / 短信 / 写存储） |
| 4 | 开屏 | 穿山甲接 **原生 TTSplashAd**；Meta 保持自绘壳；不可用时回退自绘壳 |
| 5 | 默认 provider | `meta` |
| 6 | 执行 | 直接进入 implement：先写 `tasks.md`，再做 Phase 0 |

**接入方式修订（2026-10-22）**：穿山甲提供官方 Maven 集成
（`https://artifact.bytedance.com/repository/pangle` + `com.pangle.cn:ads-sdk-pro:7.8.1.5`），
已核实 POM 与 metadata 可访问，因此不再需要下载 AAR/入库二进制，Phase 1 可直接开始。

## 9. 完成定义

- spec 的 FR-001~FR-019 逐条有实现或测试对应，SC-001~SC-007 可复现；
- `meta` / `pangle` 两种开关都能 `assembleDebug`，产物 SDK 类互斥；
- feature 模块零 diff；
- 单测全绿，真机（`10AG9C079Y001L0`）或模拟器（`emulator-5554`）三条广告路径各验一次；
- 宪法 v1.19、docs 与 specs 索引同步，handoff 写明“如何切换”。

### 宪法 v1.19 修订草案（implement 阶段落地）

1. **§2 技术栈**「广告」行改为：

   | 类别 | 选型 | 备注 |
   | --- | --- | --- |
   | 广告 | Meta Audience Network 6.22.0（海外）<br>穿山甲 Pangle 7.8.1.5（国内） | 分别封装在 `:ads:meta` / `:ads:pangle`；构建开关 `foolproof.adSdk` 二选一，单个产物只含一个 SDK；feature 只依赖 `:base` 的 `AdGate` |

2. **§3 模块结构**加入：

   ```
   :ads:api      // 广告抽象：AdSdk、横幅槽、门控策略（无具体 SDK）
   :ads:meta     // Meta Audience Network 实现
   :ads:pangle   // 穿山甲实现（国内）
   ```

   依赖方向补一行：`ads → base`；`app → ads:api + 选中的一个 ads provider`。

3. **§11** 增加：广告 SDK 带入的定位/通讯录/短信/`READ_PHONE_STATE`/`WRITE_EXTERNAL_STORAGE`/
   `REQUEST_INSTALL_PACKAGES`/`QUERY_ALL_PACKAGES` 必须用 `tools:node="remove"` 剔除；
   穿山甲在未配置或未获隐私同意前不得初始化。

4. **§14** 追加 v1.19 changelog（日期、变更点、理由：Meta 在国内不可用）。
