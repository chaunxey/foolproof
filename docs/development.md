# 开发流程

## 规格驱动

已初始化 [spec-kit](https://github.com/github/spec-kit)（1.2.2，DSH 集成）：

```
constitution → specify → plan → tasks → implement
```

- 宪法：[`.specify/memory/constitution.md`](.specify/memory/constitution.md)（最高约束）；
- 特性规格：`specs/<feature>/`；
- 原始需求：[`docs/requirements.md`](requirements.md)（已被宪法取代，仅历史留存）。

新功能从 `/speckit.specify` 开始。动手前先读宪法：技术栈、模块边界、MVI、权限与隐私红线都在里面。

## 提交

[Conventional Commits](https://www.conventionalcommits.org/)：

```
feat(video): 按目标体积压缩
fix(pdf): 删除按钮位置
docs: 拆分 README
refactor / test / chore
```

- `main` 为稳定基线，功能走 `feature/<name>`；
- 提交前至少跑 `./gradlew :app:testDebugUnitTest`；
- 构建脚本/依赖变更同时更新 `gradle/libs.versions.toml`。

## 文档

- 产品说明：[README.md](../README.md)
- 架构：[architecture.md](architecture.md)
- 功能：[features.md](features.md)
- 构建发布：[build-and-release.md](build-and-release.md)
- 多语言与广告：[localization-and-ads.md](localization-and-ads.md)
- 命名：[naming.md](naming.md)
- 机会评估：[feature-opportunities.md](feature-opportunities.md)

文档随代码一起提交，避免 README 再次变成“什么都有”的大杂烩。
