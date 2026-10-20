# Implementation Plan: 图片压缩（image-compression）

**Branch**: `feature_foundation1` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-image-compression/spec.md`

## Summary

新增 Android Library `:features:image-compression`，对外只暴露一个 Compose `CompressionSection` 与一个
`CompressionViewModel`；内部完成「选图 → 参数 → 串行压缩 → 体积对比 → 分享/保存」的完整链路。

技术关键点有三个：**Compressor 停更且传递依赖很老**、**HEIC 的版本门槛**、**免权限的选图与导出**。

---

## Technical Context

**Language/Version**: Kotlin 2.4.21（AGP 9.4.1 内置），JVM Target 11

**Primary Dependencies**（版本已核对 Maven Central / Google Maven）

| 依赖 | 版本 | 用途 | 核对结果 |
| --- | --- | --- | --- |
| `:base` | 本工程 | MVI 基类、Koin、`LogUtil` | 已有，33 个测试 |
| `id.zelory:compressor` | **3.0.1** | 唯一压缩实现（宪法 §2） | 最新版，2021-03 停更 |
| `io.coil-kt.coil3:coil-compose` | **3.6.3** | 列表缩略图（宪法 §2 已登记） | stdlib 2.4.10 ✅ |
| `androidx.activity:activity-compose` | 1.13.0 | Photo Picker（`PickMultipleVisualMedia`） | 工程已有 |
| Compose BOM | 2026.09.00 | Section UI | 工程已有 |
| `androidx.core:core-ktx` | 1.19.1 | `FileProvider` | 工程已有 |

**Storage**: 应用私有目录 `files/compressed/`（无权限）；API ≥ 29 可另存 MediaStore

**Testing**: JUnit4 + `kotlinx-coroutines-test`（JVM 单测），不引入 Robolectric

**Target Platform**: Android `minSdk 24` / `compileSdk 37` / `targetSdk 37`

**Project Type**: 多模块 Android 应用中的一个 feature 模块

**Constraints**:
- 不申请任何存储权限（读走 Photo Picker，写走私有目录）
- 压缩必须用 Compressor，不得自研或替换
- 不引 WorkManager / Room / 网络库（§7 YAGNI）

---

## Constitution Check

| 宪法条款 | 检查点 | 结论 |
| --- | --- | --- |
| §1.2 不做什么 | 不联网、不上传；本模块无网络依赖 | ✅ |
| §2 技术栈锁定 | Compressor 压缩、Coil 3 加载、Compose UI、Koin 注入；未引入表外库 | ✅ |
| §3 模块结构 | `:features:image-compression` 仅依赖 `:base`，不依赖其他 feature | ✅ |
| §3.2 页面归属 | 只暴露 `CompressionSection` + `ViewModel`，整页由 `:app` 组装；顺序为压缩在上 | ✅ |
| §3.3 HEIC 门槛 | API ≥ 28 处理，24–27 标记 `Unsupported` | ✅ |
| §4 MVI | 继承 `BaseViewModel`，状态 `StateFlow`、副作用 `Channel`、异常走 `onError` | ✅ |
| §5 注入 | ViewModel 用 `viewModel { }` 注册；依赖全部注入 | ✅ |
| §6 日志 | 用注入的 `LogUtil`，禁 `android.util.Log` | ✅ |
| §7 YAGNI | 不做目标体积压缩、不做一键导出、不引 WorkManager | ✅ |
| §8 权限 | 零权限；不申请 `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` | ✅ |
| 质量门槛 | 参数映射、HEIC 门槛、状态机、失败隔离均有单测 | ✅ |

**结论：无违反项。** 唯一需要说明的是 Compressor 停更（见风险与 R1），属宪法已锁定项，不构成违反。

---

## Project Structure

### Documentation (this feature)

```text
specs/002-image-compression/
├── spec.md          # 已完成
├── plan.md          # 本文件
└── tasks.md         # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
settings.gradle.kts              # + include(":features:image-compression")
gradle/libs.versions.toml        # + compressor / coil 版本与库条目

features/image-compression/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml                 # 仅 FileProvider 声明
    │   ├── res/xml/image_compression_paths.xml # FileProvider 路径
    │   └── java/com/foolproof/features/compression/
    │       ├── CompressionSection.kt           # 对外 Composable
    │       ├── CompressionViewModel.kt
    │       ├── CompressionContract.kt          # UiState / UiEffect / UiAction
    │       ├── di/ImageCompressionModule.kt    # Koin 声明
    │       ├── domain/
    │       │   ├── SourceImage.kt              # 不持有 android.net.Uri，用 String id
    │       │   ├── CompressionOption.kt        # 格式 + 质量
    │       │   ├── CompressionOutcome.kt       # 单张结果 + 状态
    │       │   ├── ImageCompressor.kt          # 接口（测试接缝）
    │       │   └── ResultExporter.kt           # 接口（分享 / 存相册）
    │       ├── data/
    │       │   ├── CompressorImageCompressor.kt
    │       │   ├── MediaStoreResultExporter.kt
    │       │   └── ImageMetadataReader.kt
    │       └── platform/
    │           ├── PlatformCapabilities.kt     # sdkInt 注入 + HEIC 支持判定
    │           └── BitmapSampling.kt           # inSampleSize 计算
    └── test/java/com/foolproof/features/compression/
        ├── CompressionViewModelTest.kt
        ├── CompressionOptionTest.kt
        ├── PlatformCapabilitiesTest.kt
        └── BitmapSamplingTest.kt

