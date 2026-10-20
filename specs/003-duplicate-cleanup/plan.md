# Implementation Plan: 重复照片清理（duplicate-cleanup）

**Branch**: `feature_foundation1` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-duplicate-cleanup/spec.md`

## Summary

新增 `:features:duplicate-cleanup`：扫描相册 → 计算 8×8 dHash（64 bit）→ 按汉明距离 ≤5 分组 →
分组网格展示并默认预选「除最大一张外」→ 二次确认后按 API 通道删除。

三处难点：**纯色图会让 dHash 失效**、**首次引入运行时权限**、**删除在 API 30 前后是两套机制**。

---

## Technical Context

**Language/Version**: Kotlin 2.4.21（AGP 9.4.1 内置），JVM Target 11

**Primary Dependencies**

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `:base` | 本工程 | MVI 基类、Koin、`LogUtil` |
| Compose BOM + material3 | 2026.09.00 | Section UI、分组网格、全屏预览 |
| `coil-compose` | 3.6.3 | 网格缩略图 |
| `androidx.activity:activity-compose` | 1.13.0 | 权限请求、SAF 选择、删除 PendingIntent |

**不新增任何第三方依赖**：哈希、分组、方差判定全部自己实现（纯 Kotlin + 平台 API）。

**Storage**: 只读 `MediaStore.Images`；不写任何自有数据

**Testing**: JUnit4 + `kotlinx-coroutines-test`；哈希/分组/预选/状态机全部可 JVM 单测

**Target Platform**: Android `minSdk 24` / `compileSdk 37` / `targetSdk 37`

**Constraints**:
- 只申请读取权限（§8）；删除不得申请写权限
- 绝不自动删除（§3.2）；预选规则见宪法 v1.10
- HEIC 在 API ≤ 27 必须跳过（§3.3）

---

## Constitution Check

| 宪法条款 | 检查点 | 结论 |
| --- | --- | --- |
| §1.2 不做什么 | 不联网；模块无网络依赖 | ✅ |
| §2 技术栈锁定 | 未引入表外库；哈希自研（纯 Kotlin） | ✅ |
| §3 模块结构 | 只依赖 `:base`，不依赖其他 feature | ✅ |
| §3.2 页面归属 | 只提供 `DuplicateSection` + `DuplicateViewModel`，位置在压缩区之下 | ✅ |
| §3.2 分组算法 | dHash + 汉明距离 ≤5 | ✅ |
| §3.2 删除通道 | API 30+ 回收站 / 24–29 SAF 不可恢复 | ✅ |
| §3.2 预选与确认 | 默认预选、二次确认、绝不自动删除（v1.10） | ✅ |
| §3.3 HEIC 门槛 | API ≤ 27 跳过并计数 | ✅ |
| §4 MVI | 继承 `BaseViewModel`，异常走 `onError` | ✅ |
| §5 注入 | ViewModel 与能力类经 Koin | ✅ |
| §6 日志 · 复用 | 用注入的 `LogUtil`；**重复逻辑必须下沉**（见 R4） | ✅（需先做一次小重构） |
| §8 权限 | 仅 `READ_MEDIA_IMAGES`(33+) / `READ_EXTERNAL_STORAGE`(≤32) | ✅ |
| §9 一票否决 | 无 feature 直连网络、无写权限、无 `:app` 业务逻辑 | ✅ |
| 质量门槛 | 哈希/分组/预选/状态机单测 | ✅ |

**结论：无违反项。** 唯一需要先做的前置动作见 R4（两个工具函数下沉 `:base`）。

---

## Project Structure

```text
settings.gradle.kts            # + include(":features:duplicate-cleanup")

base/
└── src/main/java/com/foolproof/base/util/
    ├── BitmapSampling.kt      # 从 :features:image-compression 迁移过来（R4）
    └── ByteFormat.kt          # 同上：字节数格式化

features/duplicate-cleanup/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml                  # 读取权限（含 maxSdkVersion 分段）
    │   └── java/com/foolproof/features/duplicates/
    │       ├── DuplicateSection.kt              # 对外 Composable
    │       ├── DuplicateViewModel.kt
    │       ├── DuplicateContract.kt             # UiState / Action / Effect
    │       ├── di/DuplicateModule.kt
    │       ├── domain/
    │       │   ├── PhotoItem.kt                 # id/名称/字节/宽高/哈希/是否低信息量
    │       │   ├── PhotoGroup.kt                # 成员、总字节、可释放字节
    │       │   ├── PerceptualHash.kt            # 纯函数：灰度 → dHash、汉明距离
    │       │   ├── DuplicateGrouper.kt          # 纯函数：分组 + 预选规则
    │       │   ├── PhotoScanner.kt              # 接口（扫描进度流）
    │       │   └── PhotoDeleter.kt              # 接口（删除通道）
    │       ├── data/
    │       │   ├── MediaStorePhotoScanner.kt
    │       │   ├── ThumbnailPixelSource.kt      # loadThumbnail(29+) / 采样解码(≤28)
    │       │   └── MediaStorePhotoDeleter.kt    # PendingIntent(30+) / SAF(≤29)
    │       └── ui/
    │           ├── DuplicateGroupGrid.kt        # LazyVerticalGrid + 跨列组头
    │           └── PhotoPreviewDialog.kt        # 全屏预览
    └── test/java/com/foolproof/features/duplicates/
        ├── PerceptualHashTest.kt
        ├── DuplicateGrouperTest.kt
        └── DuplicateViewModelTest.kt

