# Tasks: 可切换广告 SDK（新增穿山甲模块）

**Feature**: `specs/010-ad-sdk-provider/spec.md` · **Plan**: `specs/010-ad-sdk-provider/plan.md`
**Constitution**: v1.19（广告 provider 可切换）
**分支**: `feature/ad-sdk-provider`
**测试**：`./gradlew :ads:api:testDebugUnitTest`、`:ads:pangle:testDebugUnitTest`、`:app:assembleDebug`

> **进度（2026-10-23）**：Phase 0 / Phase 1 / Phase 2 / release 收尾均已完成。
> `:ads:api` 7 个单测、`:ads:pangle` 5 个单测通过；`meta` / `pangle` 两种构建均成功；
> pangle 依赖树只有 `com.pangle.cn:mediation-sdk:7.8.1.4`，meta 只有 FAN；
> 真实设备验证：**横幅有填充、原生开屏有填充**（需 `usesCleartextTraffic`）；
> 门控广告位如为「插屏」类型会报 840031，需改成「插全屏/全屏视频」（T027）。
> **待办**：门控广告位类型调整后验证 5 次门控全屏。

## Phase 0: Setup + Foundational（不依赖 SDK）✅

- [x] T001 新建 `:ads:api`：`ads/api/build.gradle.kts`（android library、`api(project(":base"))`、
  minSdk 24、Java 11）、`AndroidManifest.xml`、`settings.gradle.kts` 注册
- [x] T002 `:ads:api` 契约：`AdSdk : AdGate`、`AdProviderId`、`AdBannerSlot`、`SplashAdSlot`、
  `AdSdkRegistry`、`AdGateConfig`、`AdUsage`（`ads/api/src/main/java/com/foolproof/ads/api/`）
- [x] T003 共享门控 `CountingAdGate`（纯 Kotlin：`FullscreenAd` / `FullscreenAdLoader` /
  `DelayScheduler`，超时兜底 + 只回调一次）+ `HandlerDelayScheduler`（Android）
- [x] T004 `:ads:api` JVM 单测：`CountingAdGateTest` 覆盖 <5 放行、=5 展示、加载失败、
  超时放行、关闭清零、只回调一次（7 个用例全绿）
- [x] T005 新建 `:ads:meta`：`ads/meta/build.gradle.kts`（依赖 `:ads:api` + `libs.audience.network`）、
  manifest、settings 注册
- [x] T006 Meta 迁移：`MetaAdsConfig`、`MetaBannerSlot`（320×50、60s 刷新、destroy）、
  `MetaFullscreenLoader`、`MetaAdSdk`（初始化、门控、`loadSplash` → `onUnavailable`）
- [x] T007 `:app` 开关：`gradle.properties` 加 `foolproof.adSdk=meta`；`app/build.gradle.kts`
  校验取值、按开关挂 `src/ad/<provider>/kotlin` 与条件依赖
- [x] T008 `app/src/ad/meta/kotlin/com/foolproof/ads/AdSdkFactory.kt`：`createAdSdk(usage)`
- [x] T009 `FoolproofApp.setupAds` 改用 `createAdSdk`，同时设置 `AdSdkRegistry.sdk` 与
  `AdGateProvider.gate`（feature 模块零改动）
- [x] T010 `:app` 横幅改为 SDK 无关：`BannerAd` 用 `AdBannerSlot` + `AndroidView` + 60s 刷新 +
  占位 + `DisposableEffect` 释放
- [x] T011 `SplashAdOverlay` 改为 Loading → Native/Shell 状态机（2s 看门狗、6s 硬上限、
  Shell 保持 5s 倒计时与 3s 跳过）
- [x] T012 删除 `MetaAdGate.kt` / `AdsConfig.kt`；`UsageStore` 实现 `AdUsage`；
  `AdGate` 注释更新实现位置
- [x] T013 Phase 0 验证：`:ads:api:testDebugUnitTest` 7/7 通过；`:app:assembleDebug` 成功；
  emulator-5554 启动无崩溃、横幅占位、自绘开屏正常；依赖树只有 FAN

## Phase 1: 穿山甲接入（官方 Maven）✅

- [x] T014 `settings.gradle.kts` 注册 `:ads:pangle` + 官方 Maven
  （`https://artifact.bytedance.com/repository/pangle`）；`libs.versions.toml` 登记
  `com.pangle.cn:mediation-sdk:7.8.1.4`（GroMore 聚合广告位）；模块编译通过
- [x] T015 `PangleAdsConfig`（APP_ID / BANNER_CODE_ID / INTERSTITIAL_CODE_ID / SPLASH_CODE_ID，
  空串 = 未配置）
- [x] T016 `PangleBannerSlot`：`loadBannerExpressAd` + `TTNativeExpressAd.render()`，
  换新前 `destroy()`，60s 刷新，SDK 未就绪时短轮询；`onRenderSuccess` 的 view 可能为 null，
  用 `getExpressAdView()` 兜底
- [x] T017 `PangleFullscreenLoader`：`loadFullScreenVideoAd` + `setFullScreenVideoAdInteractionListener`，
  `onAdClose`/`onError` 收口到 `CountingAdGate`
- [x] T018 `PangleSplashSlot`：`loadSplashAd` + `getSplashView/showSplashView` +
  `setSplashAdListener`（`onSplashAdClose` → 关闭），`init` 成功后预热一次
- [x] T019 `PangleAdSdk`：`TTAdSdk.init` + `TTAdSdk.start`、`useMediation(true)`、debug 模式、
  `TTCustomController` 关闭位置/电话状态/应用列表采集，未配置全降级
- [x] T020 `app/src/ad/pangle/kotlin/.../AdSdkFactory.kt`；`:app` Manifest 用
  `tools:node="remove"` 剔除 `WRITE_EXTERNAL_STORAGE` 与 ASUS 设备权限；
  开 `android:usesCleartextTraffic="true"`（开屏素材走 http）
- [x] T021 `PangleAdsConfigTest`（5/5）；`.\gradlew.bat :app:assembleDebug "-Pfoolproof.adSdk=pangle"` 成功

## Phase 2: 治理与文档 ✅

- [x] T022 宪法升 v1.19：§2 广告依赖、§3 模块结构与依赖方向、§11 权限、§14 changelog
- [x] T023 文档同步：`docs/architecture.md`、`features.md`、`localization-and-ads.md`、
  `build-and-release.md`、`handoff.md`
- [x] T024 `specs/README.md` 增加 010 索引

## Phase 3: 验证与收尾

- [x] T025 双构建 + 依赖互斥：`meta` / `pangle` 各构建成功；
  pangle 依赖树只有 `com.pangle.cn:mediation-sdk`，meta 只有 FAN；非法开关值构建失败
- [x] T026 release 收尾：`assembleRelease` / `bundleRelease` 两种 provider 各跑一次——
  meta APK 14.3 MB / AAB 18.3 MB，pangle APK 19.7 MB / AAB 21.1 MB；
  APK 类互斥（meta 有 `com.facebook.ads` 无 Pangle，pangle 反之）；release 权限清单无
  `WRITE_EXTERNAL_STORAGE` / `READ_PHONE_STATE` / 定位等敏感项
- [ ] T027 设备验收（`10AG9C079Y001L0`）：
  meta 三路径回归；pangle 横幅/原生开屏已验证有填充；
  **门控广告位需改为「插全屏/全屏视频」类型**后验证 5 次门控全屏（当前插屏类型报 840031）
