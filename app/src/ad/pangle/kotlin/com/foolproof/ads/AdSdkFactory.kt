package com.foolproof.ads

import com.foolproof.ads.api.AdSdk
import com.foolproof.ads.api.AdUsage
import com.foolproof.ads.pangle.PangleAdSdk

/**
 * 当前构建选中的广告 provider（pangle）。
 *
 * 由 `gradle.properties` 的 `foolproof.adSdk` 决定：`:app` 只会编译对应
 * `src/ad/<provider>/kotlin` 下的工厂文件，另一个 provider 的依赖不进产物。
 */
fun createAdSdk(usage: AdUsage): AdSdk = PangleAdSdk(usage)
