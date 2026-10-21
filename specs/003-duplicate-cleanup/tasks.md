# Tasks: 重复照片清理（duplicate-cleanup）

**Input**: Design documents from `specs/003-duplicate-cleanup/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)

**Tests**: 必须写（spec FR-017 + 宪法质量门槛）。哈希与分组是纯函数，JVM 单测即可覆盖
SC-002 / SC-003，不依赖真机。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可与同阶段其它 [P] 任务并行（不同文件、无依赖）
- 路径相对仓库根目录

---

## Phase 1: Setup（前置重构 + 模块骨架）

**目的**：先把重复的两个工具函数下沉到 `:base`（宪法 §6），再建模块骨架并验证可编译

- [x] T001 把 `BitmapSampling` 从 `features/image-compression/**/platform/` 迁移到
  `base/src/main/java/com/foolproof/base/util/BitmapSampling.kt`（改包名、更新 002 的引用与单测）
- [x] T002 把字节格式化（`formatBytes`）从 002 的 `CompressionSection.kt` 抽到
  `base/src/main/java/com/foolproof/base/util/ByteFormat.kt`，002 改为调用它
- [x] T003 运行 `./gradlew :base:testDebugUnitTest :features:image-compression:testDebugUnitTest`，
  确认重构无回归（**门槛**，失败则先修）
- [x] T004 在 `settings.gradle.kts` 注册 `include(":features:duplicate-cleanup")`
- [x] T005 创建 `features/duplicate-cleanup/build.gradle.kts`：`android.library` + `kotlin.compose`、
  `namespace = com.foolproof.features.duplicates`、`compileSdk { release(37) }`、`minSdk 24`、Java 11、
  `buildFeatures { compose = true; buildConfig = true }`；依赖 `:base`、Compose BOM/material3、`coil-compose`、
  `activity-compose`；测试依赖 `junit` + `kotlinx-coroutines-test` + `koin-test`
- [x] T006 创建 `features/duplicate-cleanup/src/main/AndroidManifest.xml`：只声明
  `READ_MEDIA_IMAGES` 与 `READ_EXTERNAL_STORAGE`（`maxSdkVersion="32"`），**不得有写权限**
- [x] T007 [P] 创建占位 `.../duplicates/DuplicateSection.kt`（空 Composable），让模块可编译
- [x] T008 运行 `./gradlew :features:duplicate-cleanup:assembleDebug`（**硬门槛**：验证子模块 Compose 配置）

---

## Phase 2: Foundational（纯逻辑与模型）

- [x] T009 [P] 创建 `.../domain/PhotoItem.kt` 与 `PhotoGroup.kt`
  （PhotoItem：id、名称、字节、宽高、dHash、lowInformation；PhotoGroup：成员、总字节、可释放字节）
- [x] T010 [P] 创建 `.../domain/PerceptualHash.kt`：`grayscale`、`dHash`（9×8 → 64 bit）、`hamming`、
  `isLowInformation`（灰度方差阈值）
- [x] T011 [P] 创建 `.../domain/DuplicateGrouper.kt`：`group(items, threshold = 5)` 流式贪心 +
  低信息量保护（仅与宽高比接近且字节差 < 2% 合并）+ `defaultSelection(group)`（除最大一张外）
- [x] T012 [P] 创建 `src/test/.../PerceptualHashTest.kt`：全等图距离 0、全反图距离 64、
  单像素差、9×8 缩放输入、低信息量判定（纯色 → true）
- [x] T013 [P] 创建 `src/test/.../DuplicateGrouperTest.kt`：距离 5 入组 / 6 不入组、
  同图不同压缩版本同组（SC-002）、不同构图不误并（SC-003）、纯色图不互并（FR-005）、
  预选为「除最大外」（FR-012）
- [x] T014 运行 `./gradlew :features:duplicate-cleanup:testDebugUnitTest` 通过

---

## Phase 3: US1 — 扫描并分组展示 (P1)

- [x] T015 [US1] 创建 `.../domain/PhotoScanner.kt`：`fun scan(): Flow<ScanEvent>`，
  事件含 `Started(total)` / `Progress(done, total)` / `Finished(skipped)` / `Failed(reason)`
- [x] T016 [US1] 创建 `.../data/ThumbnailPixelSource.kt`：API ≥ 29 用
  `ContentResolver.loadThumbnail(uri, Size(64,64), null)`，≤ 28 用采样解码；统一输出 `IntArray` + 宽高；
  逐张 try/catch（HEIC 失败计入跳过，不中断）
- [x] T017 [US1] 创建 `.../data/MediaStorePhotoScanner.kt`：查询 `MediaStore.Images`（含子目录）、
  逐张取像素 → 算 dHash → 发事件；每 N 张 `yield()`；支持协作取消
- [x] T018 [US1] 创建 `.../DuplicateContract.kt`：`DuplicateUiState`（scan/groups/selected/deleting/permission/lastReport）、
  `DuplicateAction`（Scan/Cancel/Select/ToggleGroup/Delete/...）、`DuplicateEffect`（OpenPermission/OpenSettings/
  ShowConfirm/DeleteRequest(PendingIntent)/Message）、`ScanState`、`PermissionState`
- [x] T019 [US1] 创建 `.../DuplicateViewModel.kt`：继承 `BaseViewModel`，实现扫描（`launchJob` + 收集 Flow）、
  取消、完成后分组并用 `defaultSelection` 初始化勾选、空结果提示
- [x] T020 [US1] 创建 `src/test/.../DuplicateViewModelTest.kt`：扫描状态流转、取消后不再更新、
  空结果、分组后默认勾选
- [x] T021 [US1] 创建 `.../ui/DuplicateGroupGrid.kt`：`LazyVerticalGrid` + 跨列组头
  （组头显示「N 张 · 可释放 X MB」），支持勾选切换
- [x] T022 [US1] 在 `DuplicateSection.kt` 组装：按钮文案状态机（【查询相同图片】/【取消】/【重新查询】）、
  扫描进度、空态、结果网格；首次扫描前经 `DuplicateEffect` 请求权限
- [x] T023 [US1] 运行测试并 `assembleDebug` 通过

---

## Phase 4: US2 — 勾选并删除 (P1)

- [x] T024 [US2] 创建 `.../domain/PhotoDeleter.kt`：`DeleteChannel`（SYSTEM_REQUEST / SAF / NONE）、
  `delete(uris): DeleteReport`、`setSafTree(uri)`；`DeleteReport(success, failed)`
- [x] T025 [US2] 创建 `.../data/MediaStorePhotoDeleter.kt`：
  API ≥ 30 用 `MediaStore.createDeleteRequest` 造 `PendingIntent`；API ≤ 29 用 SAF `deleteDocument`
  并先检查 `FLAG_SUPPORTS_DELETE`；**删除后重新查询 uri 是否存在**以判定真实成败
- [x] T026 [US2] ViewModel 增加删除流程：`RequestDelete` → 校验勾选 → 产生确认 Effect；
  `ConfirmDelete` → 执行（30+ 把 PendingIntent 交给 UI）→ 汇总报告 → 刷新分组与勾选
- [x] T027 [US2] 在 `src/test/.../DuplicateViewModelTest.kt` 补：删除前必须先确认（SC-005）、
  失败项不影响其余、报告计数正确、全部删光的组消失
- [x] T028 [US2] 在 `DuplicateSection.kt` 加：删除按钮（显示待删张数与总大小）、二次确认框
  （API ≤ 29 追加「删除后无法恢复」）
- [x] T029 [US2] 用 `ActivityResultContracts.StartIntentSenderForResult` 拉起系统删除确认，
  回调后触发「重新校验」而非直接信任结果码
- [x] T030 [US2] 运行测试并 `assembleDebug` 通过

---

## Phase 5: US3 — 全屏预览 (P2)

- [x] T031 [US3] 创建 `.../ui/PhotoPreviewDialog.kt`：全屏展示，可查看大图（Coil 加载原图）
- [x] T032 [US3] `DuplicateSection` 接入预览；返回后勾选状态保持不变

---

## Phase 6: US4 — 低版本 SAF 降级 (P2)

- [x] T033 [US4] 实现 SAF 目录选择：`ActivityResultContracts.OpenDocumentTree` + 持久化授权
  （`takePersistableUriPermission`），写入 `PhotoDeleter.setSafTree`
- [x] T034 [US4] ViewModel/UI：API ≤ 29 且未授权时删除按钮置灰并说明；授权后可用，
  确认框与结果提示「已删除，无法恢复」
- [x] T035 [US4] 在单测中覆盖通道选择逻辑（SDK 30 / 29 边界、SAF 未授权 → NONE）

---

## Phase 7: Polish（集成与收口）

- [x] T036 创建 `.../di/DuplicateModule.kt`：`viewModel { DuplicateViewModel(...) }` +
  Scanner / Deleter / PixelSource 的 `single`
- [x] T037 在 `app/build.gradle.kts` 增加 `implementation(project(":features:duplicate-cleanup"))`
- [x] T038 在 `FoolproofApp.kt` 的 `koinModules()` 追加 `duplicateModule`
- [x] T039 在 `app/ui/compression/CompressionTabScreen.kt` 追加 `DuplicateSection()`
  （位于压缩区**下方**，宪法 §3.2 v1.8）
- [x] T040 运行 `./gradlew :app:assembleDebug` 通过
- [x] T041 全仓检索 `WRITE_EXTERNAL_STORAGE`，确认本模块 0 处（SC-004）
- [x] T042 全仓检索本模块 `android.util.Log` / `println(`，确认 0 处
- [x] T043 运行 `./gradlew :app:assembleRelease` 通过（R8 下不裁剪哈希/分组逻辑）
- [x] T044 **真机验证（vivo V2602A / API 37，已完成分组准确性，回收站存疑）**：
  分组准确性 ✅ —— 11 张相册实测：完全相同的两份副本同组；同内容不同文件名的两张图同组；
  **纯色图（白 400x400 与黑 800x600，宽高比不同）未被合并**（保护生效）；
  内容不同的图未被误并。
  删除链路 ✅ —— 只勾选自己的测试文件后，弹出确认框（「将删除 1 张，约 111 KB」，
  无「不可恢复」警告，符合 API ≥ 30）→ 系统对话框「要允许 Foolproof 删除这张照片吗？」→ 允许后文件确实消失。
  **SC-006 回收站恢复：本机未证实** —— 删除后 `find /sdcard` 无残留、MediaStore 无 `is_trashed=1` 行、
  无 `.trash` 目录可见。系统对话框证明走的是 `createDeleteRequest`（回收站通道），
  但无法确认 vivo 是否保留可恢复副本，需在 AOSP 模拟器上对照验证（见 T047）
- [x] T045 更新 `README.md`：模块规划标注 duplicate-cleanup 已建，补一节说明与「dHash 镜像不敏感」的已知局限
- [x] T046 修复真机反馈的排版问题（宪法 §3.2 v1.11）：整页改为单一滚动容器，
- [ ] T047 **AOSP 对照验证回收站（SC-006）**：在模拟器上把同一份测试图通过应用删除，检查`/sdcard/.trash` 或 MediaStore 的 `is_trashed=1` 行，确认「应用删除 → 回收站 → 可恢复」链路由应用正确触发；若 AOSP 有而 vivo 无，则属 OEM 行为，需在 README 注明
  Section 不再自带 `LazyColumn` / `LazyVerticalGrid`；缩略图由 `Crop` 改为按原图比例完整显示

---

## Dependencies

```
Phase 1 (T001–T008)
   └─→ Phase 2 (T009–T014)   ← T009~T013 可并行
          ├─→ Phase 3 US1 (T015–T023)  ← 主链路
          │      ├─→ Phase 4 US2 (T024–T030)
          │      ├─→ Phase 5 US3 (T031–T032)
          │      └─→ Phase 6 US4 (T033–T035)
          └─→ Phase 7 (T036–T045)  ← 依赖全部前置
```

**关键约束**：

- **T003 与 T008 是门槛**：重构必须先绿、子模块 Compose 必须先编译通过
- T019（ViewModel）依赖 T017（Scanner）与 T011（Grouper）
- T026（删除流程）依赖 T025（Deleter）
- T044 需要真机，本机当前设备已掉线

## 并行机会

| 阶段 | 可并行 | 说明 |
| --- | --- | --- |
| Phase 2 | T009～T013 | 六个独立文件 |
| Phase 4/5/6 | 三者互不依赖 | 都以 US1 的 VM 与网格为基础 |
| Phase 7 | T041/T042 可在 T040 后并行 | 纯检索 |

## MVP 范围

**最小可演示切片 = Phase 1 + 2 + 3（T001–T023）**：能扫描、能分组、能看可释放空间，
但还不能删——删除（US2）是下一个增量。

## 需求覆盖自检

| 需求 | 覆盖任务 |
| --- | --- |
| FR-001 / FR-002（模块与页面归属） | T004、T005、T039 |
| FR-003（不联网） | 模块无网络依赖，T043 的 release 构建间接验证 |
| FR-004（dHash + ≤5） | T010、T011、T012、T013 |
| FR-005（纯色图保护） | T010、T011、T013 |
| FR-006 / FR-007（分批、进度、可取消、采样） | T015、T016、T017、T019、T020 |
| FR-008（HEIC 跳过） | T016、T017、T020 |
| FR-009 / FR-010（只读权限 + 请求流程） | T006、T018、T019、T022 |
| FR-011（分组网格 + 组头） | T021、T022 |
| FR-012（默认预选 + 用户可改 + 不自动删） | T011、T013、T019、T027、T028 |
| FR-013（全屏预览） | T031、T032 |
| FR-014（二次确认 + 低版本警告） | T026、T028 |
| FR-015（删除通道） | T024、T025、T033、T035 |
| FR-016（失败隔离 + 汇总） | T025、T026、T027 |
| FR-017（单测） | T012、T013、T020、T027、T035 |
| FR-018（继承基类 + 日志） | T019、T042 |
| SC-002 / SC-003 | T013 |
| SC-004 | T041 |
| SC-006 | T044 |
