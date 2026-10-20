# Feature Specification: 图片压缩（image-compression）

**Feature Branch**: `feature_foundation1`（目录：`specs/002-image-compression`）

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "图片压缩：选择多张图片，按 JPEG/WebP 与质量参数本地压缩，展示压缩前后体积并可分享导出"

## 概述

用户在「图片压缩」tab 里从系统照片选择器挑多张图片，设置输出格式与质量，在本地完成压缩，
看到「原大小 → 新大小」的对比，并把结果分享出去或保存到相册。

三条硬边界：

- **全程离线**，不上传任何图片（宪法 §1.2）；
- **不申请存储写权限**，读图走系统照片选择器（宪法 §8）；
- **HEIC 仅在 API 28+ 可解码**，低版本明确提示而非崩溃（宪法 §3.3 门槛）。

本功能是同页中「压缩」那一半的 Section；整页与重复照片清理的组装由 `:app` 负责（宪法 §3.2，v1.4 定案）。

---

## User Scenarios & Testing

### User Story 1 - 批量压缩并看到体积对比 (Priority: P1)

用户一次选中多张图片，选择输出格式与质量，点击开始压缩，逐张看到进度与结果，
最终看到每张图压缩前后的体积差。

**Why this priority**: 这是功能 1 的全部用户价值所在；没有它，其余故事都没有承载物。

**Independent Test**: 在 ViewModel 层注入假的压缩器，选中 3 张图后触发开始，
断言状态依次为 `Pending → Running → Success`，且结果里同时包含原始体积与压缩后体积。

**Acceptance Scenarios**:

1. **Given** 用户选中 5 张图片，**When** 点击「开始压缩」，**Then** 列表按顺序逐张处理，
   当前处理项显示进度，其余保持等待状态。
2. **Given** 全部处理完成，**When** 查看结果列表，**Then** 每张显示「原大小 → 新大小」与节省百分比。
3. **Given** 某张图压缩后反而变大，**When** 查看该行，**Then** 标注为「无收益」，并默认保留原图供用户选择。
4. **Given** 用户未选任何图片，**When** 点击「开始压缩」，**Then** 按钮不可用（不触发空任务）。

---

### User Story 2 - 把压缩结果带出去 (Priority: P1)

压缩完成后，用户可以对单张结果「分享」，或在支持的设备上「保存到相册」。

**Why this priority**: 压缩完拿不出来等于没做；但它是 P1 而非最高，因为 US1 已能独立演示价值。

**Independent Test**: 断言 API 29/30+ 下「保存到相册」可见且调用后结果出现在相册；
API 24–28 下该入口不可见、仅提供分享，且不申请任何写权限。

**Acceptance Scenarios**:

1. **Given** API ≥ 29，**When** 点击「保存到相册」，**Then** 图片写入系统相册，且未申请 `WRITE_EXTERNAL_STORAGE`。
2. **Given** API ≤ 28，**When** 查看结果操作区，**Then** 只有「分享」，并有说明「当前系统不支持直接保存到相册」。
3. **Given** 任意版本，**When** 点击「分享」，**Then** 系统分享面板出现且附件为压缩后的文件。

---

### User Story 3 - 调参数时先看预估效果 (Priority: P2)

用户拖动质量滑杆或切换格式时，对当前第一张图做一次样张压缩，显示出预估体积，
避免「压完才发现还是太大」的返工。

**Why this priority**: 显著改善体验，但不影响主链路可用性。

**Independent Test**: 连续改动三次参数，断言样张任务只按防抖窗口触发一次，且结果回填到预估区。

**Acceptance Scenarios**:

1. **Given** 列表非空，**When** 用户把质量从 80 调到 60，**Then** 300ms 内无第二次改动时触发一次样张压缩，显示预估体积。
2. **Given** 样张正在计算，**When** 用户继续拖动，**Then** 上一次计算被取消，只保留最后一次结果。
3. **Given** 列表为空，**When** 用户调整参数，**Then** 不触发样张任务。

---

### User Story 4 - 失败与中断不毁掉整批 (Priority: P2)

某张图解码失败或磁盘写不下时，该张标记失败并继续处理其余；用户点取消或离开页面时任务停止，
已完成的结果保留。

**Why this priority**: 批量场景下「一颗老鼠屎坏一锅汤」会直接导致用户丢结果。

**Independent Test**: 让第 2 张图解码抛异常，断言第 1、3 张仍成功、第 2 张为 `Failed` 且可重试；
再断言取消后不再有新状态更新。

