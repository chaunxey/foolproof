package com.foolproof.ads

/**
 * 广告配置。
 *
 * 正式环境把下面三个 placement ID 换成 Meta Audience Network 后台创建的值；
 * 为空时广告位显示占位块（调试用），插屏直接放行，不会卡住功能。
 */
object AdsConfig {

    const val BANNER_PLACEMENT_ID = ""
    const val INTERSTITIAL_PLACEMENT_ID = ""
    const val SPLASH_PLACEMENT_ID = ""

    /** 同一功能成功使用多少次后，下一次使用前需要看插屏。 */
    const val USES_BEFORE_AD = 5

    val configured: Boolean
        get() = BANNER_PLACEMENT_ID.isNotBlank() && INTERSTITIAL_PLACEMENT_ID.isNotBlank()
}
