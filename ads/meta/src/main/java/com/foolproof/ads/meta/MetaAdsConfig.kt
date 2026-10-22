package com.foolproof.ads.meta

/**
 * Meta Audience Network 配置。
 *
 * 正式环境把 placement ID 换成 Meta 后台创建的值；为空时横幅显示占位块、门控放行。
 */
object MetaAdsConfig {

    const val BANNER_PLACEMENT_ID = ""
    const val INTERSTITIAL_PLACEMENT_ID = ""
    const val SPLASH_PLACEMENT_ID = ""

    val configured: Boolean
        get() = BANNER_PLACEMENT_ID.isNotBlank() && INTERSTITIAL_PLACEMENT_ID.isNotBlank()
}