app/
├── build.gradle.kts                            # + implementation(project(":features:duplicate-cleanup"))
└── src/main/java/com/foolproof/ui/compression/CompressionTabScreen.kt
    # Column { CompressionSection(weight 1f); DuplicateSection() }
```

**Structure Decision**: 沿用平铺 feature 结构；UI 拆成「网格」与「预览」两个文件，避免 `DuplicateSection`
膨胀成第二个 200 行的巨型 Composable。

---

## Research（动手前要确认的事实）

### R1. dHash 的实现与失效场景

- 标准 dHash：灰度化 → 缩放到 **9×8** → 比较每行相邻像素 `left > right` → 64 bit。
- **纯色图**：所有像素相等 → 64 bit 全 0 → 与任何纯色图距离为 0，会被误判为同组。
  实测过这类误判在手机相册里很常见（截图、白底商品图、纯黑照片）。
- **保护策略（FR-005）**：计算灰度方差；方差低于阈值（例如 σ < 8）时标记 `lowInformation`，
  这类图**只与「宽高比接近且字节数差异 < 2%」的同组候选合并**，否则自成一组。

### R2. 缩略图获取是性能关键

- 1000 张图逐张 `BitmapFactory.decodeFile` + 降采样会很慢。
- API ≥ 29 用 `ContentResolver.loadThumbnail(uri, Size(64,64), null)`，走系统缩略图缓存，快一个数量级；
  API ≤ 28 退回「采样解码到 64px」，并复用 `BitmapSampling`。
- 两者都返回 `IntArray`（ARGB），便于纯函数哈希。

### R3. 权限映射

| API | 权限 | 说明 |
| --- | --- | --- |
| ≥ 33 | `READ_MEDIA_IMAGES` | Android 13 起的分段媒体权限 |
| 24–32 | `READ_EXTERNAL_STORAGE`（`maxSdkVersion=32`） | 旧版整卡读权限 |

申请前先 `checkSelfPermission`，已授予则直接扫描；被拒绝一次提示原因，永久拒绝（`shouldShowRequestPermissionRationale` 为 false 且未授予）引导去设置。

### R4. 两个工具函数已经重复，必须下沉（§6 第 1 条）

`BitmapSampling`（采样计算）与字节格式化当前在 `:features:image-compression`，本模块都要用。
宪法 §6 规定「同一逻辑出现第 2 次必须抽公共」，且 feature 之间禁止互引 → **迁移到 `:base/util`**，
同步更新 `:features:image-compression` 的引用与其单测。这是一次小重构，放在任务最前面。

### R5. 删除是两套机制

- **API ≥ 30**：`MediaStore.createDeleteRequest(resolver, uris)` 返回 `PendingIntent`，
  用 `ActivityResultContracts.StartIntentSenderForResult` 拉起系统确认框，用户确认后系统删除，
  **进回收站可恢复**；返回的 `resultCode` 只表示"请求已处理"，不保证每张都删成功。
- **API ≤ 29**：无回收站。只能走 SAF：`ACTION_OPEN_DOCUMENT_TREE` 让用户选目录 →
  `DocumentsContract.deleteDocument()`。**删除不可恢复**，UI 必须明示。
  注意：SAF 删除在部分 ROM / 目录上可能不被支持，需要按 `Document.COLUMN_FLAGS` 判断
  `FLAG_SUPPORTS_DELETE`，失败时明确报错而不是静默。

### R6. 分组算法与规模

- 流式贪心：每张图与「已有组的代表哈希」比较，命中入组、否则新建组。复杂度 O(n·k)，
  k 为组数；1000 张典型场景 k < 50，完全够用（无需并查集）。
- 阈值边界必须有测试：距离 5 入组、6 不入组。
- **镜像行为（原假设错误，已由测试纠正）**：最初以为 dHash 对水平镜像不敏感、会误判为重复；
  实测相反——镜像会反转每行的比较方向，使哈希近似按位取反，距离接近 64。
  因此镜像副本是**漏判**而非误判，不会误删用户图片，v1 接受。

---

## Design Decisions

### D1. 对外契约

```kotlin
@Composable
fun DuplicateSection(modifier: Modifier = Modifier)
```

内部 `koinViewModel()`；不接收 `NavController`、不自己开页面。

### D2. 纯函数承载全部判定逻辑（可测性核心）

```kotlin
object PerceptualHash {
    fun grayscale(pixels: IntArray, width: Int, height: Int): IntArray
    fun dHash(gray: IntArray): Long          // 64 bit
    fun hamming(a: Long, b: Long): Int
    fun isLowInformation(gray: IntArray): Boolean   // 方差阈值
}

