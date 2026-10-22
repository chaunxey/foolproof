package com.foolproof.ui.auth

import android.content.Context
import com.foolproof.common.storage.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地演示账号：只保存手机号，不联网、不上传、不校验真实短信。
 * 正式接后端时替换这里即可，UI 不用改。
 */
object AuthStore {

    private const val KEY_PHONE = "auth_phone"

    private val _phone = MutableStateFlow<String?>(null)
    val phone: StateFlow<String?> = _phone.asStateFlow()

    fun init(context: Context) {
        LocalStore.init(context.applicationContext)
        _phone.value = LocalStore.readStringOrNull(KEY_PHONE)
    }

    fun login(phone: String) {
        _phone.value = phone
        LocalStore.writeString(KEY_PHONE, phone)
    }

    fun logout() {
        _phone.value = null
        LocalStore.remove(KEY_PHONE)
    }
}
