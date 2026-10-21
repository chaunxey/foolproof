# Foolproof

纯本地的 Android 媒体工具箱：**图片压缩、重复照片清理、视频压缩/拼接、图片与文本转 PDF、离线 PDF 工具**。

所有文件都在设备上处理，不上传、不需要账号、不加水印；只有广告 SDK 需要联网。

## 功能

| 功能 | 说明 | 状态 |
| --- | --- | --- |
| 图片压缩 | JPEG / WebP，质量可调，批量串行，实时预估体积 | ✅ |
| 重复照片清理 | dHash 分组，手动勾选，走系统回收站，绝不自动删 | ✅ |
| 视频压缩 | 480p/720p/1080p，可按档位或**目标体积**压缩，支持裁剪 | ✅ |
| 视频裁剪 | 只保留选中的起止区间，关键帧对齐时尝试免重编码 | ✅ |
| 视频拼接 | 2–10 段，逐段裁剪、拖拽排序，格式不统一时自动走兼容路径 | ✅ |
| 图片 → PDF | 多选/拍照，A4 排版，可删图、调页数 | ✅ |
| 文本 / Markdown → PDF | 自研极简 Markdown，CJK 折行 | ✅ |
| PDF 工具 | 合并、按页码范围拆分、旋转、页面重排/删除、压缩 | ✅ |
| 多语言 | 简体中文 / English / ខ្មែរ / 日本語 / Français / Deutsch，切换不重启 | ✅ |
| 主题与字体 | 明暗主题、字体 100%–200%、大按钮 | ✅ |
| 广告 | Meta Audience Network，横幅/插屏/开屏，可跳过 | 骨架完成，placement ID 待配 |
| AI 聊天 | 语音/文本对话 | 未开始 |

## 为什么是 Foolproof

- **全本地**：图片、视频、PDF 都不离开设备；
- **零账号**：不登录、不注册、不要邮箱；
- **无强制付费**：基础功能免费，广告支撑；
- **小体积**：release APK 约 **13 MB**；
- **对低端机友好**：无重型 native 模型，大字体/老人模式可用；
- **高棉语完整支持**：界面、文案、大字体都做了本地化。

## 构建

环境：JDK 17+、Android SDK Platform 37、Android Studio（支持 AGP 9.4）。

```bash
# debug
./gradlew :app:assembleDebug

# release（未签名）
./gradlew :app:assembleRelease

# 单元测试
./gradlew :app:testDebugUnitTest
```

产物在 `app/build/outputs/apk/`。release 体积优化、签名与 AAB 说明见
[构建与发布](docs/build-and-release.md)。

## 技术栈

Kotlin 2.4 · Jetpack Compose（Material 3）· AGP 9 · Koin · Media3 Transformer ·
CameraX · PDFBox-Android · Meta Audience Network。版本统一在
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) 管理。

## 文档

- [架构与模块边界](docs/architecture.md) — 模块职责、依赖方向、MVI、AGP 9 约定
- [功能细节](docs/features.md) — 每个功能的实现与已知限制
- [构建与发布](docs/build-and-release.md) — 命令、测试、体积优化、签名
- [多语言与广告](docs/localization-and-ads.md) — 无感切换、广告位与合规
- [产物命名](docs/naming.md) — `Foolproof_*` 命名规则
- [开发流程](docs/development.md) — spec-kit、提交规范、测试要求
- [功能机会评估](docs/feature-opportunities.md) — 视频目标体积/PDF/OCR 的竞品与工作量
- [海外市场与技术机会（2026）](docs/market-opportunities-2026.md) — IAA 工具缺口、端侧 AI 技术雷达

## 状态

功能开发阶段基本完成，当前阻塞在**上架**：

1. Google Play 开发者账号与封闭测试；
2. 隐私政策、Data Safety（广告 / 相机 / 文件）；
3. Meta Audience Network placement ID（需要 Play 商店链接）；
4. 真机回归（PDF 页面/压缩、视频目标体积、长视频/大文件）。

## 许可证

尚未指定。若开源，请补充 `LICENSE` 并在此声明。
