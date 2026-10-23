package com.foolproof.ads.pangle

/**
 * 穿山甲（GroMore 融合 SDK）配置。
 *
 * appId 形如 `5xxxxxx`（5 开头的 7 位数字）；广告位是 GroMore 聚合广告位（ID 以 1 开头）：
 * - BANNER_CODE_ID：Banner / 模板横幅；
 * - REWARDED_AD_ID：激励视频（解锁次数）；
 * - SPLASH_CODE_ID：开屏。
 *
 * 任一项为空即视为未配置：不初始化 SDK、不请求广告、横幅占位、门控放行。
 */
object PangleAdsConfig {

    const val APP_ID = "5899851"
    const val BANNER_CODE_ID = "104722089"
    const val REWARDED_AD_ID = "104722138"
    const val SPLASH_CODE_ID = "104721749"

    val configured: Boolean
        get() = isConfigured(APP_ID, BANNER_CODE_ID, REWARDED_AD_ID)

    internal fun isConfigured(
        appId: String,
        bannerCodeId: String,
        rewardedAdId: String,
    ): Boolean = appId.isNotBlank() &&
        bannerCodeId.isNotBlank() &&
        rewardedAdId.isNotBlank()
}
