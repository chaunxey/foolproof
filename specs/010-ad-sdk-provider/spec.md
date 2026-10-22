# Feature Specification: 可切换广告 SDK（新增穿山甲模块）

**Feature Branch**: `feature_foundation1`
**Directory**: `specs/010-ad-sdk-provider`
**Created**: 2026-10-22
**Status**: Implemented（`feature/ad-sdk-provider`）
**Input**: “加一个国内的 ad sdk module，可以由开发者自由切换，选取的是穿山甲广告 sdk。”

> 级别：L3（新增依赖 + 新模块）。按宪法 §13：spec → plan → tasks → implement → verify。
> 本文件只描述**行为与约束**，技术选型见 `plan.md`。

## 概述

现状（v1.1.0）：广告只有 **Meta Audience Network（FAN）**，`AdGate` 接口在 `:base`，
实现在 `:app`（`MetaAdGate` / `BannerAd` / `SplashAdOverlay`）。Meta 在国内基本不可用，
导致国内版本没有有效广告填充。

本次要做的是：

1. **把广告能力抽象出来**（初始化、横幅、全屏门控），与具体 SDK 解耦；
2. **现有 Meta 实现原样迁出**到独立模块，行为不变；
3. **新增穿山甲（Pangle / CSJ）实现模块**（国内 SDK），覆盖现有三种广告形态
   （开屏在穿山甲下走 **原生 TTSplashAd**，Meta 仍用自绘壳）；
4. **开发者构建期一键切换** `meta` / `pangle`，每个产物只打包一个 SDK；
5. feature 模块（图片/去重/视频/转换）**零改动**，继续只用 `:base` 的 `AdGate`。

### 不做什么（本次范围外）

- ❌ 不做用户可见的广告开关——这是**开发者构建期开关**，不是设置项；
- ❌ 不接激励视频、信息流、插屏半屏等新形态；
  只覆盖现有三种：功能分割处横幅、次数门控全屏、启动/唤醒广告层；
- ❌ 不做完整的隐私同意弹窗/隐私政策页（另立 spec）；
  但穿山甲 SDK **不得在未同意隐私政策、且未配置参数时初始化**；
- ❌ 不改 Meta 的计数阈值（5 次）、展示时机、跳过时机；
- ❌ 不引第二个网络栈、不做服务端配置下发。

---

## User Scenarios & Testing

### User Story 1 - 开发者切换广告 SDK (Priority: P1)

开发者把构建开关从 `meta` 改为 `pangle`，同步后重新构建，即可得到只含穿山甲 SDK 的安装包；
功能代码、UI、计数数据都不需要改。

**Why this priority**：这是需求本身；没有它，穿山甲模块等于死代码。

**Independent Test**：分别用 `meta` / `pangle` 构建，检查产物中 SDK 的类集合互斥。

**Acceptance Scenarios**：

1. **Given** 默认配置，**When** 构建 debug 包，**Then** 使用 Meta，产物中含 `com.facebook.ads.*`，不含 `com.bytedance.sdk.openadsdk.*`。
2. **Given** 开关设为 `pangle`，**When** 构建 debug 包，**Then** 使用穿山甲，产物中含 `com.bytedance.sdk.openadsdk.*`，不含 `com.facebook.ads.*`。
3. **Given** 切换开关，**When** 构建，**Then** feature 模块不需要任何代码改动，构建成功。
4. **Given** 开关填了非法值（如 `foo`），**When** 构建，**Then** 构建失败并给出明确提示，不静默回退。

### User Story 2 - 功能分割处横幅 (Priority: P1)

图片 / 视频 / 转换三个 tab 的分割处横幅，在穿山甲下展示 320×50 模板横幅；
未配置代码位时继续显示占位块，布局不跳动。

**Independent Test**：真机/模拟器切到穿山甲，配置有效代码位后三个 tab 各出现一次横幅；
清空配置后出现占位块且无崩溃。

**Acceptance Scenarios**：

