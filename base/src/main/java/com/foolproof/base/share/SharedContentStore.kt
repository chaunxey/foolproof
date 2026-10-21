package com.foolproof.base.share

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统分享入口的内容中转：Activity 解析 Intent，feature 消费后清空。
 */
object SharedContentStore {

    enum class Kind { IMAGE, VIDEO, PDF }

    data class SharedContent(val kind: Kind, val uris: List<String>)

    private val _content = MutableStateFlow<SharedContent?>(null)
    val content: StateFlow<SharedContent?> = _content.asStateFlow()

    fun publish(kind: Kind, uris: List<String>) {
        val cleaned = uris.filter { it.isNotBlank() }.distinct()
        if (cleaned.isNotEmpty()) {
            _content.value = SharedContent(kind, cleaned)
        }
    }

    fun consume() {
        _content.value = null
    }

    @Suppress("DEPRECATION")
    fun publishFrom(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val mime = intent.type ?: return
        val kind = when {
            mime.startsWith("image/") -> Kind.IMAGE
            mime.startsWith("video/") -> Kind.VIDEO
            mime == "application/pdf" -> Kind.PDF
            else -> return
        }
        val uris = if (action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
        publish(kind, uris.map { it.toString() })
    }
}
