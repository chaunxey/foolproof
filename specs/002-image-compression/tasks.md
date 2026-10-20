# Tasks: 图片压缩（image-compression）

**Input**: Design documents from `specs/002-image-compression/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)

**Tests**: 必须写（spec FR-018 + 宪法「质量门槛」）。测试范围限定在纯逻辑与 ViewModel，
不引入 Robolectric。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可与同阶段其它 [P] 任务并行（不同文件、无依赖）
- 路径相对仓库根目录

---

## Phase 1: Setup（模块骨架与构建验证）

**目的**：让 `:features:image-compression` 存在且**能编译 Compose**——这是本 feature 最容易踩坑的地方

- [ ] T001 在 `settings.gradle.kts` 注册 `include(":features:image-compression")`
- [ ] T002 [P] 在 `gradle/libs.versions.toml` 增加 `compressor = "3.0.1"` 与 `coil = "3.6.3"`，
  以及 `compressor`、`coil-compose` 两个库条目（Coil 已达 stdlib 2.4.10，与工程 2.4.21 兼容）
- [ ] T003 创建 `features/image-compression/build.gradle.kts`：应用 `android.library` + `kotlin.compose`，
  `namespace = "com.foolproof.features.compression"`、`compileSdk { release(37) }`、`minSdk 24`、Java 11、
  `buildFeatures { compose = true; buildConfig = true }`；依赖 `project(":base")`、Compose BOM、
  `activity-compose`、`core-ktx`、`compressor`、`coil-compose`、`kotlinx-coroutines-android`；
  测试依赖 `junit` + `kotlinx-coroutines-test` + `koin-test`
- [ ] T004 [P] 创建 `features/image-compression/src/main/AndroidManifest.xml`（空 manifest，权限一个不加）
- [ ] T005 [P] 创建占位的 `features/image-compression/src/main/java/com/foolproof/features/compression/CompressionSection.kt`
  （空 `@Composable`），让模块可编译
- [ ] T006 运行 `./gradlew :features:image-compression:assembleDebug`——**硬门槛**：
  验证子模块的 Compose 插件与 BOM 配置正确，失败则先解决再继续

---

## Phase 2: Foundational（领域模型与纯逻辑）

- [ ] T007 [P] 创建 `.../domain/SourceImage.kt`、`CompressionOption.kt`、`CompressionOutcome.kt`
  （含 `OutcomeStatus` 六态）；`SourceImage` 用 `String id` 表示图片，**不持有 `android.net.Uri`**
- [ ] T008 [P] 创建 `.../domain/ImageCompressor.kt` 与 `ResultExporter.kt` 两个接口（最小测试接缝）
- [ ] T009 [P] 创建 `.../platform/PlatformCapabilities.kt`：`sdkInt` 可注入，提供 `isHeicSupported`（≥28）
  与 WebP 格式选择（≥30 用 `WEBP_LOSSY`，否则 `WEBP`）
- [ ] T010 [P] 创建 `.../platform/BitmapSampling.kt`：`calculateInSampleSize(width, height, maxDimension)` 纯函数
- [ ] T011 [P] 创建 `src/test/.../CompressionOptionTest.kt`：格式/质量映射、质量边界（0/100）、SDK 分支
- [ ] T012 [P] 创建 `src/test/.../PlatformCapabilitiesTest.kt`：SDK 27/28 边界的 HEIC 判定、WebP 常量分支
- [ ] T013 [P] 创建 `src/test/.../BitmapSamplingTest.kt`：小图返回 1、非 2 的幂、极端长宽比（1:100）
- [ ] T014 运行 `./gradlew :features:image-compression:testDebugUnitTest` 通过

---

## Phase 3: US1 — 批量压缩并看到体积对比 (P1)

- [ ] T015 [US1] 创建 `.../data/ImageMetadataReader.kt`：读取文件名、字节数、MIME、宽高（`inJustDecodeBounds`），
  并识别 HEIC（MIME 或扩展名）
- [ ] T016 [US1] 创建 `.../data/CompressorImageCompressor.kt`：调用 Compressor 的阻塞 `compressToFile`，
  强制在 `Dispatchers.IO` 执行；先按 `BitmapSampling` 判定是否需要预处理大图
- [ ] T017 [US1] 创建 `.../CompressionContract.kt`：`CompressionUiState`、`CompressionAction`、`CompressionEffect`
- [ ] T018 [US1] 创建 `.../CompressionViewModel.kt`：继承 `BaseViewModel`，实现 `Start`（串行循环、逐张状态、
  单张异常隔离为 `Failed`）、`ImagesPicked`；每张之间 `yield()` 以便取消
- [ ] T019 [US1] 创建 `src/test/.../CompressionViewModelTest.kt`：3 张成功路径、状态流转
  `Pending → Running → Success`、体积对比字段、空列表不触发
- [ ] T020 [US1] 实现 `.../CompressionSection.kt`：选图入口、格式/质量选择、列表（缩略图 + 原大小）、
  开始按钮、逐张进度与结果行
- [ ] T021 [US1] 运行 `./gradlew :features:image-compression:testDebugUnitTest` 通过

---

## Phase 4: US2 — 把结果带出去 (P1)

- [ ] T022 [US2] 创建 `.../data/ExportNaming.kt`：输出文件名与重名追加规则（纯函数，可单测）
- [ ] T023 [US2] 创建 `src/test/.../ExportNamingTest.kt`：扩展名映射、重名 `_1`/`_2`、名称含特殊字符
- [ ] T024 [US2] 创建 `.../data/MediaStoreResultExporter.kt`：写入私有目录 → `FileProvider` 分享；
  API ≥ 29 用 `MediaStore` + `IS_PENDING` 存入相册（无权限），API ≤ 28 返回"不支持"
- [ ] T025 [US2] 创建 `src/main/res/xml/image_compression_paths.xml` 并在
  `features/image-compression/src/main/AndroidManifest.xml` 声明 `FileProvider`，
  authority 用 `${applicationId}.imagecompression`
- [ ] T026 [US2] 在 `CompressionSection.kt` 结果行加「分享」与「保存到相册」；
  `canSaveToGallery()` 为 false 时隐藏保存并显示说明（FR-010）
- [ ] T027 [US2] 运行测试并 `assembleDebug`，确认 FileProvider 配置被 manifest 合并接受

---

## Phase 5: US3 — 参数调整时的预估 (P2)

- [ ] T028 [US3] 在 `CompressionViewModel` 实现采样预览：`OptionChanged` 后 `debounce(300)`，
  对第一张做一次压缩写 `estimateBytes`；新参数取消旧任务；列表为空不触发
- [ ] T029 [US3] 在 `src/test/.../CompressionViewModelTest.kt` 补三条断言：防抖只触发一次、
  连续改动只保留最后一次、空列表不触发
- [ ] T030 [US3] 在 `CompressionSection.kt` 参数区展示预估体积（加载中显示占位）

---

## Phase 6: US4 — 失败与中断 (P2)

- [ ] T031 [US4] 在 `CompressionViewModel` 增加 `Cancel` 与 `Retry(id)`：持有批次 `Job`，
  取消时把运行中的那张复位为 `Pending`，已完成结果保留
- [ ] T032 [US4] 在 `src/test/.../CompressionViewModelTest.kt` 补：取消后不再产生新状态、
  失败项单独重试成功、失败不影响其余项
- [ ] T033 [US4] 在 `CompressionSection.kt` 增加取消按钮与失败项的「重试」入口

---

## Phase 7: Polish（集成与收口）

- [ ] T034 创建 `.../di/ImageCompressionModule.kt`：`viewModel { CompressionViewModel(...) }` +
  `ImageCompressor` / `ResultExporter` / `ImageMetadataReader` 的 `single`
- [ ] T035 在 `app/build.gradle.kts` 增加 `implementation(project(":features:image-compression"))`
- [ ] T036 在 `app/src/main/java/com/foolproof/FoolproofApp.kt` 的 `koinModules()` 追加 `imageCompressionModule`
- [ ] T037 创建 `app/src/main/java/com/foolproof/ui/compression/CompressionTabScreen.kt`：
  仅 `Column { CompressionSection() }`，不写任何压缩逻辑（宪法 §9 v1.4 否决项）
- [ ] T038 在 `app/src/main/java/com/foolproof/MainActivity.kt` 用 `CompressionTabScreen` 替换模板 `Greeting`
- [ ] T039 运行 `./gradlew :app:assembleDebug` 通过
- [ ] T040 全仓检索 `WRITE_EXTERNAL_STORAGE`，确认本模块相关代码与 manifest **0 处**（SC-004）
- [ ] T041 全仓检索 `android.util.Log` 与 `println(`，生产代码 **0 处**（FR-019）
- [ ] T042 运行 `./gradlew :app:assembleRelease` 通过（R8 下 Compressor/Coil/Compose 不缺类）
- [ ] T043 更新 `README.md`：模块规划标注 image-compression 已建、测试数更新
- [ ] T044 真机冒烟：选图 → 压缩 → 对比体积 → 分享 → 保存相册（≥29）→ HEIC 在 27/28 上的差异（本机无设备，待执行）

---

## Dependencies

```
Phase 1 (T001–T006)
   └─→ Phase 2 (T007–T014)   ← T007~T013 互相独立，可并行
          ├─→ Phase 3 US1 (T015–T021)  ← 核心路径，其余故事都依赖它
          │      └─→ Phase 4 US2 (T022–T027)
          │      └─→ Phase 5 US3 (T028–T030)   ← 只依赖 T018 的 ViewModel
          │      └─→ Phase 6 US4 (T031–T033)   ← 只依赖 T018 的 ViewModel
          └─→ Phase 7 (T034–T044)  ← 依赖全部前置
```

**关键约束**：

- **T006 是硬门槛**：子模块 Compose 配置错误会导致后续全部 UI 任务无法编译
- T020（UI）依赖 T018（ViewModel 契约）；T024（导出）依赖 T023 的命名规则
- T044 需要真机，本机无法执行

## 并行机会

| 阶段 | 可并行 | 说明 |
| --- | --- | --- |
| Phase 2 | T007～T013 | 六个独立文件，可一次写完 |
| Phase 4/5/6 | 三者互不依赖 | 都以 T018 的 ViewModel 为基础，可并行推进 |
| Phase 7 | T040/T041 可在 T039 之后并行 | 纯检索任务 |

## MVP 范围

**最小可演示切片 = Phase 1 + 2 + 3（T001–T021）**：能选图、串行压缩、看到体积对比。
US2（导出）、US3（预估）、US4（取消/重试）是可增量交付的体验增强。

## 需求覆盖自检

| 需求 | 覆盖任务 |
| --- | --- |
| FR-001 / FR-002 / FR-003（模块与边界） | T001、T003、T037 |
| FR-004（零权限） | T004、T040 |
| FR-005（用 Compressor） | T003、T016 |
| FR-006（JPEG/WebP + 质量） | T011、T020 |
| FR-007（多选 + 串行） | T018、T020 |
| FR-008（体积对比 / 无收益） | T018、T020 |
| FR-009（私有目录 + 分享） | T022、T024、T026 |
| FR-010（保存相册仅 ≥29） | T024、T026 |
| FR-011（HEIC 门槛） | T009、T015、T018 |
| FR-012（采样防 OOM） | T010、T016 |
| FR-013（继承 BaseViewModel） | T018 |
| FR-014（失败隔离 + 重试） | T018、T031、T032 |
| FR-015（可取消） | T031、T032 |
| FR-016（不联网） | 无网络依赖，T042 的 release 构建可间接验证 |
| FR-017（不携带定位 EXIF） | T016（重编码不写回 EXIF） |
| FR-018（单测） | T011–T014、T019、T023、T029、T032 |
| FR-019（禁 android.util.Log） | T041 |
| SC-004 / SC-006 | T040、T020 |
