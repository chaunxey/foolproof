# Tasks: 可切换广告 SDK（新增穿山甲模块）

**Feature**: `specs/010-ad-sdk-provider/spec.md` · **Plan**: `specs/010-ad-sdk-provider/plan.md`
**Constitution**: v1.18 → 本 feature 完成后升 v1.19
**分支**: `feature/ad-sdk-provider`
**测试**：`./gradlew :ads:api:testDebugUnitTest`、`:app:assembleDebug`
**组织方式**：先做不依赖 AAR 的抽象层与 Meta 迁移（Phase 0），再接入穿山甲（Phase 1），
最后补治理文档与全量验收。

> **进度（2026-10-22）**：Phase 0 已完成并验证——`:ads:api` 7 个单测通过、
> `:app:assembleDebug` 成功、`emulator-5554` 安装启动无崩溃、横幅占位与自绘开屏正常、
> 运行时依赖树只有 FAN。
> **阻塞项**：Phase 1 需要 `open_ad_sdk` 7.8.1.5 AAR。AAR 未就位前 `foolproof.adSdk`
> 只接受 `meta`（传 `pangle` 会在配置期报错）。

## Phase 0: Setup + Foundational（不依赖 AAR）✅

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
  校验取值、按开关挂 `src/ad/<provider>/kotlin` 与条件依赖（pangle 分支 Phase 1 加）
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
  emulator-5554 启动无崩溃、横幅占位、自绘开屏正常；依赖树只有 FAN。
  （门控的分支逻辑由单测覆盖；真机 5 次门控与 pangle 三路径留 Phase 3）

## Phase 1: 穿山甲接入（需 AAR）

- [ ] T014 `settings.gradle.kts` 注册 `:ads:pangle` + 本地 Maven 仓库（`ads/pangle/repo`）；
  放入 `open_ad_sdk-7.8.1.5.aar` + 手写 POM；`ads/pangle/build.gradle.kts` 编译通过
- [ ] T015 `PangleAdsConfig`（APP_ID / BANNER_CODE_ID / INTERSTITIAL_CODE_ID / SPLASH_CODE_ID，
  空串 = 未配置）
- [ ] T016 `PangleBannerSlot`：`loadBannerExpressAd` + `TTNativeExpressAd.render()`，
  换新前 `destroy()`，60s 刷新
- [ ] T017 `PangleFullscreenLoader`：`loadFullScreenVideoAd` + `setFullScreenVideoAdInteractionListener`，
  `onAdClose`/`onError` 收口到 `CountingAdGate`
- [ ] T018 `PangleSplashSlot`：`loadSplashAd` + `getSplashView()` + `setSplashInteractionListener`
  （`onAdSkip`/`onAdTimeOver` → 关闭），`init` 后台预热一次
- [ ] T019 `PangleAdSdk`：`TTAdSdk.init` + `TTAdSdk.start`、debug 模式、未配置全降级
- [ ] T020 `app/src/ad/pangle/kotlin/.../AdSdkFactory.kt`；`:app` Manifest 用
  `tools:node="remove"` 剔除敏感权限
- [ ] T021 `PangleAdsConfigTest`；`.\gradlew.bat :app:assembleDebug "-Pfoolproof.adSdk=pangle"` 成功

## Phase 2: 治理与文档

- [ ] T022 宪法升 v1.19：§2 广告依赖、§3 模块结构与依赖方向、§11 权限、§14 changelog
- [ ] T023 文档同步：`docs/architecture.md`、`features.md`、`localization-and-ads.md`
  （切换与配置方法）、`handoff.md`、`development.md`
- [ ] T024 `specs/README.md` 增加 010 索引

## Phase 3: 验证与收尾

- [ ] T025 双构建 + 依赖互斥：`meta` / `pangle` 各构建一次，
  `:app:dependencies` 与 APK 中 SDK 类互斥；非法开关值构建失败
- [ ] T026 设备验收（`10AG9C079Y001L0` 或 `emulator-5554`）：
  meta 三路径回归；pangle 有代码位/空配置两种降级；门控 5 次；开屏
- [ ] T027 体积与 Manifest 对比（相对 v1.1.0）；release `assembleRelease` 通过
