# Feature Specification: PDF 工具（pdf-tools）

**Feature Branch**: `feature_foundation1`
**Directory**: `specs/007-pdf-tools`
**Created**: 2026-10-22
**Status**: Implemented (retro-spec)
**Input**: “PDF 合并/拆分/旋转/页面管理/压缩，以及 PDF 转图片后预览、分享、保存。”

> 追溯说明：该能力已上线，归属于 `:features:file-conversion`；本文档按当前实际行为补齐。
> 宪法 §8.2。原 `005-file-conversion` 只覆盖“生成 PDF”，PDF 工具作为独立增量记录。

## 概述

用户选择一个或多个本地 PDF，在转换 tab 的“PDF 工具”中完成：

1. 合并；2. 拆分；3. 旋转；4. 页面管理；5. 压缩；6. 转图片。

全部处理在本地完成，底层使用 PDFBox-Android；不申请写权限，结果写入应用私有目录后
通过分享/保存下载导出。

---

## User Scenarios & Testing

### User Story 1 - 合并多个 PDF (Priority: P1)

用户选择多个 PDF，按列表顺序合并为一份。

**Acceptance Scenarios**：

1. **Given** 选择 3 个 PDF，**When** 合并，**Then** 输出页数 = 3 份页数之和，顺序与列表一致。
2. **Given** 只选了 1 个 PDF，**Then** 合并按钮不可用或明确提示至少 2 个。
3. **Given** 其中一份加密/损坏，**Then** 明确失败，不产出半成品。

### User Story 2 - 按页码范围拆分 (Priority: P1)

用户输入 `1-3,5` 这样的页码范围，导出只包含这些页的新 PDF。

**Acceptance Scenarios**：

1. **Given** 10 页 PDF，输入 `1-3,5`，**Then** 输出 4 页，顺序为 1,2,3,5。
2. **Given** 输入越界页码，**Then** 提示范围无效，不崩溃。
3. **Given** 输入非法格式，**Then** 输入框/按钮给出可感知错误。

### User Story 3 - 旋转整份 PDF (Priority: P1)

用户选择 90°/180°/270°，整份文档所有页旋转。

**Acceptance Scenarios**：

1. **Given** 选择 90°，**When** 处理完成，**Then** 每页视觉方向旋转 90°，页数不变。
2. **Given** 选择 180°/270°，**Then** 结果与选择一致。

### User Story 4 - 页面管理 (Priority: P2)

用户查看页缩略图列表，调整页顺序或删除页，再重写 PDF。

**Acceptance Scenarios**：

1. **Given** 5 页 PDF，上移第 3 页，**Then** 输出顺序为 1,2,4,3,5。
2. **Given** 删除第 2 页，**Then** 输出 4 页且原第 2 页消失。
3. **Given** 删除到 0 页，**Then** 禁止导出并提示至少保留 1 页。

### User Story 5 - 有损压缩 (Priority: P2)

用户选择高/中/低质量，把 PDF 页面栅格化为 JPEG 后重建 PDF。

**Acceptance Scenarios**：

1. **Given** 扫描件 PDF，选择低质量，**Then** 输出体积小于原文件。
2. **Given** 任意压缩，**Then** UI 必须提示“文字层会丢失/不可选中”。
3. **Given** 压缩失败，**Then** 不覆盖原文件，给出错误提示。

### User Story 6 - PDF 转图片并导出 (Priority: P1)

用户把每一页渲染成 JPEG，预览后可以一次分享全部或保存到下载目录。

**Acceptance Scenarios**：

1. **Given** 3 页 PDF，**When** 转图片，**Then** 生成 3 张 JPEG，命名包含页码。
2. **Given** 转换完成，**Then** 页面显示缩略图、页数和“分享全部/保存”入口。
3. **Given** 点击分享，**Then** 系统分享面板包含全部图片；分享失败必须有提示。
4. **Given** 点击保存，**Then** 图片写入下载目录；成功/失败都有反馈。
5. **Given** FileProvider 未配置 `pdf_images/`，**Then** 分享会失败——实现必须配置该路径，并用测试防止回归。

---

## Functional Requirements

- **FR-001**：必须支持合并、拆分、旋转、页面管理、压缩、转图片 6 种模式。
- **FR-002**：PDF 解析/写入统一使用 PDFBox-Android，不引入 iText。
- **FR-003**：页码范围必须支持逗号分隔与 `a-b` 区间，非法输入必须拒绝。
- **FR-004**：旋转必须作用于所有页，页数不变。
- **FR-005**：页面管理必须支持调整顺序与删除页，且至少保留 1 页。
- **FR-006**：压缩必须是有损栅格化，UI 必须明确告知文字层丢失。
- **FR-007**：转图片输出 JPEG，逐页命名，支持预览、全量分享、保存下载。
- **FR-008**：所有输出先写应用私有目录，再通过 FileProvider 分享；不申请写权限。
- **FR-009**：加密 PDF 当前必须明确失败，不得静默或输出空文件。
- **FR-010**：任何失败都必须恢复按钮可用状态，不能卡在“处理中”。
- **FR-011**：`FileProvider` 必须包含 `pdf_images/` 路径。

## Success Criteria

- **SC-001**：10 页以内 PDF 的合并/拆分/旋转在 10 秒内完成（中端机）。
- **SC-002**：拆分 `1-3,5` 的输出页数与顺序 100% 正确。
- **SC-003**：转图片后分享面板能收到全部图片，0 次“无响应”。
- **SC-004**：加密 PDF、非法范围、0 页删除均 100% 有可感知错误，0 崩溃。

## Assumptions & Out of Scope

- 不支持 PDF 密码输入/解密；
- 不支持 OCR、文本编辑、签名、表单填写；
- 不支持 PDF 转 Word/Excel；
- 压缩不保留文本层（产品已接受）。
