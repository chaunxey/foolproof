package com.foolproof.ads.pangle

import com.bytedance.sdk.openadsdk.TTCustomController

/**
 * 隐私开关：默认关闭地理位置、电话状态、写外部存储、应用列表、录音权限的采集。
 *
 * 完整隐私同意弹窗不在本次范围；这里保证即使 SDK 已初始化也尽量少采集。
 */
internal object PanglePrivacyController : TTCustomController() {

    override fun isCanUseLocation(): Boolean = false

    override fun isCanUsePhoneState(): Boolean = false

    override fun isCanUseWriteExternal(): Boolean = false

    override fun alist(): Boolean = false

    override fun isCanUsePermissionRecordAudio(): Boolean = false
}
