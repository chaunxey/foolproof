# 架构与模块边界

## 模块

```
:app                              应用壳：导航、页面组装、广告开关、主题、设置
:base                             MVI 基类、Koin、Ktor 封装、日志、广告接口
:common                           TabHost、共享 Compose 组件（品牌按钮）、导航
:ads:api                          广告抽象：AdSdk、横幅/开屏槽、门控策略（无具体 SDK）
:ads:meta                         Meta Audience Network 实现
:ads:pangle                       穿山甲（GroMore 融合 SDK）实现
:features:image-compression       图片压缩
:features:duplicate-cleanup       重复照片清理
:features:video-compression       视频压缩 + 视频拼接
:features:file-conversion         图片/文本 → PDF、PDF 工具
```

依赖方向单一，禁止反向与横穿：

```
app → base, common, features/*, ads:api + 选中的一个 ads provider
feature → base, common
ads → base
common → base
base → 无业务依赖
```

业务模块之间禁止互相依赖；需要共享的 UI 放 `:common`，基础设施放 `:base`。

## 页面组装

每个 feature 只对外暴露一个 `@Composable` Section，整页由 `:app` 组装：

- `CompressionTabScreen`：图片压缩 + 去重，中间放横幅；
- `VideoTabScreen`：压缩 / 拼接二选一；
- `ConversionTabScreen`：生成 PDF / PDF 工具二选一；
- `FoolproofTabHost`：底部 tab，业务模块不写导航。

## 广告 provider

- `:ads:api`：`AdSdk : AdGate`（`init` / `createBanner` / `loadSplash` / 门控）、
  `AdBannerSlot`、`SplashAdSlot`、`CountingAdGate`（5 次成功 + 5 秒超时兜底）；
- `:ads:meta`：Meta 横幅/插屏/初始化；开屏继续用 `:app` 的自绘壳（`loadSplash` 直接回退）；
- `:ads:pangle`：穿山甲 GroMore 融合 SDK（模板横幅、插全屏门控、原生开屏 `CSJSplashAd`）；
  广告位是 **GroMore 聚合广告位（ID 以 1 开头）**，初始化开 `useMediation`，请求都带
  `MediationAdSlot`；初始化成功后预热一次开屏，并在 `TTCustomController` 里关闭位置/电话状态/
  应用列表等采集；
- 切换：`gradle.properties` 的 `foolproof.adSdk=meta|pangle`（默认 `meta`）。`:app` 只把选中的
  provider 挂进依赖，且只编译 `src/ad/<provider>/kotlin` 下的工厂文件，一个产物里只有一套 SDK；
- 启动：`FoolproofApp.setupAds()` 创建 `AdSdk`，同时写入 `AdSdkRegistry`（Compose 横幅/开屏用）
  和 `AdGateProvider`（feature 门控用）；
- 开屏：`:app` 的 `SplashAdOverlay` 先试原生开屏（2 秒看门狗、6 秒硬上限），
  不支持/失败/超时回退到自绘 5 秒壳。

## base

- **MVI**：`BaseViewModel<S, E>`，`updateState` / `sendEffect`，协程用基类 `launch`，异常统一进 `onError`；
- **网络**：Ktor 封装 `NetworkClient` + `ApiResult`，目前没有功能使用，为语音聊天预留；
- **日志**：`LogUtil` 注入使用，debug 才输出，自动脱敏 `sk-` / `Authorization`；
- **广告**：`AdGate` / `AdGateProvider` 接口；实现放在 `:ads:meta` / `:ads:pangle`，由构建开关二选一；
- **工具**：`ByteFormat`、`OutputNaming`、`findActivity`。

约定：`:base` 不含 UI、不申请权限、不直接发起请求。

## 本地存储

统一使用 `:common` 的 `LocalStore`（DataStore Preferences，文件 `app_data.preferences_pb`）：

- `readString/Int/Long/Float/Boolean`：启动阶段同步读一次；
- `writeString/Int/Long/Float/Boolean`：异步落盘；
- `remove`：删除某个 key；
- 设置、登录态、广告计数、开屏时间都走这一套，不再维护第二套键值存储。

## AGP 9 约定

1. 不引入 `org.jetbrains.kotlin.android`，AGP 9 内置 Kotlin；
2. `compileSdk { version = release(37) }`；
3. R8 规则放 `app/src/main/keepRules/`，没有 `proguard-rules.pro`；
4. release 优化用 `optimization { enable = true; packageScope = ... }`；
5. release 使用 `keystore.properties` 中的签名配置；文件缺失时输出 unsigned。

## 依赖选择说明

- **Media3 Transformer**：视频压缩/拼接/裁剪；
- **CameraX**：PDF 图片模式的拍照；
- **PDFBox-Android（Apache-2.0）**：PDF 合并/拆分/旋转/页面重写/压缩/转图片；
  宪法 v1.18 已将 PDFBox 登记为正式 PDF 能力依赖，不再算偏离；
- **Meta Audience Network**：广告（海外）；
- **穿山甲 GroMore 融合 SDK（官方 Maven `com.pangle.cn:mediation-sdk`）**：广告（国内），与 Meta 二选一；
- Ktor/BouncyCastle 当前只被 base 网络层引用，可由 R8 裁剪；若确定短期不做 AI 聊天，可整体移出以进一步减小体积。
