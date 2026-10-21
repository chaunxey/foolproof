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

SDK：Meta Audience Network 6.22.0，仅 `:app` 依赖；feature 通过 `:base` 的 `AdGate` 调用。

- **配置**：`app/src/main/java/com/foolproof/ads/AdsConfig.kt` 的 banner / interstitial placement ID；
  为空时横幅显示占位块、门控直接放行；
- **功能分割处横幅**：图片 tab（压缩 ↔ 去重）、视频 tab（压缩 ↔ 拼接）、转换 tab（生成 PDF ↔ PDF 工具）；
  新增分段统一调用 `SectionBannerAd()`；
- **次数门控**：每个 `FeatureId` 独立计数，成功使用 5 次后，下一次使用前展示插屏；广告加载失败一律放行；
- **启动/唤醒**：冷启动必展示 5 秒广告层，3 秒后右上角出现【跳过】；后台超过 1 小时回来再展示；
- **测试**：debug 包自动 `AdSettings.setDebugBuild(true)`，还需在 Meta 后台登记测试设备；
- **合规**：广告 SDK 会联网并处理设备标识/广告交互数据；EEA/UK 需要同意入口，
  隐私政策与 Play Data Safety 必须说明。

广告依赖 Play 商店链接才能创建 placement ID，因此上架是广告链路的前置条件。
