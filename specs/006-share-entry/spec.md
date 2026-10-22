# Feature Specification: 系统分享入口（share-entry）

**Feature Branch**: `feature_foundation1`
**Directory**: `specs/006-share-entry`
**Created**: 2026-10-22
**Status**: Implemented (retro-spec)
**Input**: 用户要求“其他 App 分享图片/视频/PDF 到 Foolproof，能直接进入对应能力，图片分享后自动压缩”。

> 追溯说明：该能力已上线，本文档按**当前实际行为**补齐，不作为新需求排期依据。
> 宪法 §10；相关实现：`MainActivity`、`SharedContentStore`、`FoolproofScreen`。

## 概述

Foolproof 注册系统分享入口，接收其他 App 通过 `ACTION_SEND` / `ACTION_SEND_MULTIPLE` 发来的
图片、视频、PDF。进入 App 后自动切到对应 tab，并把分享内容交给现有 feature；图片分享后
**自动开始压缩**，视频进入压缩页，PDF 进入 PDF 工具。

三条硬边界：

- 不把分享内容上传到任何服务器；
- 不长期持有分享 Uri 的读权限；
- 读取失败必须给出明确提示，不能静默无响应。

---

## User Scenarios & Testing

### User Story 1 - 分享图片后自动压缩 (Priority: P1)

用户从相册/其他 App 分享一张或多张图片到 Foolproof，App 打开图片 tab，自动载入并开始压缩。

**Independent Test**：用 `ACTION_SEND` 分享一张图片，启动后断言图片 tab 被选中、图片出现在列表、
压缩任务自动开始，且成功弹层可分享/保存。

**Acceptance Scenarios**：

1. **Given** 其他 App 分享一张图片，**When** Foolproof 启动，**Then** 自动切到图片 tab 并载入该图。
2. **Given** 图片已载入，**When** 页面稳定，**Then** 自动开始压缩，不需要用户再点“开始压缩”。
3. **Given** 分享多张图片（`ACTION_SEND_MULTIPLE`），**Then** 按批量流程处理，顺序与分享顺序一致。
4. **Given** 分享的 Uri 无法读取，**Then** 显示读取失败提示，不崩溃、不静默。

### User Story 2 - 分享视频进入视频压缩 (Priority: P1)

用户分享一个视频，App 切到视频 tab 的“压缩”页并载入视频。

**Acceptance Scenarios**：

1. **Given** 分享一个视频，**When** 启动，**Then** 视频 tab 被选中，模式为“压缩”，视频已载入。
2. **Given** 视频格式不受支持，**Then** 明确提示失败，不进入空页面。

### User Story 3 - 分享 PDF 进入 PDF 工具 (Priority: P1)

用户分享一个 PDF，App 切到转换 tab 的 PDF 工具，并载入该 PDF。

**Acceptance Scenarios**：

1. **Given** 分享 PDF，**When** 启动，**Then** 转换 tab 的 PDF 工具页打开，文件已载入。
2. **Given** 分享 Uri 只有一次性授权，**Then** App 先复制到缓存再处理，后续操作不因授权过期失败。
3. **Given** PDF 无法打开（损坏/加密/权限），**Then** 显示明确的“PDF 处理失败”提示，不静默。

### User Story 4 - 分享入口不打扰正常启动 (Priority: P2)

用户从桌面图标正常启动时，不应被分享流程影响。

**Acceptance Scenarios**：

1. **Given** 正常点击图标启动，**Then** 不触发分享载入，按正常 tab 状态进入。
2. **Given** 分享处理完成后再次正常启动，**Then** 不重复处理旧分享内容。

---

## Functional Requirements

- **FR-001**：Manifest 必须注册 `ACTION_SEND` 与 `ACTION_SEND_MULTIPLE`，并声明支持的 MIME：
  `image/*`、`video/*`、`application/pdf`。
- **FR-002**：收到图片后必须切到图片 tab，载入图片，并自动开始压缩。
- **FR-003**：收到视频后必须切到视频 tab 的压缩模式并载入视频。
- **FR-004**：收到 PDF 后必须切到转换 tab 的 PDF 工具并载入 PDF。
- **FR-005**：PDF 必须在处理前复制到应用缓存；不得假设分享 Uri 的授权长期有效。
- **FR-006**：多选必须复用现有批量流程，不得另写一套压缩/转换逻辑。
- **FR-007**：分享内容只用于当次处理，处理完成后不得继续持有 Uri 权限。
- **FR-008**：读取失败、格式不支持、PDF 损坏/加密必须走可感知的错误提示。
- **FR-009**：分享入口不得引入新的存储权限。

## Success Criteria

- **SC-001**：分享一张图片后，10 秒内能看到压缩进度或成功结果（测试素材约 2MB）。
- **SC-002**：分享 PDF 后能成功打开并完成一次合并/拆分/转图片操作，且不因 Uri 授权失败。
- **SC-003**：分享非法/不可读内容时，100% 有错误提示，0 崩溃。
- **SC-004**：正常启动路径不出现分享内容或额外页面。

## Assumptions & Out of Scope

- 不实现 `ACTION_VIEW`（打开文件）入口；
- 不实现拖拽/多窗口分享；
- 不支持 ZIP、Office 文档分享；
- 分享来源的 App 必须提供可读 Uri。
