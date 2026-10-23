package com.foolproof.common.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.foolproof.base.ads.AdGateProvider
import com.foolproof.base.ads.FeatureId
import com.foolproof.base.util.findActivity
import com.foolproof.common.R

/**
 * 额度用完后的解锁弹窗：用户主动看激励视频，成功后 +5 次。
 *
 * 没有广告实现、拿不到 Activity 或广告不可用时都会直接放行（不卡死功能）。
 */
@Composable
fun RewardUnlockDialog(
    feature: FeatureId,
    onDismiss: () -> Unit,
    onAllowed: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.ad_unlock_title)) },
        text = {
            Text(
                if (loading) {
                    stringResource(R.string.ad_unlock_loading)
                } else {
                    stringResource(R.string.ad_unlock_message, 5)
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    val activity = context.findActivity()
                    val gate = AdGateProvider.gate
                    if (activity == null || gate == null) {
                        onAllowed()
                    } else {
                        loading = true
                        gate.requestUnlock(activity, feature) { allowed ->
                            loading = false
                            if (allowed) onAllowed() else onDismiss()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.ad_unlock_watch))
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) {
                Text(stringResource(R.string.ad_unlock_cancel))
            }
        },
    )
}
