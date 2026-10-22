# 开发交接（Handoff）

> 新会话、换人或隔一段时间继续开发时，先读本文档，再读宪法与 specs。
> 本文档只记录**当前状态与流程**，不替代宪法/规格；冲突时以宪法为准。

## 1. 工作区

```
H:\AndroidDevelop\workplace\Foolproof
```

仓库：`https://github.com/chaunxey/foolproof`

## 2. 先读什么

按顺序：

1. [.specify/memory/constitution.md](../.specify/memory/constitution.md)（v1.18：本地优先、权限、模块、MVI、spec 治理）
2. [specs/README.md](../specs/README.md)（001–009 规格索引）
3. [docs/architecture.md](architecture.md)（模块、数据流、依赖选择）
4. [docs/features.md](features.md)（已上线行为与已知限制）
5. 当前需求对应的 `specs/00x-*/spec.md`

不要凭印象改代码；涉及行为先确认对应 REQ。

## 3. 当前状态（2026-10-22）

- 分支：`main`
- 最新提交：以 `git log -1 main` 为准（本文档会随提交更新）
- 最新 Release：`v1.1.0`（Release 源提交 `29916fb`）
  - APK：`Foolproof-v1.1.0.apk`（14,261,641 bytes）
  - AAB：`Foolproof-v1.1.0.aab`（18,330,097 bytes）
  - 签名证书 SHA-256：`2e6c62554cb1d7939f3babaa576b9f373341c609d139c1fd04b362c422cc0dc1`
- 追溯文档分支：`spec/constitution-retro`
- 当前版本：`versionCode 2` / `versionName 1.1.0`
- minSdk 24 / compileSdk 37 / targetSdk 37

### 已上线能力

- 图片：压缩、目标 KB、EXIF 移除、成功预览、保存幂等；
- 重复照片：dHash + 相关性兜底、分组卡片、回收站/SAF 删除；
- 视频：压缩、拼接、裁剪、三模式 Pager；
- 转换：图片/文本→PDF、PDF 合并/拆分/旋转/页面/压缩/转图片；
- 系统分享入口：图片/视频/PDF；
- 本地模拟登录（验证码 `0000`、密码 `admin`，无后端）、抽屉、设置、DataStore；
- 6 语言、深色模式、广告占位/接入。

### 已知边界

- 登录是本地模拟，不发真实短信，没有服务端账号；
- PDF 压缩有损，文字层会丢失；加密 PDF 不支持；
- 未配置 FAN placement ID 时横幅是占位块；
- Play Console、关闭测试、正式广告 ID、隐私政策尚未完成（如需上架再单独排期）。

## 4. 开发流程（必须遵守）

### 分级

| 级别 | 示例 | 流程 |
| --- | --- | --- |
| L0 热修 | 崩溃、阻塞 | 直接修 + 测试 + 事后记录 |
| L1 bugfix/样式/性能 | 卡顿、间距、文案 | 直接改 + 构建/测试，不强制 spec |
| L2 行为变更 | 保存规则、识别阈值、交互变更 | `specify update` → plan review → implement → verify |
| L3 新功能/依赖 | 新页面、新格式、新 SDK | 完整 spec → plan → tasks → implement → verify |

### 五步（L2/L3）

1. 用户只描述改动点；
2. 先 `/speckit.specify update`，**只改对应 REQ 条目**，不动代码；
3. `/speckit.plan review`：确认技术方案是否变化；
4. 用户明确 `/speckit.implement` 后，才增量改代码；
5. `/speckit.verify` 逐条对照新 spec 验证，不能用“能编译/截图正常”代替。

**在用户明确 implement 之前，不要改业务代码。**

## 5. 仓库约定

- Conventional Commits：`feat/fix/docs/refactor/perf/style/chore/test`；
- 不改无关文件；`.dsh/`、`.idea/`、`.specify/presets/` 是本地工具/IDE 变更，默认不提交；
- 提交前确认 `git status`，只 add 本次相关文件；
- 构建：`.\gradlew.bat :app:assembleDebug`；
- 发布：`.\gradlew.bat :app:assembleRelease :app:bundleRelease`（需 `keystore.properties`）；
- 真机序列号：`10AG9C079Y001L0`；模拟器：`emulator-5554`；
- GitHub 推送网络不稳定时，先试代理；失败后 unset 代理并 `git -c http.version=HTTP/1.1 push`，完成后恢复代理。

## 6. 新会话开场模板

```text
先读 docs/handoff.md，然后按里面的状态和流程执行。

需求：
<只写改动点>

先不要改代码，等我确认 plan review 后再 implement。
```
