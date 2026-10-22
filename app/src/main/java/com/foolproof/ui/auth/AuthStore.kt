package com.foolproof.ui.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地演示账号：只保存手机号，不联网、不上传、不校验真实短信。
 * 正式接后端时替换这里即可，UI 不用改。
 */
object AuthStore {

    private const val PREFS = "auth"
    private const val KEY_PHONE = "phone"

    private var prefs: android.content.SharedPreferences? = null
    private val _phone = MutableStateFlow<String?>(null)
    val phone: StateFlow<String?> = _phone.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _phone.value = prefs?.getString(KEY_PHONE, null)
    }

    fun login(phone: String) {
        _phone.value = phone
        prefs?.edit()?.putString(KEY_PHONE, phone)?.apply()
    }

    fun logout() {
        _phone.value = null
        prefs?.edit()?.remove(KEY_PHONE)?.apply()
    }
}
