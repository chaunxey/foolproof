package com.foolproof.ads.pangle

/**
 * 穿山甲（Pangle）配置。
 *
 * appId 形如 `5xxxxxx`（5 开头的 7 位数字）；代码位在穿山甲后台创建：
 * - BANNER_CODE_ID：Banner / 模板横幅；
 * - INTERSTITIAL_CODE_ID：门控用的全屏广告（全屏视频或新插屏，走 loadFullScreenVideoAd）；
 * - SPLASH_CODE_ID：开屏。
 *
 * 任一项为空即视为未配置：不初始化 SDK、不请求广告、横幅占位、门控放行。
 */
object PangleAdsConfig {

    const val APP_ID = "5899851"
    const val BANNER_CODE_ID = "104722089"
    const val INTERSTITIAL_CODE_ID = "104722138"
    const val SPLASH_CODE_ID = "104721749"

    val configured: Boolean
        get() = isConfigured(APP_ID, BANNER_CODE_ID, INTERSTITIAL_CODE_ID)

    internal fun isConfigured(
        appId: String,
        bannerCodeId: String,
        interstitialCodeId: String,
    ): Boolean = appId.isNotBlank() &&
        bannerCodeId.isNotBlank() &&
        interstitialCodeId.isNotBlank()
}