1. **Given** 穿山甲 banner 代码位已配置且网络正常，**When** 进入任一 tab，**Then** 分割处展示横幅，高度固定 50dp，不遮挡内容。
2. **Given** 参数为空，**When** 进入 tab，**Then** 显示占位块（“AD”），不发广告请求。
3. **Given** 加载失败/无网，**When** 进入 tab，**Then** 显示占位块，不崩溃、不阻塞滚动。
4. **Given** 横幅可见，**When** 停留超过 60 秒，**Then** 自动刷新一次（与 Meta 现状一致）。
5. **Given** 离开 tab/销毁页面，**Then** 横幅释放（不泄漏 View、不后台请求）。

### User Story 3 - 成功次数门控 (Priority: P1)

每个功能独立计数，成功使用 5 次后，下一次使用前展示全屏广告；关闭后放行并清零。
广告加载失败、超时、未配置时**一律放行**，绝不卡死核心功能。

**Independent Test**：单测覆盖门控策略的 5 个分支；真机上把计数刷到 5，验证全屏广告展示与关闭放行。

**Acceptance Scenarios**：

1. **Given** 某功能成功使用 4 次，**When** 第 5 次使用前，**Then** 不展示广告，直接放行。
2. **Given** 已成功使用 5 次，**When** 第 6 次使用前，**Then** 展示全屏广告，关闭后放行，计数清零。
3. **Given** 广告加载失败，**Then** 立即放行并清零，功能不受影响。
4. **Given** 广告在约定超时内未就绪（5 秒），**Then** 放行并清零，且只回调一次 `onAllow`。
5. **Given** 未配置代码位，**Then** 不请求广告，直接放行并清零（与 Meta 现状一致）。
6. **Given** 同一功能连续多次触发，**Then** 计数按次累加，互不串号；不同功能各自独立。

### User Story 4 - 启动/唤醒广告层 (Priority: P2)

冷启动必展示一次广告层，后台超过 1 小时回来再展示：

- **穿山甲**：优先展示**原生开屏（TTSplashAd）**，倒计时与跳过由 SDK 渲染；
- **Meta**：沿用现有自绘 5 秒壳（横幅内容、3 秒后可跳过）；
- **穿山甲加载失败/超时/未配置**：回退到自绘壳，保证冷启动始终有广告层，不卡在空白加载态。

**Independent Test**：真机冷启动、切后台 1 小时以上再回来；分别验证 pangle（有代码位 / 空配置）与 meta。

**Acceptance Scenarios**：

1. **Given** pangle 模式且开屏代码位有效，**When** 冷启动，**Then** 展示穿山甲原生开屏，SDK 自带倒计时与跳过，跳过或倒计时结束进入应用。
2. **Given** pangle 模式但加载失败或 2 秒内未就绪，**Then** 回退到自绘 5 秒壳，不出现白屏/卡死。
3. **Given** meta 模式，**Then** 与原行为一致：5 秒广告层、3 秒后可跳过。
4. **Given** 未配置代码位，**Then** 显示占位块，5 秒后正常进入应用。
5. **Given** 后台停留 > 1 小时回到前台，**Then** 再次展示；< 1 小时不展示。

### User Story 5 - Meta 行为回归 (Priority: P1)

切回 `meta` 构建后，横幅、门控、开屏的行为与 v1.1.0 完全一致（除新增的超时兜底）。

**Independent Test**：对照验收清单逐项打勾，真机跑一遍门控与横幅。

**Acceptance Scenarios**：

1. **Given** `meta` 构建，**When** 完成一次图片压缩，**Then** 成功计数 +1。
2. **Given** 计数到 5，**When** 再次压缩，**Then** 展示 Meta 插屏，关闭后放行。
3. **Given** Meta 未配置 placement ID，**Then** 横幅占位、门控放行，与 v1.1.0 一致。

### 边界情况

- **参数为空/只有部分代码位**：视为未配置——不初始化 SDK、不请求广告、显示占位、门控放行；
- **SDK 初始化失败或超时**：不阻塞启动，后续广告请求直接降级；
- **无网络/弱网**：加载失败 → 占位/放行，最多等待约定的 5 秒；
- **两个 provider 同时进 classpath**：构建必须失败（防止 APK 里塞两个广告 SDK）；
- **release 混淆**：SDK 自带 keep 规则必须生效；provider 实现类不得被裁掉；
- **低内存/快速切 tab/旋转**：横幅 View 正确销毁重建，不泄漏、不重复叠加；
- **权限合并**：SDK 附带的敏感权限必须按 FR-012 剔除，不随依赖悄悄进入 Manifest；
- **合规**：未同意隐私政策前不初始化穿山甲、不采集设备标识。

