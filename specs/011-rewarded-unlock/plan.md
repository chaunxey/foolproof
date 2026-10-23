# Implementation Plan: 激励视频解锁次数（rewarded-unlock）

**Feature**: `specs/011-rewarded-unlock/spec.md`
**Created**: 2026-10-23
**Constitution**: v1.19（广告 provider、权限、MVI）
**Status**: 已实施（待补充真机完整奖励与跨天验收）

## 1. 技术上下文

| 项 | 值 |
|---|---|
| 现有门控 | `:base` `AdGate.maybeGate` + `:ads:api` `CountingAdGate`（自动插全屏，5 次清零） |
| 新额度模型 | 每日免费 5 次/功能（0 点重置、不累计）+ 激励余额（+5、可叠加、不清零） |
| 激励 SDK | 穿山甲 `loadRewardVideoAd` / `TTRewardVideoAd`（广告位 `104722138`）；Meta `RewardedVideoAd` |
| 持久化 | 复用 `:common` 的 DataStore `LocalStore` |
| UI | 共享 `RewardUnlockDialog` + 5 个功能按钮文案切换 |
| 失败策略 | 失败/无填充/8 秒超时 → 放行当前一次（不扣额度）+ 60 秒冷却 |
| 构建开关 | 不变（`foolproof.adSdk=meta|pangle`） |

## 2. 额度模型

### 2.1 纯逻辑 `Quota`（`:ads:api`，可 JVM 单测）

```kotlin
data class Quota(
    val dayKey: String,          // 本地时区 yyyy-MM-dd
    val freeUsed: Int,           // 今日已用免费次数 0..5
    val bonus: Int,              // 激励余额，>= 0，跨天保留
) {
    val freeRemaining: Int get() = (DAILY_FREE - freeUsed).coerceAtLeast(0)
    val available: Int get() = freeRemaining + bonus

    fun rollTo(today: String): Quota    // dayKey != today → freeUsed = 0，bonus 不动
    fun consume(): Quota                // 先 freeUsed+1（<5），否则 bonus-1
    fun grant(amount: Int): Quota       // bonus + amount
}
```

常量：`DAILY_FREE = 5`、`REWARD_AMOUNT = 5`、`FAIL_COOLDOWN_MS = 60_000`、`LOAD_TIMEOUT_MS = 8_000`。

### 2.2 存储（`:app` `UsageStore`）

每个功能三个 key（DataStore）：

```
free_day_<FEATURE>   String  "yyyy-MM-dd"（设备本地时区）
free_used_<FEATURE>  Int     今日已用免费次数
bonus_<FEATURE>      Int     激励余额（永不被日期重置）
```

- 读取时惰性跨天：`quota.rollTo(today)`，必要时写回；
- `MainActivity.onStart` 调一次 `UsageStore.refreshAll()`，保证 App 挂着跨天回前台也刷新；
- 旧 `uses_<FEATURE>` 键删除（feature 分支未发布，无迁移）；
- 每次变更后同步 `AdGateState`（见 2.3）。

### 2.3 锁定态（`:base`）

```kotlin
const val USES_BEFORE... // 不需要，额度全在 Quota

interface AdGate {
    fun onSuccess(feature: FeatureId)                       // 成功后扣 1
    fun available(feature: FeatureId): Int
    fun isLocked(feature: FeatureId): Boolean               // available <= 0
    fun requestUnlock(activity: Activity, feature: FeatureId, onResult: (Boolean) -> Unit)
}

object AdGateState {
    val available: StateFlow<Map<FeatureId, Int>>           // UI 观察
    fun update(feature: FeatureId, available: Int)
}
```

## 3. 解锁流程（`RewardedGate`）

```kotlin
class RewardedGate(
    private val usage: AdUsage,
    private val config: AdGateConfig,
    private val loaderFactory: (Activity) -> RewardedAdLoader,
    private val schedulerFactory: () -> DelayScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : AdGate
```

`requestUnlock`：

1. `available > 0` → `onResult(true)`；
2. 未配置广告 → `onResult(true)`（放行、不扣额度）；
3. 60 秒失败冷却内 → `onResult(true)`；
4. 8 秒看门狗 → 超时记失败、放行；
5. 加载成功 → `show`：
   - `rewarded = true` → `usage.grantBonus(5)` + `onResult(true)`；
   - `rewarded = false` → `onResult(false)`；
6. 加载失败 → 记失败时间 + 放行；
7. `AtomicBoolean` 保证 `onResult` 只一次，迟到回调丢弃。

`onSuccess` → `usage.consume()`（先免费后激励）→ 更新 `AdGateState`。

## 4. UI

### 4.1 共享弹窗（`:common`）

```kotlin
@Composable
fun RewardUnlockDialog(feature: FeatureId, onDismiss: () -> Unit, onAllowed: () -> Unit)
```

- 初始：标题「解锁更多次数」+「观看一段广告，解锁 5 次使用」+【取消】【观看广告】；
- 点击后：按钮加载态，文案「广告加载中…」，不可重复触发；
- 奖励成功/失败放行 → `onAllowed()`；无奖励 → `onDismiss()`；
- 文案进 `:common` 的 6 语言 `strings.xml`。

### 4.2 5 个功能

| 功能 | 文件 | 按钮 |
|---|---|---|
| 图片压缩 | `features/image-compression/.../CompressionSection.kt` | 开始压缩 |
| 重复清理 | `features/duplicate-cleanup/.../DuplicateHeader.kt` | 查询相同图片 |
| 视频压缩 | `features/video-compression/.../VideoCompressionSection.kt` | 开始压缩 |
| 视频拼接 | `features/video-compression/.../VideoMergeSection.kt` | 开始拼接 |
| 文件转换 | `features/file-conversion/.../FileConversionSection.kt` | 生成 PDF |

