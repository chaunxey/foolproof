# 架构与模块边界

## 模块

```
:app                              应用壳：导航、页面组装、广告、主题、设置
:base                             MVI 基类、Koin、Ktor 封装、日志、广告接口
:common                           TabHost、共享 Compose 组件（品牌按钮）、导航
:features:image-compression       图片压缩
:features:duplicate-cleanup       重复照片清理
:features:video-compression       视频压缩 + 视频拼接
:features:file-conversion         图片/文本 → PDF、PDF 工具
```

依赖方向单一，禁止反向与横穿：

```
app → base, common, features/*
feature → base, common
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

## base

- **MVI**：`BaseViewModel<S, E>`，`updateState` / `sendEffect`，协程用基类 `launch`，异常统一进 `onError`；
- **网络**：Ktor 封装 `NetworkClient` + `ApiResult`，目前没有功能使用，为语音聊天预留；
- **日志**：`LogUtil` 注入使用，debug 才输出，自动脱敏 `sk-` / `Authorization`；
- **广告**：`AdGate` / `AdGateProvider` 接口，app 层实现 `MetaAdGate`；
- **工具**：`ByteFormat`、`OutputNaming`、`findActivity`。

约定：`:base` 不含 UI、不申请权限、不直接发起请求。

## 本地存储

统一使用 `:common` 的 `LocalStore`（DataStore Preferences，文件 `app_data.preferences_pb`）：

- `readString/Int/Long/Float/Boolean`：启动阶段同步读一次；
- `writeString/Int/Long/Float/Boolean`：异步落盘；
- `remove`：删除某个 key；
- 设置、登录态、广告计数、开屏时间都走这一套，不再直接使用 SharedPreferences。

## AGP 9 约定

1. 不引入 `org.jetbrains.kotlin.android`，AGP 9 内置 Kotlin；
2. `compileSdk { version = release(37) }`；
3. R8 规则放 `app/src/main/keepRules/`，没有 `proguard-rules.pro`；
4. release 优化用 `optimization { enable = true; packageScope = ... }`；
5. release 使用 `keystore.properties` 中的签名配置；文件缺失时输出 unsigned。

## 依赖选择说明

- **Media3 Transformer**：视频压缩/拼接；
- **CameraX**：PDF 图片模式的拍照；
- **PDFBox-Android（Apache-2.0）**：PDF 合并/拆分/旋转/页面重写/压缩；
  这是对宪法“零第三方 PDF 库”的偏离，后续宪法修订需同步；
- **Meta Audience Network**：广告；
- Ktor/BouncyCastle 当前只被 base 网络层引用，可由 R8 裁剪；若确定短期不做 AI 聊天，可整体移出以进一步减小体积。
