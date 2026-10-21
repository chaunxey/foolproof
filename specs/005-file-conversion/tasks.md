# Tasks: 文件转 PDF（file-conversion）

**Feature**: `specs/005-file-conversion/spec.md` · **Plan**: `specs/005-file-conversion/plan.md`
**Constitution**: v1.17（导航已改为轻量 tab host）
**测试**：`./gradlew :features:file-conversion:testDebugUnitTest`
**组织方式**：先模块骨架，再纯逻辑（可单测），再用户故事，最后集成与打磨。

## Phase 1: Setup（两个新模块）

- [x] T001 新建 `:common` 模块：`common/build.gradle.kts`（android library + compose + Koin）、
  `common/src/main/AndroidManifest.xml`、`settings.gradle.kts` 加 `include(":common")`
- [x] T002 新建 `:features:file-conversion` 模块：`features/file-conversion/build.gradle.kts`
  （依赖 `:base`、`:common`、compose、koin；**不新增第三方依赖**）、manifest、settings 注册
- [x] T003 `:app` 增加对 `:common` 与 `:features:file-conversion` 的依赖，并在
  `FoolproofApp.kt` 注册 `fileConversionModule`（`app/build.gradle.kts`、`app/src/main/java/com/foolproof/FoolproofApp.kt`）

## Phase 2: Foundational（纯逻辑与骨架，先做完再写 UI）

- [x] T004 定义领域模型与接口：`PdfSource`（Image/Text）、`PdfPageSpec`、`PdfResult`、
  `PdfConverter`、`PdfProgress`（`features/file-conversion/src/main/java/com/foolproof/features/conversion/domain/`）
- [x] T005 实现 `MarkdownParser`：标题/段落/列表/粗体/斜体/行内码/代码块/分隔线 → `List<Block>`，
  纯 Kotlin 无 Android 依赖（`.../data/MarkdownParser.kt`）
- [x] T006 实现 `TextLayout`：`Paint.breakText` 折行、超长无空格串强制断行、跨页不切行、
  代码块整体换页（`.../data/TextLayout.kt`）
- [x] T007 实现 `BitmapImageLoader`：`inJustDecodeBounds` 探测 + `inSampleSize`（长边 ≤ 2048）+
  EXIF 方向修正（`.../data/BitmapImageLoader.kt`）
- [x] T008 实现 `ContentUriTextLoader`：UTF-8 严格解码，失败回退 GBK，再失败返回分类错误（`.../data/ContentUriTextLoader.kt`）
- [x] T009 实现 `NativePdfGenerator`：A4 595×842pt、图片等比居中（边距 24pt）、文本逐行绘制（边距 48pt）、
  页数产出（`.../data/NativePdfGenerator.kt`）
- [x] T010 实现 `PdfOutputCleaner`（Koin 单例）：删除 `files/pdf/` 下全部残留；
  `MediaStorePdfExporter`（API ≥ 29 存 `Downloads/Foolproof/`，`IS_PENDING`）（`.../data/`）
- [x] T011 ViewModel 骨架：`FileConversionContract`（State/Action/Effect）+ `FileConversionViewModel`
  继承 `BaseViewModel`，状态机 Idle/Ready/Converting/Done/Failed，注入 `LogUtil`（`.../FileConversion*.kt`）
- [x] T012 Koin 模块 `FileConversionModule`：绑定 Converter / Loader / Exporter / Cleaner（`.../di/FileConversionModule.kt`）

## Phase 3: US1 — 多张图片合成 PDF (P1)

- [x] T013 图片选择：`PickMultipleVisualMedia`（上限 50），选中后读宽高并生成缩略图列表（`.../ui/FileConversionSection.kt`）
- [x] T014 列表交互：显示序号与缩略图、删除单张、上移/下移调整顺序（`.../ui/ImageSourceList.kt`）
- [x] T015 生成入口：未选图片时置灰；点击后逐张绘制并推进进度（`current/total`）（`.../ui/FileConversionSection.kt`、ViewModel）
- [x] T016 HEIC 门槛：API 24–27 遇到 HEIC 时提示并跳过该张，其余继续（`BitmapImageLoader` + ViewModel 错误映射）
- [x] T017 取消与清理：取消立即停止、删除半成品、回到 Ready（ViewModel + `NativePdfGenerator`）