统一改法：

```kotlin
val available = AdGateState.available.collectAsState().value[feature] ?: 5
val locked = available <= 0
var showUnlock by remember { mutableStateOf(false) }
Button(onClick = { if (locked) showUnlock = true else start() }) {
    Text(if (locked) stringResource(R.string.ad_unlock_button, 5) else normalLabel)
}
if (showUnlock) {
    RewardUnlockDialog(feature, onDismiss = { showUnlock = false },
        onAllowed = { showUnlock = false; start() })
}
```

## 5. Provider

### 5.1 穿山甲

- `PangleAdsConfig.INTERSTITIAL_CODE_ID` → `REWARDED_AD_ID = "104722138"`（激励视频广告位）；
- `PangleRewardedLoader`：
  `loadRewardVideoAd(AdSlot + MediationAdSlot, RewardVideoAdListener)`；
  `TTRewardVideoAd.setRewardAdInteractionListener` 中
  `onRewardVerify` / `onRewardArrived` 置 rewarded，`onAdClose` 回调 `onFinished(rewarded)`；
  `show` → `showRewardVideoAd(activity)`；参数空值防护。

### 5.2 Meta

- `MetaAdsConfig` 增加 `REWARDED_PLACEMENT_ID`；
- `MetaRewardedLoader`：`RewardedVideoAd#loadAd()`；
  `onReward` 置 rewarded，`onAdClosed` 回调；未配置 → `isConfigured = false`。

## 6. 文件清单

### 新增

```
specs/011-rewarded-unlock/{spec,plan,tasks}.md
ads/api/src/main/java/com/foolproof/ads/api/Quota.kt              // Quota + AdUsage
ads/api/src/main/java/com/foolproof/ads/api/RewardedAd.kt         // RewardedAd / Loader / RewardedGate
ads/api/src/test/java/com/foolproof/ads/api/QuotaTest.kt
ads/api/src/test/java/com/foolproof/ads/api/RewardedGateTest.kt
common/src/main/java/com/foolproof/common/ui/RewardUnlockDialog.kt
ads/meta/src/main/java/com/foolproof/ads/meta/MetaRewardedLoader.kt
ads/pangle/src/main/java/com/foolproof/ads/pangle/PangleRewardedLoader.kt
```

### 修改

```
base/.../ads/AdGate.kt                        AdGate v2 + AdGateState；删除 maybeGate/gateOrRun
ads/api/.../AdGateConfig.kt                   dailyFree/reward/timeout/cooldown
ads/api/.../CountingAdGate.kt                 删除（由 RewardedGate 取代）
ads/api/.../CountingAdGateTest.kt             删除（由 QuotaTest/RewardedGateTest 取代）
ads/meta/.../MetaAdSdk.kt, MetaAdsConfig.kt   激励 loader + placement
ads/pangle/.../PangleAdSdk.kt, PangleAdsConfig.kt  激励 loader + 广告位改名
app/.../ads/UsageStore.kt                     Quota 持久化 + 跨天 + AdGateState
app/.../MainActivity.kt                       onStart 刷新跨天
features/*/...（5 处 UI）                      按钮文案 + 弹窗
common/src/main/res/values*/strings.xml       解锁文案（6 语言）
.specify/memory/constitution.md               §11 门控改为每日免费 + 主动激励
docs/architecture.md / features.md / localization-and-ads.md / handoff.md
specs/010-ad-sdk-provider/spec.md             FR-009 标注被 011 取代
specs/README.md                               + 011 索引
```

## 7. 实施阶段

1. **额度与门控**：`Quota` + `AdUsage` + `RewardedGate` + 单测；删 `CountingAdGate`；
2. **Provider**：`PangleRewardedLoader`（104722138）、`MetaRewardedLoader`；
3. **存储与状态**：`UsageStore` 三键持久化 + 跨天 + `AdGateState`；
4. **UI**：`:common` 弹窗 + 6 语言 + 5 个功能按钮；
5. **验证**：单测、双构建、真机（免费→锁定→看广告→+5→跨天）。

## 8. 测试与验证

| 层 | 内容 |
|---|---|
| QuotaTest | 免费扣减、跨天重置不累计、激励 +5 可重复、余额跨天保留、先免费后激励 |
| RewardedGateTest | 未锁定、奖励成功、无奖励、失败放行、冷却、未配置、超时、只回调一次 |
| 构建 | meta / pangle debug + release |
| 真机 | 5 个功能：免费 5 次、锁定弹窗、取消、看完 +5、中途关闭、断网 8 秒 |
| 跨天 | 改设备时间到次日 0 点后验证免费刷新、余额保留 |

## 9. 风险

| 风险 | 缓解 |
|---|---|
| 设备时区/时间被修改 | 用本地日期 key；不做服务端校验（本地工具应用，可接受） |
| Meta 无 rewarded placement | 未配置 → fail-open，不锁功能 |
| 激励视频必须用户主动触发 | 只有【观看广告】后才 `show`；静态检查 |
| 回调 null（GroMore） | ad/view 参数可空 + 空值防护 |
| 解锁后重复执行 | `AtomicBoolean` 只回调一次 |
| 跨天 App 挂着不刷新 | `onStart` + 每次读取惰性 rollTo |
| 5 个页面改动面大 | 共享弹窗 + 固定改法，逐页编译验证 |

## 10. 完成定义

- spec FR-001~FR-016 全部实现；SC-001~SC-006 可复现；
- 免费/激励两套额度互不影响，跨天行为正确；
- 单测全绿，meta/pangle 构建通过，真机与跨天验收通过；
- 文档与索引同步。
