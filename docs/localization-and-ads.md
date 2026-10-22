# 多语言与广告

## 多语言

支持：简体中文 / English / ខ្មែរ / 日本語 / Français / Deutsch。

- 文案放在各模块 `values/`（中文默认）与 `values-en` / `values-km` / `values-ja` / `values-fr` / `values-de`；
- Compose 统一 `stringResource`，代码里不写用户可见字面量；
- 带参数一律位置参数：`%1$s` / `%1$d` / `%1$d%%`；
- 设置页「语言」= 跟随系统 + 5 种语言，选完自动关闭设置回首页；
- **无感切换**：`MainActivity` 用 `CompositionLocalProvider` 替换 `LocalContext` / `LocalConfiguration`，只重组、不重建 Activity；
- 字体大小同理，通过 `Density.fontScale` 覆盖，最大 200%；
- 主题：跟随系统 / 浅色 / 深色，持久化在 `SettingsStore`。

新增语言：

1. 各模块加 `values-xx/strings.xml`（key 与 `values/` 一致）；
2. `AppLanguage` 加一项与母语显示名；
3. 设置页自动出现新 chip。

英文/高棉文/日文/法文/德文为草拟译文，建议母语者校对，尤其高棉文；改 `values-*/` 即可，不用动代码。

## 广告

两套广告 SDK，**构建期二选一**（默认 Meta），每个产物只打包一套：

| provider | SDK | 依赖 |
| --- | --- | --- |
| `meta`（默认） | Meta Audience Network 6.22.0 | `libs.audience.network` |
| `pangle` | 穿山甲 Pangle 7.8.1.5 | 官方 Maven `com.pangle.cn:ads-sdk-pro` |

- **切换**：改 `gradle.properties` 的 `foolproof.adSdk`，sync 后重新构建即可；
  `:app` 只依赖选中的 provider，另一个 SDK 不会进包；
- **配置**：
  - Meta：`ads/meta/.../MetaAdsConfig.kt`（banner / interstitial / splash placement ID）；
  - 穿山甲：`ads/pangle/.../PangleAdsConfig.kt`（APP_ID / BANNER_CODE_ID /
    INTERSTITIAL_CODE_ID / SPLASH_CODE_ID）；
  - 为空时：**不初始化 SDK**、横幅显示占位块、门控直接放行；
- **功能分割处横幅**：图片 tab（压缩 ↔ 去重）、视频 tab（压缩 ↔ 拼接）、转换 tab（生成 PDF ↔ PDF 工具）；
  新增分段统一调用 `SectionBannerAd()`；
- **次数门控**：每个 `FeatureId` 独立计数，成功使用 5 次后，下一次使用前展示全屏广告；
  加载失败或 5 秒内未就绪一律放行；
- **启动/唤醒**：冷启动必展示一次广告层，后台超过 1 小时回来再展示；
  Meta 用自绘 5 秒壳（3 秒后可跳过），穿山甲优先原生开屏，失败/超时回退自绘壳；
- **测试**：debug 包 Meta 自动 `AdSettings.setDebugBuild(true)`，穿山甲 `TTAdConfig.debug(true)`；
- **合规**：未配置参数不初始化穿山甲；初始化时通过 `TTCustomController` 关闭位置/电话状态/
  应用列表/写外部存储采集；广告 SDK 会联网并处理设备标识/广告交互数据，
  隐私政策与 Play Data Safety 必须说明。

Meta 的 placement ID 依赖 Play 商店链接，上架是 Meta 广告链路的前置条件；
穿山甲的 appId/代码位在穿山甲媒体平台创建。