## Phase 4: US2 — 文本 / Markdown 转 PDF (P1)

- [x] T018 文本选择：SAF `ACTION_OPEN_DOCUMENT`（`text/*` + `.md`），显示文件名与大小；> 2MB 拒绝（`.../ui/FileConversionSection.kt`）
- [x] T019 解析与排版接入：Markdown → Block → 折行 → 分页 → 绘制，串成一条链路（ViewModel + `NativePdfGenerator`）
- [x] T020 空内容/编码错误提示：空文件、全空白、GBK 回退失败分别给出分类提示（ViewModel 错误映射）
- [x] T021 模式切换：图片模式 / 文本模式切换时清理另一侧的中间状态（`.../ui/ModeSelector.kt`）

## Phase 5: US3 — 保存与应用内分享 (P2)

- [x] T022 结果区：显示文件名、大小、页数，并提供【打开】【分享】【存到下载】（`.../ui/FileConversionSection.kt`）
- [x] T023 分享：`FileProvider` + `ACTION_VIEW`/`ACTION_SEND`，`file_paths.xml` 增加 `pdf/` 目录（`app/src/main/res/xml/file_paths.xml`）
- [x] T024 存下载：API ≥ 29 经 `MediaStore.Downloads` 写入；API ≤ 28 不显示该按钮（`MediaStorePdfExporter` + UI 分支）
- [x] T025 启动清理接入：`FoolproofApp.onCreate` 后台线程调用 `PdfOutputCleaner`（与 `VideoOutputCleaner` 并列）

## Phase 6: 导航集成（`:common` + `:app`）

- [x] T026 `:common` 导航封装：`FoolproofDestination`（Images/Videos/Converter）、
  `FoolproofTabHost`（NavigationBar + 状态机，重复点同一 tab 不重建、切回首 tab 即重置）（`common/.../navigation/`）
- [x] T027 `:app` 改造：`FoolproofScreen` 用 `FoolproofTabHost` 组装三个 tab 的内容
  （图片页沿用现有网格、视频页 `VideoCompressionSection`、转换页 `FileConversionSection`），
  删除 `:app` 内自写的导航状态机（`app/src/main/java/com/foolproof/ui/FoolproofScreen.kt`）

## Phase 7: Polish

- [ ] T028 单测：`MarkdownParser`（各语法元素）、`TextLayout`（中英混排/超长串/分页不切行）、
  文件名生成、状态机与错误映射（`features/file-conversion/src/test/java/...`）
- [ ] T029 仪器化测试：用 `PdfRenderer` 打开生成物，断言页数与页面尺寸（A4 点值）（`.../src/androidTest/...`）
- [ ] T030 端到端自测：5 张混合比例图片 → PDF（页序、不变形）；中文 Markdown → PDF（无乱码）；
  取消后无残留；存下载落盘
- [ ] T031 文档：README 增加「文件转 PDF」章节；`.specify/memory/constitution.md` 若实现中发现偏差则同步

## 依赖关系

```
T001 ─┬─> T002 ─> T003
      └─> T026 ─> T027
T002/T003 ─> T004 ─> T011 ─> T012
T004 ─> T005,T006,T007,T008 ─> T009 ─> T010
T011/T012 + T005..T010 ─> T013..T017（US1）
                        └─> T018..T021（US2） ─> T022..T025（US3）
T026/T027 可与 US1–US3 并行
```

## 验收映射

| 需求 | 任务 |
|---|---|
| FR-001、FR-006、FR-007 | T013、T015、T009 |
| FR-002、FR-003、FR-004、FR-008 | T018、T005、T006、T019 |
| FR-005（零第三方 PDF 库） | T002、T009 |
| FR-010（HEIC 门槛） | T016 |
| FR-011、FR-017 | T015、T022 |
| FR-012、FR-013 | T023、T024 |
| FR-014 | T010、T017、T025 |
| FR-015、FR-016 | T011、T020 |
| FR-018、FR-019 | T028、T029 |
| SC-001..SC-006 | T030、T029 |