app/
├── build.gradle.kts                            # + implementation(project(":features:image-compression"))
└── src/main/java/com/foolproof/
    ├── FoolproofApp.kt                         # koinModules 追加 feature 模块
    └── ui/compression/CompressionTabScreen.kt  # 只做布局组装
```

**Structure Decision**: 沿用宪法 §3 的平铺结构，feature 模块放在 `features/` 下，
与 `specs/` 的编号目录无耦合（模块名固定为 `:features:image-compression`）。

---

## Research（设计前确认的事实）

### R1. Compressor 的现状与风险

- 坐标 `id.zelory:compressor:3.0.1`，Maven Central 最新版，**2021-03 之后停更**。
- 传递依赖：`kotlin-stdlib-jdk7:1.3.61` + `kotlinx-coroutines-core:1.3.3`（都很老）。
  工程显式声明 `kotlinx-coroutines-android:1.11.0`，Gradle 冲突解析会取高版本，行为可控。
- 它的 `compressToFile(File)` 是**阻塞 API**，必须在 `Dispatchers.IO` 调用；
  它另有一个基于 Flow 的 API，但我们不用（避免与 1.3.3 时代的 Flow 语义纠缠）。

**缓解**：所有参数映射收敛到一个 `CompressionOption.toCompressFormat()` + 一个 `CompressRequest`，
用单测把「质量、格式、SDK 分支」锁住。将来它若行为变化，测试先红，而不是线上才发现。

**真机测试补充的三个实测结论（2026-10-21，API 34 模拟器）**：

1. `DefaultConstraint` 默认把图缩到 **612×816 / 质量 80** —— 必须显式 `resolution()` 覆盖，否则"压缩"变"缩图"；
2. 传 `Bitmap.CompressFormat.WEBP_LOSSY`（API 30+ 常量）会失败：Compressor 3.0.1 只认 `JPEG/PNG/WEBP`。
   因此统一用 `WEBP`，`PlatformCapabilities.useLossyWebp` 已删除；
3. 输入文件**没有扩展名**且输出扩展名变化时，它会自算一个不可写的落点
   （实测为 `/data/user/0/<包名>.<ext>`）→ `EACCES`。修法：临时输入文件带上目标扩展名，
   并在返回路径与目标不一致时做一次兜底搬移。

### R2. Photo Picker 的回退不是我们的事

`ActivityResultContracts.PickMultipleVisualMedia` 在无系统照片选择器的设备上，
**androidx 内部已回退到 `ACTION_OPEN_DOCUMENT`**，我们不需要自己写分支。
因此 spec 里「选择器不可用则回退」这条，实现上是**零代码**——只需记录这个事实，避免有人画蛇添足。

### R3. 免权限的写入路径

- **API ≥ 29**：向 `MediaStore.Images` `insert` 自己的条目无需任何权限（Scoped Storage 允许）。
  用 `IS_PENDING` 流程：先 insert 拿到 uri → 写入 → 清除 `IS_PENDING`。
- **API ≤ 28**：写入相册必须 `WRITE_EXTERNAL_STORAGE`（宪法 §8 禁止）→ 只提供私有目录 + 分享。
- **分享**：`FileProvider` + `Intent.ACTION_SEND`，全版本可用。

### R4. 大图与 OOM

`BitmapFactory.Options.inBounds` 先读尺寸，再按 `inSampleSize`（2 的幂）降采样；
阈值取 spec 的「长边 > 8192px 或 > 40MP」。`inSampleSize` 计算抽成纯函数，单测覆盖边界（1、非 2 的幂、极端长宽比）。

---

## Design Decisions

### D1. 对外契约只有一个 Section + 一个 ViewModel

```kotlin
// 对外唯一入口，由 :app 放进同页的上半部分
@Composable
fun CompressionSection(modifier: Modifier = Modifier)
```

- Section 内部通过 `koinViewModel()` 取 `CompressionViewModel`（Koin 的 `viewModel { }` 注册）；
- 不暴露整页、不暴露 NavHost、不接收 `NavController`（§3.2）。

### D2. 三个测试接缝（最小化，不是预埋抽象）

| 接缝 | 为什么必须 |
| --- | --- |
| `ImageCompressor` 接口 | Compressor 需要真实 `Context`，JVM 单测跑不了；接口让 ViewModel 可测 |
| `PlatformCapabilities.sdkInt` 可注入 | HEIC 门槛与 WebP 常量分支必须能测两个 SDK 分支 |
| `BitmapSampling.calculateInSampleSize()` 纯函数 | 采样算法是最容易写错的地方，必须单测 |

其余（导出、元数据读取）不抽接口，直接实现——它们只在真机路径上跑，抽出来也没有测试价值（§7）。

### D3. 压缩管线

```
SourceImage(id, sizeBytes, mime, width, height)
   → ImageMetadataReader（bounds + 大小 + mime）
   → BitmapSampling（需要时先采样）
   → ImageCompressor.compress(id, option)   [Dispatchers.IO，阻塞]
   → CompressionOutcome(originalBytes, compressedBytes, status)
