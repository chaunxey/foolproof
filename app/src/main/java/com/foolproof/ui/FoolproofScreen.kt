package com.foolproof.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.R
import com.foolproof.base.share.SharedContentStore
import com.foolproof.base.util.findActivity
import com.foolproof.common.navigation.FoolproofDestination
import com.foolproof.common.navigation.FoolproofTabHost
import com.foolproof.ui.compression.CompressionTabScreen
import com.foolproof.ui.conversion.ConversionTabScreen
import com.foolproof.ui.auth.AuthScreen
import com.foolproof.ui.auth.AuthStore
import com.foolproof.ui.auth.maskPhone
import com.foolproof.ui.settings.SettingsScreen
import com.foolproof.ui.video.VideoTabScreen
import kotlinx.coroutines.launch

/**
 * 应用主界面：左侧抽屉 + 底部三个 tab（图片 / 视频 / 转换）。
 *
 * 抽屉只放静态用户信息与设置入口；设置本身是全屏页面，不再是 Dialog。
 */
@Composable
fun FoolproofScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backPressAgain = stringResource(R.string.back_press_again)
    var lastBackAt by remember { mutableStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }

    BackHandler(enabled = !showSettings && !showAuth) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt <= 2_000L) {
            // LocalContext 被语言设置包装成 ContextWrapper，不能直接 as Activity
            context.findActivity()?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(context, backPressAgain, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    if (showAuth) {
        AuthScreen(onBack = { showAuth = false }, onDone = { showAuth = false })
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val userPhone by AuthStore.phone.collectAsState()
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    val shared by SharedContentStore.content.collectAsState()
    val requestedDestination = when (shared?.kind) {
        SharedContentStore.Kind.IMAGE -> FoolproofDestination.Images
        SharedContentStore.Kind.VIDEO -> FoolproofDestination.Videos
        SharedContentStore.Kind.PDF -> FoolproofDestination.Converter
        null -> null
    }

    ModalNavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .clickable { if (userPhone == null) showAuth = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_account_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userPhone?.let { maskPhone(it) }
                                ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                if (userPhone == null) {
                                    R.string.auth_login_or_register
                                } else {
                                    R.string.auth_logged_in
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_title)) },
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettings = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )

                if (userPhone != null) {
                    TextButton(
                        onClick = { AuthStore.logout() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text(stringResource(R.string.auth_logout))
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.drawer_version, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        },
    ) {
        FoolproofTabHost(
            modifier = Modifier.fillMaxSize(),
            onMenuClick = { scope.launch { drawerState.open() } },
            requestedDestination = requestedDestination,
        ) { destination ->
            when (destination) {
                FoolproofDestination.Images -> CompressionTabScreen()
                FoolproofDestination.Videos -> VideoTabScreen()
                FoolproofDestination.Converter -> ConversionTabScreen()
            }
        }
    }
}

@Composable
private fun ScrollableTab(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
}
