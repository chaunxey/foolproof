# Specs 索引

本目录存放 spec-kit 规格。每个 feature 一个目录，至少包含 `spec.md`；
已实现的功能另有 `plan.md` / `tasks.md`（历史功能），追溯补齐的功能标注 `Implemented (retro-spec)`。

| 目录 | 范围 | 状态 |
| --- | --- | --- |
| [001-base-module](001-base-module/spec.md) | MVI、Koin、日志、网络基座 | 已实现 |
| [002-image-compression](002-image-compression/spec.md) | 图片压缩（含目标体积、EXIF、保存幂等） | 已实现 |
| [003-duplicate-cleanup](003-duplicate-cleanup/spec.md) | 重复照片扫描、分组、删除 | 已实现 |
| [004-video-compression](004-video-compression/spec.md) | 视频压缩 | 已实现 |
| [005-file-conversion](005-file-conversion/spec.md) | 图片/文本 → PDF | 已实现 |
| [006-share-entry](006-share-entry/spec.md) | 系统分享入口 | 已实现（追溯） |
| [007-pdf-tools](007-pdf-tools/spec.md) | PDF 合并/拆分/旋转/页面/压缩/转图片 | 已实现（追溯） |
| [008-auth-settings](008-auth-settings/spec.md) | 本地模拟登录、抽屉、设置、DataStore | 已实现（追溯） |
| [009-video-editing](009-video-editing/spec.md) | 视频拼接、裁剪、三模式 Pager | 已实现（追溯） |
| [010-ad-sdk-provider](010-ad-sdk-provider/spec.md) | 可切换广告 SDK（Meta / 穿山甲） | 已实现（feature 分支） |
| [011-rewarded-unlock](011-rewarded-unlock/spec.md) | 每日免费 + 激励视频解锁次数（取代 010 的自动门控） | 已实现（feature 分支） |

> 治理规则见 [.specify/memory/constitution.md](../.specify/memory/constitution.md) §13：
> 新增功能/行为变更必须补 spec、plan、tasks；纯 bugfix/样式/性能可直接修。
