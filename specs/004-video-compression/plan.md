# Implementation Plan: 视频压缩（video-compression）

**Branch**: `feature_foundation1` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-video-compression/spec.md`

## Summary

新增 `:features:video-compression`：选视频 → 选分辨率/码率档位 + 裁剪起止 → Media3 Transformer
前台转码 → 分享/存相册。

三处难点：**必须硬编（否则明确失败）**、**裁剪与缩放要组合进同一次转码**、**长任务可取消且不留垃圾文件**。

---

## Technical Context

**Language/Version**: Kotlin 2.4.21（AGP 9.4.1 内置），JVM Target 11

**Primary Dependencies**

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `:base` | 本工程 | MVI 基类、Koin、`LogUtil`、`ByteFormat` |
| `androidx.media3:media3-transformer` | **1.11.1** | 转码管线（编码器选择、封装、时间戳） |
| `androidx.media3:media3-effect` | **1.11.1** | `Presentation` 做分辨率缩放 |
| Compose BOM + material3 | 2026.09.00 | Section UI、时间轴 |
| `androidx.activity:activity-compose` | 1.13.0 | Photo Picker（选视频） |
| `androidx.core:core-ktx` | 现有 | FileProvider |

**不新增**：ExoPlayer / media3-ui（播放用平台 `VideoView`）、FFmpeg、WorkManager。

**关键兼容性（已核实 AAR 的 `AndroidManifest.xml`）**：
`media3-transformer` 与 `media3-effect` 的 `minSdkVersion=23`，本工程 `minSdk=24` → **兼容**。

**Storage**: 输入经 Photo Picker；输出 `files/videos/`；存相册经 MediaStore（API ≥ 29）

**Testing**: JUnit4 + `kotlinx-coroutines-test`（纯逻辑与状态机）；`androidTest` 查编码器能力

**Target Platform**: `minSdk 24` / `compileSdk 37` / `targetSdk 37`

---

## Constitution Check

| 宪法条款 | 检查点 | 结论 |
| --- | --- | --- |
| §1.2 不做什么 | 只做压缩与裁剪，不做剪辑；不联网 | ✅ |
| §2 技术栈（v1.13） | 仅 Media3 Transformer + effect；不引 FFmpeg | ✅ |
| §3 模块结构 | 只依赖 `:base`，不依赖其他 feature | ✅ |
| §3.2 同页规则 | 视频压缩**独占一页**；同页规则只适用图片压缩 + 重复清理 | ✅（新 tab） |
| §4 MVI | 继承 `BaseViewModel`，异常走 `onError` | ✅ |
| §5 注入 | ViewModel / 压缩器 / 编码器目录经 Koin | ✅ |
| §6 日志与复用 | 注入 `LogUtil`；`ByteFormat` 复用 `:base` | ✅ |
| §8 权限 | 选视频零权限；存相册不申请写权限 | ✅ |
| §9 一票否决 | 无 feature 直连网络、无写权限、`:app` 无业务逻辑 | ✅ |
| 质量门槛 | 档位映射 / 预估 / 状态机 / 取消 / 错误映射单测 | ✅ |

**结论：无违反项。**

---

## Project Structure

```text
settings.gradle.kts            # + include(":features:video-compression")

features/video-compression/
├── build.gradle.kts
└── src/
    ├── main/java/com/foolproof/features/video/
    │   ├── VideoCompressionSection.kt        # 对外唯一 Composable
    │   ├── VideoCompressionViewModel.kt
    │   ├── VideoCompressionContract.kt       # UiState / Action / Effect
    │   ├── di/VideoModule.kt
    │   ├── domain/
    │   │   ├── SourceVideo.kt                # id/名称/字节/时长/宽高/帧率/有无音轨
    │   │   ├── CompressionPreset.kt          # 分辨率档 + 码率档 → 目标参数（纯函数）
    │   │   ├── TrimRange.kt                  # 起止 ms + 校验（纯函数）
    │   │   ├── VideoCompressor.kt            # 接口（测试接缝）
    │   │   ├── EncoderCatalog.kt             # 接口：查询 H.264/H.265 编码能力
    │   │   └── VideoOutcome.kt               # 进度/成功/失败分类
    │   ├── data/
    │   │   ├── Media3VideoCompressor.kt      # Transformer + Presentation + Clipping
    │   │   ├── ContentResolverVideoReader.kt # MediaMetadataRetriever 读元信息
    │   │   ├── MediaStoreVideoExporter.kt    # 存相册（IS_PENDING）/ 分享
    │   │   └── MediaCodecEncoderCatalog.kt   # MediaCodecList 查询
    │   └── ui/
    │       ├── TrimTimeline.kt               # VideoView + 抽帧缩略图 + 双滑块
    │       └── PresetSelector.kt             # 分辨率/码率档位
    ├── test/java/com/foolproof/features/video/
    │   ├── CompressionPresetTest.kt
    │   ├── TrimRangeTest.kt
    │   └── VideoCompressionViewModelTest.kt
    └── androidTest/java/com/foolproof/features/video/
        └── EncoderCatalogTest.kt             # 打印并断言设备实际编码能力