**Acceptance Scenarios**:

1. **Given** 3 张图中第 2 张解码失败，**When** 批次结束，**Then** 第 1、3 张成功，第 2 张标记失败并显示原因类别。
2. **Given** 任务进行中，**When** 用户点击取消，**Then** 不再处理剩余图片，已完成的保留在列表中。
3. **Given** 用户在压缩过程中离开该 tab，**When** 返回，**Then** 任务已终止且列表显示已完成的结果。

---

### 边界情况

- **HEIC 文件（API 24–27）**：标记「系统不支持此格式」并跳过，不计入失败、不崩溃。
- **超大图（长边 > 8192px 或像素数 > 40MP）**：先采样解码再压缩，避免 OOM。
- **小图 / 已经压过的图**：压缩后可能变大或几乎无变化，标注「无收益」并默认保留原图。
- **输出重名**：追加序号，不覆盖已有文件。
- **磁盘空间不足**：该张失败，提示清理空间，其余继续。
- **重复点击「开始压缩」**：进行中时按钮禁用，不产生并发批次。
- **选择器不可用**：设备无系统照片选择器时回退到文档选择器（同样免权限）。

---

## Requirements

### Functional Requirements

**模块与边界**

- **FR-001**: 系统 MUST 提供独立 Android Library `:features:image-compression`，仅依赖 `:base`。
- **FR-002**: 本模块 MUST 只对外暴露 `CompressionSection`（Composable）与 `CompressionViewModel`；
  整页布局由 `:app` 组装，本模块 MUST NOT 自带整页或 NavHost。
- **FR-003**: 本模块 MUST NOT 依赖任何其他 `:features:*` 模块。
- **FR-004**: 本模块 MUST NOT 申请任何存储写权限；读图 MUST 经系统照片选择器（免权限选择）。

**压缩能力**

- **FR-005**: 压缩 MUST 使用 `id.zelory:compressor`（zetbaitsu/Compressor），MUST NOT 自研压缩实现或替换为其他库。
- **FR-006**: 输出格式 MUST 限定为 JPEG 与 WebP；质量 MUST 可调（0–100），默认 JPEG / 80。
- **FR-007**: 系统 MUST 支持一次多选；处理 MUST 串行进行（逐张），并逐张暴露状态。
- **FR-008**: 结果 MUST 展示原大小、压缩后大小与节省比例；压缩后未变小时 MUST 明确标注「无收益」。
- **FR-009**: 压缩结果 MUST 写入应用私有目录，并通过 `FileProvider` 对外分享。
- **FR-010**: 「保存到相册」入口 MUST 仅在 API ≥ 29 出现；API ≤ 28 MUST 隐藏并说明原因。

**格式与版本**

- **FR-011**: HEIC 输入 MUST 在 API ≥ 28 正常处理；API ≤ 27 MUST 标记「系统不支持此格式」并跳过，
  MUST NOT 崩溃或静默丢图。
- **FR-012**: 超过阈值的大图 MUST 先采样解码（默认：长边 > 8192px 或 > 40MP），避免 OOM。

**状态与异常**

- **FR-013**: ViewModel MUST 继承 `BaseViewModel<S, E>`，异常 MUST 经基类的 `onError` 出口。
- **FR-014**: 单张失败 MUST NOT 中断整批；失败项 MUST 可单独重试。
- **FR-015**: 进行中的批次 MUST 可取消（用户操作或离开页面），已完成结果 MUST 保留。
- **FR-016**: 本功能 MUST NOT 发起任何网络请求。
- **FR-017**: 输出文件 MUST NOT 携带原图的位置信息（GPS 等定位 EXIF）。

**质量**

- **FR-018**: 压缩参数到 Compressor 的映射、HEIC 版本门槛、状态机流转、批量串行与失败隔离 MUST 有单元测试，
  `./gradlew :features:image-compression:testDebugUnitTest` 通过。
- **FR-019**: 生产代码 MUST NOT 使用 `android.util.Log` / `println`，统一用注入的 `LogUtil`。

### Key Entities

