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

- [ ] T014 [US1] 创建 `.../domain/VideoCompressor.kt`：接口 + `VideoProgress`
  （Running/Success/Failed/Cancelled）+ `VideoFailure` 分类
- [ ] T015 [US1] 创建 `.../domain/EncoderCatalog.kt`：接口 + `EncoderCapability`
  （mime / isHardware / maxWidth / maxHeight / maxBitrate）
- [ ] T016 [US1] 创建 `.../data/MediaCodecEncoderCatalog.kt`：`MediaCodecList(ALL_CODECS)` 查询，
  `VideoCapabilities` 取分辨率与码率上限
- [ ] T017 [US1] 创建 `.../data/ContentResolverVideoReader.kt`：`MediaMetadataRetriever` 读时长/宽高/
  帧率/有无音轨；**读完立即 release**（避免与转码抢解码器）
- [ ] T018 [US1] 创建 `.../data/Media3VideoCompressor.kt`：`Transformer` + `Presentation`（缩放）+
  `ClippingConfiguration`（裁剪）+ `VideoEncoderSettings.setBitrate`；
  `compress()` 返回 `Flow<VideoProgress>`；`cancel()` 等 `onCancelled` 后再删临时文件
- [ ] T019 [US1] 创建 `.../VideoCompressionContract.kt`：UiState/Action/Effect + `Phase`
  （Idle/Ready/Compressing/Done/Failed）
- [ ] T020 [US1] 创建 `.../VideoCompressionViewModel.kt`：继承 `BaseViewModel`；选视频→读元信息→
  编码器门槛检查→压缩→进度→完成/失败；取消走协程 + 压缩器 cancel
- [ ] T021 [US1] 创建 `src/test/.../VideoCompressionViewModelTest.kt`：状态流转、进度更新、
  取消、失败分类、重复点击防护
- [ ] T022 [US1] 运行测试 + `assembleDebug` 通过

---

## Phase 4: US1 收尾 — 导出与分享 (P1)

- [ ] T023 [US1] 创建 `features/video-compression/src/main/res/xml/video_paths.xml` +
  manifest 中声明 FileProvider（authority `${applicationId}.video`）
- [ ] T024 [US1] 创建 `.../data/MediaStoreVideoExporter.kt`：`IS_PENDING` 写入 → 置 0；
  API < 29 时 `canSaveToGallery() = false`
- [ ] T025 [US1] ViewModel 接入分享/存相册动作 + 成功/失败提示（复用图片压缩的交互口径）
- [ ] T026 单测覆盖：`canSaveToGallery` 的 API 分支、导出失败映射

---

## Phase 5: US2 — 裁剪时长 (P1)

- [ ] T027 [US2] 创建 `.../ui/TrimTimeline.kt`：平台 `VideoView` 预览 + `MediaMetadataRetriever`
  抽 8–12 帧做缩略图条 + 双滑块（起止）+ 时间标签（mm:ss）
- [ ] T028 [US2] ViewModel：`TrimChanged` 动作 → 校验（最小区段/越界）→ 更新状态与预估
- [ ] T029 [US2] 单测：裁剪后预估体积按新时长计算；起止超界被夹紧；整段与不裁剪等价
- [ ] T030 [US2] `Media3VideoCompressor` 接入 `ClippingConfiguration`（起止 ms）

---

## Phase 6: US3 — 档位与预估 (P2)

- [ ] T031 [US3] 创建 `.../ui/PresetSelector.kt`：分辨率三档 + 码率三档（FilterChip），
  显示目标分辨率与目标码率；不可用档位置灰并说明
- [ ] T032 [US3] 界面显示预估体积与「裁剪后时长」；参数变化实时联动
- [ ] T033 [US3] 编码器能力不足时自动降档并在界面说明（如 1080p 不支持 → 降到 720p）

---

## Phase 7: 集成

- [ ] T034 创建 `.../di/VideoModule.kt`：`viewModel { }` + `VideoCompressor` / `EncoderCatalog` /
  `VideoReader` / `VideoExporter` 的 `single`
- [ ] T035 在 `app/build.gradle.kts` 增加 `implementation(project(":features:video-compression"))`
- [ ] T036 在 `FoolproofApp.kt` 的 `koinModules()` 追加 `videoCompressionModule`
- [ ] T037 在 `:app` 新建 `ui/FoolproofScreen.kt`：`NavigationBar` 两个 tab（图片 / 视频），
  `compression` → `CompressionTabScreen`，`video` → `VideoTabScreen`（内部只放 `VideoCompressionSection`）；
  `MainActivity` 改为使用 `FoolproofScreen`（**`:app` 不写业务逻辑**）
- [ ] T038 运行 `./gradlew :app:assembleDebug` 通过

---

## Phase 8: Polish

- [ ] T039 全仓检索本模块 `WRITE_EXTERNAL_STORAGE`（应为 0，SC-006）
- [ ] T040 全仓检索本模块 `android.util.Log` / `println(`（应为 0）
- [ ] T041 应用启动时清理 `files/videos/*.tmp`（防御进程被杀）
- [ ] T042 运行 `./gradlew :app:assembleRelease` 通过
- [ ] T043 更新 `README.md`：模块规划、视频压缩小节、媒体栈说明
- [ ] T044 **真机验证（待设备）**：1080p→720p 体积对比（SC-001）、预估偏差（SC-002）、
  裁剪 5s 时长（SC-003）、竖屏旋转正确（SC-007）、存相册可见（SC-005）
- [ ] T045 **模拟器验证**：软件编码器路径能跑通管线（不计性能），确认取消后无残留（SC-004）

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