app/
├── build.gradle.kts                          # + dependency
└── src/main/java/com/foolproof/
    ├── FoolproofApp.kt                       # + videoModule
    └── ui/FoolproofScreen.kt                 # 新增：底部两个 tab（图片 / 视频）
```

**Structure Decision**: 页面级拆分放 `:app`；feature 内不再细分 `platform/`（本模块平台差异集中在 `data/`）。

---

## Research（动手前确认的事实）

### R1. Transformer 的公开 API（扒 `media3-transformer-1.11.1.aar` 确认）

```java
VideoEncoderSettings.Builder#setBitrate(int)        // 码率可控（public）
Presentation.createForShortSide(int) / createForWidthAndHeight(int,int,int)  // 缩放在 media3-effect
Transformer#start(EditedMediaItem, String) + addListener(...) + cancel()
EditedMediaItem.Builder#setFrameRate(int) / setEffects(Effects) / setRemoveAudio(boolean)
```

**注意**：`TransformationRequest.Builder#setResolution(int)` 是**包内可见**，不能直接用；
缩放必须走 `Presentation` 这个 `Effect`。这一条如果搞错，会在编译期或运行期踩空。

**实现时又确认了两个与预期不同的事实（已按实际 API 写实现）**：

1. **`Transformer.Listener` 没有 `onProgress`** —— Media3 1.11 的进度只能轮询
   `Transformer#getProgress(ProgressHolder)`，`ProgressHolder.progress` 是 **Int**（0–100）。
   实现里每 400ms 轮询一次。
2. **`ExportResult` 不含输出路径** —— 它只有 `fileSizeBytes`/`durationMs` 等，
   输出路径以调用方传入的 `outputPath` 为准（`onCompleted` 回调里用它判断文件是否真的生成）。

### R2. 编码器能力只能运行时查（已实测，2026-10-21）

- `dumpsys media.codec` 在 API 34 上拿不到编码器列表；`/vendor/etc/media_codecs.xml` 的
  `<Encoders>` 段在模拟器上是**空**的（现代设备用 Codec2 动态注册）。
- 因此必须在应用内用 `MediaCodecList` 查询 `video/avc` 编码器及其 `VideoCapabilities`。

**实测结果（`EncoderCatalogTest`，两台设备）**：

| 设备 | 硬件 AVC 编码器 | 最大编码尺寸 | 码率上限 | HEVC |
| --- | --- | --- | --- | --- |
| 真机 vivo V2602A（API 37） | `c2.mtk.avc.encoder` hardware=true | 7680×7680 | 100 Mbps | 硬编可用（7680×7680），profiles: Baseline/Main/High |
| 模拟器（API 34） | **无**（只有 `c2.android.avc.encoder` / `OMX.google.h264.encoder`，hardware=false） | 2048×2048 | 12 Mbps | 软编上限仅 512×512，**不可用** |

- 真机有硬件编码器，且支持到 4K 以上 → **480p/720p/1080p 三档全部可用**；
- 模拟器只有软编，但 2048×2048 与 12 Mbps 上限仍**覆盖我们全部档位**（1080p=1920×1080、最高 8 Mbps），
  所以管线可以在模拟器上验证（只是慢）；
- 模拟器 HEVC 编码上限 512×512 → **H.265 选项必须按能力探测后再显示**（FR-005）。

**由此确定两条实现约束**：

1. `Media3VideoCompressor` 必须通过 `DefaultEncoderFactory.Builder.setVideoEncoderSelector(...)`
   显式**优先选硬件编码器**，否则 Media3 可能选到软编（真机上存在两套编码器）；
2. `EncoderCatalog` 需提供 `maxEncodeSize` 与 `isHardware`，供档位降级与 UI 提示使用。

### R3. 裁剪与缩放在同一次转码里完成

- 裁剪：`MediaItem.Builder().setClippingConfiguration(ClippingConfiguration.createForStartEnd(...))`；
  Transformer 内部重新编码，因此**帧级准确**，不需要手动找关键帧。
- 缩放：`Effects(listOf(Presentation.createForShortSide(720, LAYOUT_SCALE_TO_FIT)))`。
- 两者同时存在时，按"先裁剪后缩放"顺序组合，避免对整段视频做缩放。

### R4. 进度与取消

- 进度：`Transformer.Listener.onProgress(progressState, progressPercentage)`；
  需在 `onCompleted`/`onError` 时结束。
- 取消：`Transformer.cancel()` 是异步的 —— 必须**等 `onCancelled` 回调后再删临时文件**，
  否则可能删到正在写的文件。

### R5. 输出与权限

- 私有目录 `files/videos/` + `FileProvider`（authority 后缀 `video`，路径单独一个 xml）。
- 存相册：`MediaStore.Video.Media` + `IS_PENDING=1` → 写入 → 置 0；API ≥ 29 才显示按钮。

### R6. 时间轴缩略图