object DuplicateGrouper {
    fun group(items: List<PhotoItem>, threshold: Int = 5): List<PhotoGroup>
    fun defaultSelection(group: PhotoGroup): Set<String>   // 除最大一张外
}
```

全部纯 Kotlin，不碰 Android API → JVM 单测直接覆盖 SC-002 / SC-003。

### D3. 扫描接口（可取消 + 有进度）

```kotlin
interface PhotoScanner {
    fun scan(): Flow<ScanEvent>    // Started(total) / Progress(done,total) / Item(photo) / Finished
}
```

- 实现内部用 `Dispatchers.IO`，每处理 N 张 `yield()`；
- ViewModel 用 `launchJob` 收集，`Cancel` 时取消 Job（复用 base 的 `launchJob`）。

### D4. 删除接口（两套通道，一个门面）

```kotlin
interface PhotoDeleter {
    fun channel(): DeleteChannel              // SYSTEM_REQUEST / SAF / NONE
    suspend fun delete(uris: List<String>): DeleteReport
    fun setSafTree(uri: String?)              // API ≤ 29 用户授权后写入
}
```

- `SYSTEM_REQUEST`：返回 PendingIntent 由 UI 拉起（ViewModel 通过 Effect 把 PendingIntent 交给 UI，
  避免 ViewModel 持有 Activity）；
- 删除后重新查询这些 uri 是否还存在 → 得到真实的成功/失败清单。

### D5. 状态机

```kotlin
data class DuplicateUiState(
    val scan: ScanState = ScanState.Idle,
    val groups: List<PhotoGroup> = emptyList(),
    val selected: Set<String> = emptySet(),
    val deleting: Boolean = false,
    val permission: PermissionState = PermissionState.Unknown,
    val lastReport: DeleteReport? = null,
) : UiState
```

按钮文案：`Idle → 【查询相同图片】`；`Scanning → 【取消】`；`Done → 【重新查询】`。

### D6. 预选规则落在 grouper，不在 UI

默认勾选「除最大一张以外的其余项」由 `DuplicateGrouper.defaultSelection` 计算，
UI 只渲染；这样规则可单测，且 §3.2 v1.10 的口径只有一处实现。

---

## 实施顺序（供 /speckit-tasks 拆解）

1. **前置重构**：`BitmapSampling` / 字节格式化迁移到 `:base/util`，更新 `:features:image-compression` 引用与测试
2. **骨架**：settings 注册、模块 build 脚本、Manifest 权限、空 Section，`:features:duplicate-cleanup:assembleDebug`
3. **纯逻辑 + 单测**：`PerceptualHash`、`DuplicateGrouper`（阈值边界、低信息量保护、预选规则）
4. **数据层**：`ThumbnailPixelSource`（loadThumbnail / 采样解码）、`MediaStorePhotoScanner`
5. **ViewModel + 单测**：扫描状态机、取消、预选、删除汇总
6. **UI**：分组网格 + 组头 + 勾选、全屏预览、权限请求、删除确认框
7. **集成**：`:app` 依赖、`CompressionTabScreen` 追加 Section、Koin 注册
8. **收尾**：grep 权限红线、release 构建、真机验证（含纯色图与同图不同压缩）

## Complexity Tracking

无违反项，留空。

---

## 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 纯色图误判 | 用户被建议删除不同内容的图 | 方差保护 + 尺寸/宽高比双重条件；测试集覆盖纯色与近似纯色 |
| SAF 删除在部分 ROM 不可用（API ≤ 29） | 低版本删不掉 | 先判断 `FLAG_SUPPORTS_DELETE`，不可用则禁用并说明；文档写明 |
| 大相册扫描耗时 | 用户以为卡死 | 分批 + 进度 + 可取消；优先 `loadThumbnail` |
| `loadThumbnail` 在部分机型对 HEIC 抛异常 | 扫描中断 | 逐张 try/catch，失败计入跳过数 |
| 删除请求返回成功但实际未删 | 报告失真 | 删除后重新查询 uri 是否存在，以查询结果为准 |
| 首次引入权限被用户拒绝后体验断裂 | 功能不可用 | 三态处理（未请求/拒绝/永久拒绝）+ 跳设置入口 |