```

- 串行 for 循环，**每张之间 `yield()`**，让取消能及时生效；
- 单张异常在这里被捕获 → 该张 `Failed(reason)`，循环继续（FR-014）；
- 全程不联网、不写日志到外部。

### D4. 状态机

```kotlin
data class CompressionUiState(
    val images: List<SourceImage> = emptyList(),
    val option: CompressionOption = CompressionOption.Default,   // JPEG / 80
    val outcomes: Map<String, CompressionOutcome> = emptyMap(),
    val isRunning: Boolean = false,
    val estimateBytes: Long? = null,
) : UiState

sealed interface CompressionEffect : UiEffect {
    data class Share(val id: String) : CompressionEffect
    data class Toast(val kind: Kind) : CompressionEffect
    data object OpenPicker : CompressionEffect
}
```

- UI 只发 `dispatch(Action.*)`；打开选择器这种平台交互通过 `Effect` 回传给 UI（ViewModel 不持有 `Activity`）；
- 取消：持有 `Job` 引用，`Cancel` 时 `cancel()` 并把运行中的那张置回 `Pending`。

### D5. 采样预览（US3）

`OptionChanged` 后 `debounce(300)` 再对**第一张**做一次压缩，结果写 `estimateBytes`；
新参数到来时取消上一次（`collectLatest` 语义）。列表为空则不发任务。

### D6. 导出

```kotlin
interface ResultExporter {
    suspend fun share(id: String): Result<Unit>            // FileProvider + ACTION_SEND
    suspend fun saveToGallery(id: String): Result<Unit>    // 仅 API ≥ 29
    fun canSaveToGallery(): Boolean                        // API ≥ 29
}
```

- 输出文件名：`{原名去扩展}_q{质量}.{jpg|webp}`，重名追加 `_1`、`_2`；
- 不复制 EXIF（Compressor 重编码本身不保留；我们也不主动写回）→ FR-017。

### D7. 组装与 Koin

```kotlin
// :features:image-compression
val imageCompressionModule = module {
    single<ImageCompressor> { CompressorImageCompressor(get()) }
    single { ImageMetadataReader(get()) }
    single<ResultExporter> { MediaStoreResultExporter(get(), get()) }
    viewModel { CompressionViewModel(get(), get(), get()) }
}

// :app
override fun koinModules() = super.koinModules() + imageCompressionModule
```

`:app` 的 `CompressionTabScreen` 只负责 `Column { CompressionSection(); /* 预留重复清理 */ }`，
不写任何压缩逻辑（宪法 §9 v1.4 的否决项）。

---

## 实施顺序（供 /speckit-tasks 拆解）

1. **骨架**：settings 注册、版本目录加 compressor/coil、模块 build 脚本、空 Manifest、空 Section，`:features:image-compression:assembleDebug` 通过
2. **纯逻辑 + 单测**：`CompressionOption` 映射、`PlatformCapabilities`（HEIC 门槛）、`BitmapSampling`（采样计算）
3. **数据层**：`ImageMetadataReader`、`CompressorImageCompressor`、`MediaStoreResultExporter`（含 FileProvider 配置）
4. **ViewModel + 单测**：状态机、串行与失败隔离、取消、采样预览防抖
5. **UI**：`CompressionSection`（列表、参数区、进度、结果行）+ Preview
6. **集成**：`:app` 依赖、`CompressionTabScreen`、Koin 追加、`:app:assembleDebug`
7. **收尾**：`WRITE_EXTERNAL_STORAGE` grep、`android.util.Log` grep、release 构建、README 更新

## Complexity Tracking

无违反项，留空。

---

## 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| Compressor 停更、传递依赖过老 | 未来与新 AGP/Kotlin 不兼容 | 版本锁定 + 参数映射单测；`compressToFile` 只在 IO 线程调用；已记录替代评估入口（宪法 §2 变更流程） |
| Photo Picker 在无 Play Services 的国产 ROM 上表现不一 | 选图入口失效 | 依赖 androidx 内部回退；真机冒烟覆盖一台国产 ROM |
| 大图 OOM | 崩溃 | 先 bounds + 采样；串行处理；SC-003 作为验收指标 |
| FileProvider authority 与其他模块冲突 | 安装期 manifest 合并失败 | authority 用 `${applicationId}.imagecompression` 保证唯一 |
| MediaStore `RELATIVE_PATH` 在部分 OEM 行为差异 | 保存到相册落错目录 | 保存失败时回退到「仅分享」并提示 |
| Compose 在 feature 模块的 BOM 对齐 | 版本不一致导致运行时崩溃 | 与 `:app` 共用同一 BOM 与同一 Compose 编译器插件 |