- **SourceImage**：被选中的原图（`uri`、显示名、字节数、MIME、宽高）。
- **CompressionOption**：输出格式（JPEG/WebP）与质量（0–100）。
- **CompressionOutcome**：单张结果（原图引用、结果 URI、原始字节、压缩后字节、状态、失败原因类别）。
- **OutcomeStatus**：`Pending` / `Running` / `Success` / `NoGain` / `Failed` / `Unsupported`。
- **CompressionUiState**：选中列表、当前参数、进行中标记、逐张结果、预估体积。

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: 10 张约 4MB 的 JPEG，质量 80，在中端设备上整批完成时间 ≤ 60 秒。
- **SC-002**: 对典型手机照片（原图 > 2MB），JPEG 质量 80 压缩后体积下降 ≥ 50%。
- **SC-003**: 处理 100MP 级别的大图不 OOM（采样生效），进程内存峰值 < 256MB。
- **SC-004**: 全工程检索 `WRITE_EXTERNAL_STORAGE`，本模块相关代码与 manifest 中 **0 处**。
- **SC-005**: HEIC 行为可被自动化验证：API ≥ 28 走成功路径、API ≤ 27 走 `Unsupported` 路径。
- **SC-006**: 「无收益」场景（压缩后更大）在界面上有明确标注，且默认不覆盖原图。

---

## 假设

- **选择器**：使用 AndroidX Photo Picker（`androidx.activity` 1.13 已具备），免存储权限；
  不可用时回退 `ACTION_OPEN_DOCUMENT`（同样免权限）。
- **Compressor 版本**：`id.zelory:compressor:3.0.1`（Maven Central 最新，也是 2021 年的最后发布）。
  宪法 §2 锁定该库，因此**不替换**；维护停滞的风险在 plan 中记录并给出缓解手段。
- **输出位置**：应用私有目录 `files/compressed/`；分享经 `FileProvider`。
- **后台任务**：v1 不引入 WorkManager，压缩仅在前台进行，离开页面即取消（避免新增未登记依赖）。
- **WebP 编码**：API < 30 使用 `Bitmap.CompressFormat.WEBP`（有损），API ≥ 30 使用 `WEBP_LOSSY`。
- **不做「目标体积」压缩**：需要反复试压，v1 不做。
- **范围外**：重复照片清理（另一个 feature，同页但独立模块）、相册批量浏览、云端备份。

### NEEDS CLARIFICATION

- **N1**：压缩完成后是否需要「自动保存到相册」（当前默认：不自动保存，用户手动分享/保存）。
- **N2**：同页布局顺序——压缩区在上还是重复清理区在上（属 §3.2 页面结构，可在 plan 阶段定）。
- **N3**：是否需要「批量压缩后一键导出全部」的追加能力（当前默认：逐张分享/保存）。

---

## 附录：约束摘录（每条 FR 的宪法依据）

> 本表用于避免「规格与宪法打架」——每条 FR 必须能引用到宪法条款；引不到的即为新增需求，需负责人批准。

| FR | 宪法依据 | 原文要点 |
| --- | --- | --- |
| FR-001 / FR-002 / FR-003 | §3 模块结构 · §3.2 页面归属（v1.4） | feature 之间禁止互引；同页由 `:app` 组装，feature 只提供 Section + ViewModel |
| FR-004 | §8 权限最小化 | 不申请 `WRITE_EXTERNAL_STORAGE`（Scoped Storage） |
| FR-005 | §2 技术栈锁定 | 图片压缩 = Compressor（zetbaitsu），禁止替换 |
| FR-006 | §1.1 做什么 | 图片压缩输出 WebP/JPEG |
| FR-007 / FR-012 | §7 简洁优先 · §3.2 约束 | 不预埋抽象；大图分批/采样防 OOM |
| FR-008 | §1.1 | 工具类应用要有可见结果 |
| FR-009 / FR-010 | §8 权限最小化 | 不写权限；API 29+ 才可直接写相册 |
| FR-011 | §3.3 HEIC 门槛（v1.7） | API 28+ 平台解码；24–27 提示不支持并跳过 |
| FR-013 | §4 MVI · §4-5（v1.5） | 必须继承 `BaseViewModel`，异常走 `onError` |
| FR-014 / FR-015 | §7 简洁优先 | 失败隔离与可取消是可用性底线，不引入额外框架 |
| FR-016 | §1.2 不做 | 不联网、不上传 |
| FR-017 | §1.2 · §8 隐私合规 | 纯本地处理，输出不得携带定位信息 |
| FR-018 | 宪法「质量门槛」 | 架构基建与能力封装必须有单元测试 |
| FR-019 | §6 日志（v1.3） | 统一用 Koin 提供的 `LogUtil`，禁 `android.util.Log` |

### 未能引用宪法、需要确认的三处

- **N1 / N2 / N3**（见上）——均属产品交互细节，宪法未规定，需负责人确认或由 plan 阶段给默认值。