- `MediaMetadataRetriever.getFrameAtTime(us, OPTION_CLOSEST_SYNC)` 抽 8–12 帧，
  在 `Dispatchers.IO` 上做，结果缓存；**不要**在主线程逐帧解码。

---

## Design Decisions

### D1. 档位与预估是纯函数

```kotlin
enum class ResolutionTier(val shortSide: Int) { P480(480), P720(720), P1080(1080) }
enum class BitrateTier { HIGH, MEDIUM, LOW }

data class CompressionPreset(val resolution: ResolutionTier, val bitrate: BitrateTier) {
    fun targetShortSide(sourceWidth: Int, sourceHeight: Int): Int   // 不放大
    fun videoBitrateKbps(sourceIs1080p: Boolean): Int               // 码率表
    fun estimatedBytes(durationMs: Long): Long                      // (v+a)/8 * 时长
}
```

全部可 JVM 单测，服务端无依赖。

### D2. 压缩器接口（测试接缝）

```kotlin
interface VideoCompressor {
    fun compress(request: VideoCompressRequest): Flow<VideoProgress>
    suspend fun cancel()
}

sealed interface VideoProgress {
    data class Running(val percent: Int) : VideoProgress
    data class Success(val path: String, val bytes: Long) : VideoProgress
    data class Failed(val reason: VideoFailure) : VideoProgress
    data object Cancelled : VideoProgress
}
```

ViewModel 只依赖这个接口；`Media3VideoCompressor` 是唯一实现，平台对象（`Transformer`）不出接口。

### D3. 状态机

```kotlin
data class VideoUiState(
    val video: SourceVideo? = null,
    val trim: TrimRange = TrimRange.Full,
    val resolution: ResolutionTier = ResolutionTier.P720,
    val bitrate: BitrateTier = BitrateTier.MEDIUM,
    val phase: Phase = Phase.Idle,      // Idle / Ready / Compressing(percent) / Done / Failed
    val outcome: VideoOutcome? = null,
) : UiState
```

按钮：`Idle/Ready → 【开始压缩】`；`Compressing → 【取消】`；`Done → 【重新压缩】`。

### D4. 编码器门槛

- 进入页面时查一次编码器目录：**完全没有**编码器 → 直接禁用压缩并提示；
- **只有软编** → 允许但**明确提示**「当前设备无硬件编码器，压缩时长会比较长」（不静默，见 spec FR-004 v1.15 修订）；
- 目标分辨率超出编码器能力 → 自动降档并在界面说明（例如 1080p 不支持 → 降到 720p）。

### D5. 取消与清理

- 协程取消 → 调 `Transformer.cancel()` → 等 `onCancelled` → 删 `.tmp`；
- 应用启动时清理一次 `files/videos/*.tmp`（防御性，处理进程被杀的情况）。

### D6. 页面归属

`:app` 新增 `FoolproofScreen`：`NavigationBar` 两个 tab（图片 / 视频），
`CompressionTabScreen`（图片 + 重复清理）与 `VideoTabScreen`（视频压缩）各自一个页面。
`:app` 只做布局与组合，无业务逻辑。

---

## 实施顺序（供 /speckit-tasks 拆解）

1. **探测**：建模块骨架 + `EncoderCatalogTest`，先打印设备真实编码能力（含模拟器 vs 真机差异）
2. **纯逻辑**：`CompressionPreset`、`TrimRange` + 单测（码率表、不放大、裁剪边界、预估公式）
3. **数据层**：`ContentResolverVideoReader`、`MediaCodecEncoderCatalog`、`Media3VideoCompressor`
4. **导出**：`MediaStoreVideoExporter` + FileProvider 配置
5. **ViewModel**：状态机、参数联动、进度、取消、错误分类
6. **UI**：档位选择、时间轴（VideoView + 抽帧）、进度条、结果区
7. **集成**：`:app` 加 tab、Koin 注册
8. **收尾**：权限红线检索、release 构建、真机验证（硬编、1080p、竖屏旋转）

## Complexity Tracking

无违反项，留空。

---

## 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 模拟器无硬件编码器 | 无法在模拟器验证真实性能 | 仪器化测试直接驱动压缩器验证管线；性能与画质只在真机验收 |
| 长视频压缩耗时长（>5 分钟） | 用户以为卡死 | 显示进度百分比 + 剩余时间估算；允许随时取消 |
| 部分机型 H.265 编码不可用 | 用户选了却失败 | 进入页面时探测并隐藏不可用选项 |
| 裁剪起点不在关键帧 | 首帧花屏 | Transformer 重新编码，帧级准确（已确认）；真机验证首帧 |
| 取消后残留文件 | 占空间 | `onCancelled` 后再删 + 启动时清理 `.tmp` |
| 存储不足 | 写一半失败 | 压缩前按预估体积校验剩余空间 |
| Transformer 与 `MediaMetadataRetriever` 同时读同一 Uri | 文件锁/解码器冲突 | 元信息在进入页面时读一次即释放；压缩期间不持有 retriever |
