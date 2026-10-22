# Feature Specification: 视频拼接与裁剪（video-editing）

**Feature Branch**: `feature_foundation1`
**Directory**: `specs/009-video-editing`
**Created**: 2026-10-22
**Status**: Implemented (retro-spec)
**Input**: “视频 tab 增加拼接与裁剪；压缩/拼接/裁剪可左右滑动切换；拼接 2–10 段、可排序，裁剪只保留时间区间。”

> 追溯说明：`004-video-compression` 覆盖压缩；本文档补齐其后上线的拼接、裁剪与
> 三模式 Pager。实现归属 `:features:video-compression` + `:app` 的 `VideoTabScreen`。

## 概述

视频 tab 有三个并列模式：**压缩 / 拼接 / 裁剪**。三者共用同一个页面容器：

- 顶部胶囊与 `HorizontalPager` 双向同步；
- 左右滑动切换模式，点击胶囊也切换；
- 每个模式独立纵向滚动；
- 左侧抽屉手势只在“压缩”页保留；拼接/裁剪页横向手势归 Pager。

---

## User Scenarios & Testing

### User Story 1 - 三模式滑动切换 (Priority: P2)

用户左右滑动在压缩/拼接/裁剪之间切换。

**Acceptance Scenarios**：

1. **Given** 当前在压缩，**When** 左滑，**Then** 切到拼接，胶囊同步高亮。
2. **Given** 当前在拼接，**When** 右滑，**Then** 回到压缩。
3. **Given** 点击“裁剪”，**Then** 页面滑到裁剪，胶囊同步。
4. **Given** 在拼接/裁剪页，**Then** 左边缘横向滑动由 Pager 处理，不打开抽屉。
5. **Given** 在压缩页，**When** 从左侧 24dp 边缘右滑，**Then** 抽屉打开。

### User Story 2 - 拼接 2–10 段视频 (Priority: P1)

用户选择多个视频，调整顺序，拼接为一个文件。

**Acceptance Scenarios**：

1. **Given** 选择 2 个视频，**When** 开始拼接，**Then** 输出时长约等于两段之和，顺序与列表一致。
2. **Given** 选择超过 10 个，**Then** 拒绝/截断并提示上限。
3. **Given** 同一视频被重复加入，**Then** 自动跳过重复项。
4. **Given** 3 段视频，**When** 上移第 3 段，**Then** 输出顺序随列表变化。
5. **Given** 删除中间一段，**Then** 剩余段按列表顺序拼接。
6. **Given** 拼接中取消，**Then** 停止任务并清理半成品。
7. **Given** 输出完成，**Then** 支持预览、分享、存相册。

### User Story 3 - 裁剪时间区间 (Priority: P1)

用户拖动双滑块选择 start–end，只保留该区间。

**Acceptance Scenarios**：

1. **Given** 10 秒视频，选择 2–6 秒，**Then** 输出约 4 秒。
2. **Given** 区间小于 0.5 秒，**Then** 不允许开始，提示最小时长。
3. **Given** 选择整段，**Then** 输出与原视频时长一致。
4. **Given** 裁剪完成，**Then** 支持预览/分享/存相册，文件名包含 `TRIM`。
5. **Given** 关键帧对齐，**Then** 允许 `trimOptimization` 避免重编码；否则正常转码。

### User Story 4 - 兼容性回退 (Priority: P2)

部分机型多段同时硬解会失败。

**Acceptance Scenarios**：

1. **Given** API ≥ 31 且硬解失败，**Then** 开启解码器回退并重试。
2. **Given** API < 31，**Then** 走“逐段转码再拼接”的兼容路径。
3. **Given** 所有路径都失败，**Then** 明确提示失败，不产出损坏文件。

---

## Functional Requirements

- **FR-001**：视频 tab 必须支持压缩、拼接、裁剪三种模式。
- **FR-002**：模式切换必须同时支持点击胶囊与左右滑动，且状态双向同步。
- **FR-003**：拼接支持 2–10 段；超过上限必须提示；重复视频自动跳过。
- **FR-004**：拼接列表必须支持排序与删除，输出顺序与列表一致。
- **FR-005**：裁剪只保留 start–end，最小 0.5 秒；不改变压缩档位。
- **FR-006**：处理必须提供进度与取消；取消后清理半成品。
- **FR-007**：结果必须支持预览、分享、存相册；不申请写权限。
- **FR-008**：视频页抽屉手势只在压缩页生效；其他页把横向手势留给 Pager。
- **FR-009**：抽屉打开后必须能通过遮罩/返回关闭，不能卡死。
- **FR-010**：多段硬解失败时必须有兼容路径或明确失败，不能崩溃。
- **FR-011**：输出文件命名必须可区分拼接/裁剪/压缩。

## Success Criteria

- **SC-001**：2 段 10 秒视频拼接在 30 秒内完成（中端机，硬解路径）。
- **SC-002**：裁剪 2–6 秒的输出时长误差 ≤ 0.3 秒。
- **SC-003**：三模式滑动切换 10 次无卡死、无手势冲突。
- **SC-004**：取消拼接后不留下可播放的半成品。
- **SC-005**：重复加入同一视频时，列表不出现重复项。

## Assumptions & Out of Scope

- 不做多轨、转场、滤镜、变速、字幕、音频混合；
- 拼接不保证不同分辨率/帧率素材的完美兼容，必要时统一转码；
- 不引入 FFmpeg；
- 不做视频转 GIF。