---

## Functional Requirements

### 模块与抽象

- **FR-001**：新增 `:ads:api`（Android Library，**不含任何具体 SDK 依赖**），提供：
  `AdSdk`（初始化 / 横幅 / 门控 / provider 标识）、`AdSdkConfig`（是否配置齐全）、
  `AdBannerSlot`（View + 刷新 + 释放）、共享门控策略、进程级 registry。
- **FR-002**：新增 `:ads:meta`，把现有 Meta 实现（`MetaAdGate`、FAN 横幅、初始化、`AdsConfig`）
  原样迁入；对外只暴露 `AdSdk` 实现。
- **FR-003**：新增 `:ads:pangle`，实现同一 `AdSdk` 接口，依赖穿山甲国内 SDK（版本在 plan 中锁定）；
  覆盖初始化 + 横幅 + 全屏门控 + **原生开屏**（见 FR-010）。
- **FR-004**：`:app` 只依赖 `:ads:api` 与**当前选中的**一个 provider 模块；provider 由构建开关选择，
  开关值只允许 `meta` / `pangle`（大小写不敏感），默认 `meta`。
- **FR-005**：同一构建产物 MUST 只包含一个广告 SDK；该约束 MUST 可用命令验证
  （依赖树或 APK 类列表），且两个 provider 同时出现时构建失败。
- **FR-006**：feature 模块（`:features:*`）MUST 零改动，继续只依赖 `:base` 的 `AdGate` / `FeatureId`。
- **FR-007**：广告抽象 MUST NOT 依赖 Compose（横幅以 Android `View` 形式提供），
  Compose 侧渲染与占位逻辑仍留在 `:app`。

### 行为

- **FR-008**：横幅 MUST 为 320×50 固定高度；加载成功前与失败时显示占位块，布局不跳动；
  可见期间每 60 秒刷新一次；离开页面 MUST 释放（`destroy()`），不得泄漏原生 View 或后台请求。
- **FR-009**：门控阈值固定为 **5 次成功使用**（与现状一致）；计数按 `FeatureId` 独立持久化；
  达到阈值后下一次使用前展示全屏广告，关闭后放行并清零。
- **FR-010**：启动/唤醒广告层的**触发时机**不变（冷启动必展示；后台 > 1 小时回来再展示）：
  - **穿山甲**：MUST 优先展示原生开屏（`TTSplashAd`），倒计时/跳过由 SDK 渲染；
    加载失败、2 秒内未就绪或未配置时 MUST 回退到自绘 5 秒壳，不得停留在空白加载态；
  - **Meta**：MUST 保持现有自绘 5 秒壳（横幅内容、3 秒后可跳过）不变。
- **FR-011**：初始化 MUST 幂等且只针对当前 provider；参数为空时 MUST NOT 初始化 SDK；
  debug 构建 MUST 使用各自 SDK 的测试/调试模式；初始化失败 MUST NOT 影响应用启动与核心功能。
- **FR-012**：Manifest 合并后 MUST NOT 新增以下敏感权限：定位、通讯录、短信、
  `READ_PHONE_STATE`、`WRITE_EXTERNAL_STORAGE`、`REQUEST_INSTALL_PACKAGES`、`QUERY_ALL_PACKAGES`；
  `INTERNET` / `ACCESS_NETWORK_STATE` / `AD_ID` 保留。若某权限为 SDK 强制要求，必须在 plan review 单独确认。
- **FR-013**：各 provider 的配置（appId、banner/interstitial/splash 代码位、debug 开关）
  集中在各自模块内，空串即“未配置”；配置 MUST NOT 要求改 `:app` 业务代码。
- **FR-014**：门控 MUST 在 **5 秒**内给出结果（展示或放行），且 `onAllow` 只回调一次；
  该超时兜底对两个 provider 一致生效（这是相对 v1.1.0 的唯一行为增强）。
