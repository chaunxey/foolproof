# Tasks: 视频压缩（video-compression）

**Input**: Design documents from `specs/004-video-compression/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)

**Tests**: 必须写（spec FR-018 + 宪法质量门槛）。档位/预估/裁剪/状态机是纯逻辑，JVM 单测覆盖；
编码器能力与真实转码只能仪器化/真机验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可与同阶段其它 [P] 任务并行（不同文件、无依赖）
- 路径相对仓库根目录

---

## Phase 1: Setup（骨架 + 编码器能力探测）

**目的**：先建模块并**打印设备真实编码能力**，在写业务代码前知道这台机器能不能编 1080p

- [x] T001 在 `settings.gradle.kts` 注册 `include(":features:video-compression")`
- [x] T002 创建 `features/video-compression/build.gradle.kts`：`android.library` + `kotlin.compose`、
  `namespace = com.foolproof.features.video`、`compileSdk { release(37) }`、`minSdk 24`、Java 11、
  `buildFeatures { compose = true; buildConfig = true }`；依赖 `:base`、Compose BOM/material3、
  `activity-compose`、`koin-compose-viewmodel`、**`media3-transformer:1.11.1`**、**`media3-effect:1.11.1`**；
  测试依赖 `junit`、`kotlinx-coroutines-test`、`koin-test`、`androidx.test:runner`、`androidx.test.ext:junit`
- [x] T003 在 `gradle/libs.versions.toml` 增加 `media3 = "1.11.1"` 与两个库条目
- [x] T004 [P] 创建占位 `.../video/VideoCompressionSection.kt`（空 Composable）
- [x] T005 创建 `src/androidTest/.../EncoderCatalogTest.kt`：用 `MediaCodecList` 列出所有
  `video/avc` / `video/hevc` 编码器的名称、`isHardwareAccelerated`、支持的最大分辨率与码率范围，
  用 `Log.i` 打印并在断言中要求「至少存在一个 avc 编码器」
- [x] T006 运行 `./gradlew :features:video-compression:assembleDebug` 通过（**硬门槛**）
- [x] T007 运行 `EncoderCatalogTest`，把**模拟器实测的编码能力**写入 `plan.md` 的 R2 小节
  （**硬门槛**：这决定档位上限，结果必须先落地再往下做）

---

## Phase 2: Foundational（纯逻辑）

- [x] T008 [P] 创建 `.../domain/SourceVideo.kt`（id/名称/字节/时长/宽高/帧率/有无音轨）
- [x] T009 [P] 创建 `.../domain/CompressionPreset.kt`：`ResolutionTier`(480/720/1080)、
  `BitrateTier`(HIGH/MEDIUM/LOW)、码率表、`targetShortSide`（**不放大**）、`estimatedBytes`
- [x] T010 [P] 创建 `.../domain/TrimRange.kt`：起点/终点校验（最小区段 1s、不越界）+ 时长派生
- [x] T011 [P] 创建 `src/test/.../CompressionPresetTest.kt`：三档码率表、源更小时不放大、
  预估公式（含音频 128k）、1080p 源压到 720p 的体积关系
- [x] T012 [P] 创建 `src/test/.../TrimRangeTest.kt`：最小区段、越界裁剪、整段等价于不裁剪
- [x] T013 运行 `./gradlew :features:video-compression:testDebugUnitTest` 通过

---

## Phase 3: US1 — 压缩主链路 (P1)

- [x] T014 [US1] 创建 `.../domain/VideoCompressor.kt`：接口 + `VideoProgress`
  （Running/Success/Failed/Cancelled）+ `VideoFailure` 分类
- [x] T015 [US1] 创建 `.../domain/EncoderCatalog.kt`：接口 + `EncoderCapability`
  （mime / isHardware / maxWidth / maxHeight / maxBitrate）
- [x] T016 [US1] 创建 `.../data/MediaCodecEncoderCatalog.kt`：`MediaCodecList(ALL_CODECS)` 查询，
  `VideoCapabilities` 取分辨率与码率上限
- [x] T017 [US1] 创建 `.../data/ContentResolverVideoReader.kt`：`MediaMetadataRetriever` 读时长/宽高/
  帧率/有无音轨；**读完立即 release**（避免与转码抢解码器）
- [x] T018 [US1] 创建 `.../data/Media3VideoCompressor.kt`：`Transformer` + `Presentation`（缩放）+
  `ClippingConfiguration`（裁剪）+ `VideoEncoderSettings.setBitrate`；
  `compress()` 返回 `Flow<VideoProgress>`；`cancel()` 等 `onCancelled` 后再删临时文件
- [x] T019 [US1] 创建 `.../VideoCompressionContract.kt`：UiState/Action/Effect + `Phase`
  （Idle/Ready/Compressing/Done/Failed）
- [x] T020 [US1] 创建 `.../VideoCompressionViewModel.kt`：继承 `BaseViewModel`；选视频→读元信息→
  编码器门槛检查→压缩→进度→完成/失败；取消走协程 + 压缩器 cancel
- [x] T021 [US1] 创建 `src/test/.../VideoCompressionViewModelTest.kt`：状态流转、进度更新、
  取消、失败分类、重复点击防护
- [x] T022 [US1] 运行测试 + `assembleDebug` 通过

---

## Phase 4: US1 收尾 — 导出与分享 (P1)

- [x] T023 [US1] 创建 `features/video-compression/src/main/res/xml/video_paths.xml` +
  manifest 中声明 FileProvider（authority `${applicationId}.video`）
- [x] T024 [US1] 创建 `.../data/MediaStoreVideoExporter.kt`：`IS_PENDING` 写入 → 置 0；
  API < 29 时 `canSaveToGallery() = false`
- [x] T025 [US1] ViewModel 接入分享/存相册动作 + 成功/失败提示（复用图片压缩的交互口径）
- [x] T026 单测覆盖：`canSaveToGallery` 的 API 分支、导出失败映射

---

## Phase 5: US2 — 裁剪时长 (P1)

- [x] T027 [US2] 创建 `.../ui/TrimTimeline.kt`：平台 `VideoView` 预览 + `MediaMetadataRetriever`
  抽 8–12 帧做缩略图条 + 双滑块（起止）+ 时间标签（mm:ss）
- [x] T028 [US2] ViewModel：`TrimChanged` 动作 → 校验（最小区段/越界）→ 更新状态与预估
- [x] T029 [US2] 单测：裁剪后预估体积按新时长计算；起止超界被夹紧；整段与不裁剪等价
- [x] T030 [US2] `Media3VideoCompressor` 接入 `ClippingConfiguration`（起止 ms）

---

## Phase 6: US3 — 档位与预估 (P2)

- [x] T031 [US3] 创建 `.../ui/PresetSelector.kt`：分辨率三档 + 码率三档（FilterChip），
  显示目标分辨率与目标码率；不可用档位置灰并说明
- [x] T032 [US3] 界面显示预估体积与「裁剪后时长」；参数变化实时联动
- [x] T033 [US3] 编码器能力不足时自动降档并在界面说明（如 1080p 不支持 → 降到 720p）

---

## Phase 7: 集成

- [x] T034 创建 `.../di/VideoModule.kt`：`viewModel { }` + `VideoCompressor` / `EncoderCatalog` /
  `VideoReader` / `VideoExporter` 的 `single`
- [x] T035 在 `app/build.gradle.kts` 增加 `implementation(project(":features:video-compression"))`
- [x] T036 在 `FoolproofApp.kt` 的 `koinModules()` 追加 `videoCompressionModule`
- [x] T037 在 `:app` 新建 `ui/FoolproofScreen.kt`：`NavigationBar` 两个 tab（图片 / 视频），
  `compression` → `CompressionTabScreen`，`video` → `VideoTabScreen`（内部只放 `VideoCompressionSection`）；
  `MainActivity` 改为使用 `FoolproofScreen`（**`:app` 不写业务逻辑**）
- [x] T038 运行 `./gradlew :app:assembleDebug` 通过

---

## Phase 8: Polish

- [x] T039 全仓检索本模块 `WRITE_EXTERNAL_STORAGE`（应为 0，SC-006）
- [x] T040 全仓检索本模块 `android.util.Log` / `println(`（应为 0）
- [x] T041 应用启动时清理 `files/videos/*.tmp`（防御进程被杀）
- [x] T042 运行 `./gradlew :app:assembleRelease` 通过
- [x] T043 更新 `README.md`：模块规划、视频压缩小节、媒体栈说明
- [x] T044 **真机验证（vivo V2602A / API 37，已完成）**：
  SC-001 ✅ 44.1MB → 11.9MB（27%）；SC-002 ✅ 预估 11.0MB / 实际 11.9MB（+8.2%，35 秒素材）；
  SC-005 ✅ 存相册 `Movies/Foolproof/`，时长 7191/6309ms 正确；SC-007 ✅ 输出 orientation=90，显示竖屏；
  硬编识别 ✅ 未出现软编提示（`c2.mtk.avc.encoder`）；性能：35 秒 1080p→720p 转码 4.6 秒（约 7.6× 实时）。
  SC-003 仅验证机制（裁剪后 11.9MB → 2.4MB，输出 6309ms 与界面 00:06 一致）；**±0.2 秒的精确断言未做**，
  因为界面时间标签只显示到秒，无法作为毫秒级对照 → 见 T046
- [x] T045 **模拟器验证**：软件编码器路径能跑通管线（不计性能），确认取消后无残留（SC-004）
- [x] T046 **仪器化测试：裁剪精度（SC-003）**：在 androidTest 里用 screenrecord 生成素材，调用 Media3VideoCompressor 以 start=5000/end=10000 压缩，再用 MediaMetadataRetriever 断言输出时长 5000ms ±200ms

---

## Dependencies

```text
Phase 1 (T001–T007)  ← T007 是信息门槛：先知道设备能编什么
   └─→ Phase 2 (T008–T013)  ← 纯逻辑，可并行
          ├─→ Phase 3 (T014–T022)  主链路
          │      ├─→ Phase 4 (T023–T026) 导出
          │      └─→ Phase 5 (T027–T030) 裁剪
          │             └─→ Phase 6 (T031–T033) 档位展示
          └─→ Phase 7 (T034–T038)  ← 依赖 Phase 3~6
                 └─→ Phase 8 (T039–T045)
```

**关键约束**：

- **T006 / T007 是门槛**：模块必须能编译，且设备编码能力必须先测出来
- T018（压缩器）依赖 T015/T016（编码器能力）与 T009（档位）
- T030（裁剪进转码）依赖 T018 与 T027

## 并行机会

| 阶段 | 可并行 | 说明 |
| --- | --- | --- |
| Phase 2 | T008～T012 | 五个独立文件 |
| Phase 4 / 5 | 互不依赖 | 都以 Phase 3 的压缩器与 VM 为基础 |
| Phase 8 | T039 / T040 / T043 | 纯检索与文档 |

## MVP 范围

**最小可演示切片 = Phase 1 + 2 + 3 + 4（T001–T026）**：能选视频、能压缩、能分享/存相册，
但还不能裁剪。裁剪（Phase 5）与档位展示（Phase 6）是紧随其后的增量。

## 需求覆盖自检

| 需求 | 覆盖任务 |
| --- | --- |
| FR-001 / FR-002（模块与页面归属） | T001、T002、T004、T037 |
| FR-003（Media3，不引 FFmpeg） | T002、T003、T018 |
| FR-004（必须硬编，不静默降级） | T005、T016、T020、T033 |
| FR-005（MP4 + H.264/AAC） | T018 |
| FR-006 / FR-007（档位、不放大、30fps 上限） | T009、T011、T018、T031 |
| FR-008（预估体积） | T009、T011、T032 |
| FR-009 / FR-010（裁剪与联动） | T010、T027、T028、T029、T030 |
| FR-011（Photo Picker 零权限） | T020、T037 |
| FR-012 / FR-013（私有目录、存相册 ≥29） | T023、T024、T025 |
| FR-014（取消清理） | T018、T041 |
| FR-015 / FR-017（进度、可取消、防重复点击） | T020、T021 |
| FR-016（错误分类） | T014、T020、T021 |
| FR-018 / FR-019（单测、基类、日志） | T011、T012、T021、T026、T029 |
| SC-001 / SC-002 | T044 |
| SC-003 | T030、T044 |
| SC-004 | T045 |
| SC-005 | T024、T044 |
| SC-006 | T039 |
| SC-007 | T044 |

---

## Phase 9: Convergence

> 由 `/speckit-converge` 于 2026-10-21 追加：核对 spec/plan/tasks 与当前代码后发现的差距。
> 完成这些任务后，本 feature 的规格才算真正闭合。

- [x] T047 按 API 隐藏【存相册】：现状 `VideoCompressionSection.kt` 无条件显示，API ≤ 28 点击只会报错；
  对齐图片压缩模块的做法（`UiState.saveSupported` + `if (saveSupported)`）per FR-013 (contradicts)
- [x] T048 解决 FR-004 与 plan D4 的冲突：spec 要求「无硬件编码器 MUST 明确报错」，
  而实现（plan D4）在只有软编时仅提示、仍允许压缩。二选一并同步文档：
  严格实现需把 `!isHardware` 直接置为 `Failed(NO_ENCODER)`，并给模拟器留一条仅测试用的直连入口；
  若保留现状，则必须修订 spec FR-004 而不是让代码与规格长期不一致 per FR-004 (contradicts)
- [x] T049 失败时清理半成品：`VideoProgress.Failed` 分支未调用 `cleanupPartialFile`，
  只有取消分支调用了；失败后 `files/videos/` 会留下不完整文件 per FR-014 (missing)
- [x] T050 离开页面即取消压缩（真机已验证：压缩中切 tab 后，半成品文件被删除）：Section 缺 `DisposableEffect { onDispose { dispatch(Cancel) } }`，
  当前切走 tab 后转码仍在后台继续，与「v1 只在前台压缩」的规格不符 per FR-015 (missing)
- [x] T051 超长/超大视频提示：源超过 30 分钟或 2GB 时提示「建议先裁剪」，但仍允许继续
  （规格「边界情况」要求，当前无任何检查）per 边界情况 (missing)
- [x] T052 结果区补齐原大小与输出时长：`VideoCompressionSection.kt` 目前只显示「已完成 X（节省 Y）」，
  缺「原 Z → 新 X」和输出时长 per US1/AC3 (partial)
- [x] T053 收敛模块公开面：`ui.PresetSelector`、`ui.TrimTimeline` 改为 `internal`，
  让模块对外只暴露 `VideoCompressionSection` 与 `VideoCompressionViewModel` per FR-002 (unrequested)
---

## Phase 10: Convergence

> 由第二次 `/speckit-converge`（2026-10-21）追加：Phase 9 的 7 项已全部在代码中核实通过；
> 以下为复查中发现的 3 处低优先级差距（均为「规格写了、代码只做了一半」）。

- [x] T054 界面注明帧率上限（真机已验证：参数行显示「帧率 30fps」）：源为 60fps 时会压到 30fps（`CompressionPreset.MAX_FRAME_RATE`），
  但界面没有说明，用户无从得知输出帧率被降低 per 边界情况 (partial)
- [x] T055 压缩中提示「离开页面会取消」（真机已验证：压缩中显示「压缩中 45% · 离开页面会取消」）：规格要求该提示，当前只有代码注释；
  应在 `phase is Compressing` 时显示一行说明 per 边界情况 (partial)
- [x] T056 启动时清理孤儿半成品（真机已验证）：`VideoOutputCleaner` 注册为 Koin 单例，`FoolproofApp.onCreate` 在后台线程调用 `cleanAll()`；实测未进入视频页即清空残留。进程被杀时 `files/videos/` 会留下不完整 `.mp4`，
  失败/取消路径虽已清理但进程死亡无从执行；应在 VM 初始化时删除目录内无对应结果的残留文件
  （只删本次进程启动前就存在的、且不是最近成功产物的文件，避免误删）per FR-014 (partial)
---

## Phase 11: Convergence

> 由第三次 `/speckit-converge`（2026-10-21）追加：Phase 10 的 3 项已全部核实并在真机验证；
> 本轮发现 1 处「多次压缩后旧产物不可达却仍在磁盘上」的残留问题。

- [x] T057 开始新压缩前清理上一次的产物（模拟器已验证：连压两次后目录始终只有 1 个文件）：`VideoCompressionViewModel.startCompression` 每次生成新的
  `compressed_<ts>.mp4`，但从不删除旧文件；`UiState.output` 只保留一个，导致旧产物在会话内不可达
  却占着磁盘（真机实测压两次后留两个 12.5MB 文件）。应在开始新压缩时删除输出目录里的既有文件
  ——它们已不可能再被分享或保存 per FR-014 (partial)