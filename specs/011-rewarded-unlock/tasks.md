# Tasks: 激励视频解锁次数（rewarded-unlock）

**Feature**: `specs/011-rewarded-unlock/spec.md` · **Plan**: `specs/011-rewarded-unlock/plan.md`
**分支**: `feature/ad-sdk-provider`
**测试**：`./gradlew :ads:api:testDebugUnitTest`、`:app:assembleDebug`

## Phase 1: 额度与门控 ✅

- [x] T001 `:ads:api` `Quota`：`dayKey/freeUsed/bonus`、`available`、`rollTo/consume/grant`；
  `AdUsage` 接口
- [x] T002 `QuotaTest`：免费扣减、跨天重置不累计、激励 +5 可重复、余额跨天保留、先免费后激励（6 个用例）
- [x] T003 `RewardedGate`：未锁定直接放行、奖励 +5、无奖励不解锁、失败放行、60 秒冷却、
  8 秒超时、只回调一次
- [x] T004 `RewardedGateTest` 覆盖 T003 全部场景（10 个用例）
- [x] T005 `:base` AdGate v2：`available` / `isLocked` / `requestUnlock` + `AdGateState`；
  删除 `maybeGate` / `gateOrRun`

## Phase 2: Provider ✅

- [x] T006 穿山甲：`REWARDED_AD_ID = "104722138"`；`PangleRewardedLoader`
  （`loadRewardVideoAd` + `onRewardVerify`/`onRewardArrived` + `onAdClose`）
- [x] T007 Meta：`REWARDED_PLACEMENT_ID` + `MetaRewardedLoader`（`RewardedVideoAd`）
- [x] T008 `MetaAdSdk` / `PangleAdSdk` 换 `RewardedGate`；未配置 fail-open

## Phase 3: 存储、状态与 UI ✅

- [x] T009 `UsageStore`：`free_day_/free_used_/bonus_` 三键持久化、读取惰性跨天、同步 `AdGateState`
- [x] T010 `MainActivity.onStart` 刷新跨天（App 挂着跨天回前台）
- [x] T011 `:common` `RewardUnlockDialog` + 6 语言文案
- [x] T012 图片压缩按钮接入（锁定文案 + 弹窗）
- [x] T013 重复清理按钮接入
- [x] T014 视频压缩按钮接入
- [x] T015 视频拼接按钮接入
- [x] T016 文件转换按钮接入

## Phase 4: 治理与验证

- [x] T017 宪法 §11 / v1.20、docs、010 FR-009 标注、specs 索引同步
- [x] T018 单测全绿（6+10）；meta / pangle debug 构建通过
- [x] T019 真机（`10AG9C079Y001L0`）：锁定文案、确认弹窗、激励视频加载/播放、
  跳过不解锁 已验证；交互式广告需真人完成浏览任务才发奖，完整奖励路径待补测
- [ ] T020 跨天：设备时间改到次日，验证免费回到 5、激励余额不变

> 备注：验证「锁定态」时临时把 `DAILY_FREE_USES` 设为 0，验证后已恢复为 5。
