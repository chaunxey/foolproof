package com.foolproof.features.duplicates.data

import android.app.PendingIntent
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.foolproof.base.log.LogUtil
import com.foolproof.features.duplicates.domain.DeleteChannel
import com.foolproof.features.duplicates.domain.DeleteReport
import com.foolproof.features.duplicates.domain.DeleteResult
import com.foolproof.features.duplicates.domain.PhotoDeleter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 删除实现（宪法 §3.2 v1.6）：
 *
 * - **API ≥ 30**：`MediaStore.createDeleteRequest` 生成系统确认请求（回收站、可恢复），
 *   由 UI 用 `StartIntentSenderForResult` 拉起；回调后再 [stillExisting] 核实；
 * - **API ≤ 29**：系统没有回收站 API，只能走 SAF（`DocumentsContract.deleteDocument`），
 *   **不可恢复**；未授权目录时通道为 [DeleteChannel.NONE]；
 * - 两条路径都**不申请写权限**（宪法 §8）。
 */
class MediaStorePhotoDeleter(
    private val context: Context,
    private val logUtil: LogUtil,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : PhotoDeleter {

    private var safTreeUri: String? = null

    override fun channel(): DeleteChannel = when {
        sdkInt >= API_R -> DeleteChannel.SYSTEM_REQUEST
        safTreeUri != null -> DeleteChannel.SAF
        else -> DeleteChannel.NONE
    }

    override fun setSafTree(treeUri: String?) {
        safTreeUri = treeUri
    }

    override suspend fun delete(ids: List<String>): DeleteResult = withContext(Dispatchers.IO) {
        val uris = ids.map(Uri::parse)

        when (channel()) {
            DeleteChannel.SYSTEM_REQUEST -> {
                // 必须用 createTrashRequest（移入回收站、可恢复），而不是 createDeleteRequest（永久删除）。
                // 两者在 API 30+ 都存在，用错会让"删除可恢复"的承诺失效（宪法 §3.2 v1.6）。
                val pending: PendingIntent = MediaStore.createTrashRequest(
                    context.contentResolver,
                    uris,
                    true,
                )
                DeleteResult.NeedsConfirmation(token = pending, requested = ids)
            }

            DeleteChannel.SAF -> {
                var deleted = 0
                var failed = 0
                for (uri in uris) {
                    val ok = runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    }.getOrElse { throwable ->
                        logUtil.w { "saf delete failed: ${throwable::class.simpleName}" }
                        false
                    }
                    if (ok) deleted++ else failed++
                }
                DeleteResult.Completed(DeleteReport(requested = ids.size, deleted = deleted, failed = failed))
            }

            DeleteChannel.NONE ->
                DeleteResult.Completed(DeleteReport(requested = ids.size, deleted = 0, failed = ids.size))
        }
    }

    override suspend fun stillExisting(ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        ids.filterTo(mutableSetOf()) { id ->
            try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(id), "r")?.use { true } ?: false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                false
            }
        }
    }

    /** UI 侧把 token 还原成系统请求；类型不匹配时返回 null。 */
    fun toIntentSender(token: Any): IntentSender? = (token as? PendingIntent)?.intentSender

    private companion object {
        const val API_R = 30
    }
}