- **FR-015**：provider 切换后，已持久化的成功计数 MUST 继续有效（同一套 key，不按 SDK 分叉）。

### 治理与验证

- **FR-016**：宪法升到 v1.19：§2 技术栈登记穿山甲依赖与“单产物只打一个 SDK”的约束；
  §3 模块结构加入 `:ads:api` / `:ads:meta` / `:ads:pangle` 与依赖方向；§11 补充敏感权限剔除要求。
- **FR-017**：`docs/architecture.md`、`docs/features.md`、`docs/localization-and-ads.md`、
  `docs/handoff.md` 必须同步“如何切换”“如何配置”“已知限制”。
- **FR-018**：`:ads:api` 的门控策略 MUST 有 JVM 单测（不依赖 Android/SDK）；
  `meta` / `pangle` 两种开关各完成一次 debug 构建；至少一台真机或模拟器跑通
  横幅、门控、开屏三条路径（真机 `10AG9C079Y001L0`，模拟器 `emulator-5554`）。
- **FR-019**：release 构建 MUST 保留 SDK 必需类（keep 规则），且不把未选中的 provider 打进包。

---

## Success Criteria

### Measurable Outcomes

- **SC-001**：`./gradlew :app:assembleDebug -Pfoolproof.adSdk=pangle` 与
  `-Pfoolproof.adSdk=meta` 均构建成功；用 `:app:dependencies` 或 APK 类列表验证
  `com.facebook.ads` 与 `com.bytedance.sdk.openadsdk` **互斥出现**。
- **SC-002**：`git diff --stat features/` 为空（feature 模块零改动）。
- **SC-003**：清空穿山甲参数后，App 冷启动、三 tab 切换、五个功能各用一次均无崩溃；
  横幅为占位、门控全部放行（真机/模拟器手动验证）。
- **SC-004**：`:ads:api` 单测覆盖“<5 放行 / =5 展示 / 加载失败放行 / 超时放行 / 关闭清零”
  五个场景，全部通过。
- **SC-005**：release Manifest 与 v1.1.0 对比，无 FR-012 列出的敏感权限新增。
- **SC-006**：切回 `meta` 后，按 User Story 5 的 3 条验收场景逐项通过，行为与 v1.1.0 一致。
- **SC-007**：构建期切换只需改一处配置 + 一次 sync，不需要改任何 Kotlin 业务代码。

---

## Key Entities

- **AdSdk**：广告能力入口（`id`、`init`、`isConfigured`、`createBanner`、`maybeGate`、`onSuccess`）。
- **AdProviderId**：`META` / `PANGLE`。
- **AdBannerSlot**：原生横幅封装（View、刷新、释放、加载状态回调）。
- **SplashAdSlot**：原生开屏封装（View、关闭回调、释放）；Meta 返回 `null`，走自绘壳。
- **AdGateConfig**：阈值（5）、超时（5 秒）。
- **AdUsage**：成功计数存储（由 `:app` 的 `UsageStore` 实现，底层 DataStore）。
- **MetaAdsConfig** / **PangleAdsConfig**：各自 appId 与代码位。
- **AdSdkRegistry**：进程级当前 provider 实例（供 `:app` 的 Compose 使用）。

---

## 假设

- 穿山甲国内 SDK 通过**官方 Maven** 接入：仓库 `https://artifact.bytedance.com/repository/pangle`，
  坐标 `com.pangle.cn:ads-sdk-pro:7.8.1.5`（已核实 POM/metadata 可访问，无需下载 AAR）；
  仓库默认不带真实 appId / 代码位。
- 版本在 plan 中锁定，且需满足 `minSdk 24`。
- 构建开关是**开发者构建期**行为，不进入设置页、不随用户切换。
- **开屏形态已拍板（2026-10-22）**：穿山甲走原生 `TTSplashAd`；Meta 保持自绘 5 秒壳；
  穿山甲不可用时回退自绘壳。
- 计数阈值、开屏触发时机、横幅刷新周期保持现状，不借本次调整产品策略。
- 本次不引入广告聚合（GroMore）、不接第二个国内 SDK。
