package com.foolproof.ui.auth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foolproof.R
import kotlinx.coroutines.delay

private data class Country(val code: String, val iso: String, val maxDigits: Int)

private val COUNTRIES = listOf(
    Country("+86", "CN", 11),
    Country("+855", "KH", 9),
    Country("+1", "US", 10),
    Country("+81", "JP", 11),
    Country("+82", "KR", 11),
    Country("+65", "SG", 8),
    Country("+66", "TH", 9),
    Country("+84", "VN", 10),
    Country("+33", "FR", 9),
    Country("+49", "DE", 11),
)

private enum class AuthPage { SMS, PASSWORD }

/**
 * 统一的手机号登录：
 *
 * - 默认验证码登录；后端语义是「手机号不存在则自动注册，存在则登录」；
 * - 密码登录是独立页面，不是 tab 切换；
 * - 获取验证码后 30 秒倒计时，倒计时结束可重发；
 * - 不展示「演示账号」之类的提示，登录成功写入本地 AuthStore。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var page by remember { mutableStateOf(AuthPage.SMS) }
    var country by remember { mutableStateOf(COUNTRIES.first()) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var countryMenu by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val digits = phone.filter { it.isDigit() }
    val phoneValid = digits.length in 4..country.maxDigits

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    BackHandler {
        if (page == AuthPage.PASSWORD) page = AuthPage.SMS else onBack()
    }

    fun submit() {
        error = null
        if (!phoneValid) {
            error = R.string.auth_invalid_phone
            return
        }
        if (page == AuthPage.SMS) {
            if (code != "0000") {
                error = R.string.auth_invalid_code
                return
            }
        } else if (password != "admin") {
            error = R.string.auth_invalid_password
            return
        }
        AuthStore.login("${country.code} $digits")
        onDone()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (page == AuthPage.SMS) {
                                R.string.auth_sms_title
                            } else {
                                R.string.auth_password_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (page == AuthPage.PASSWORD) page = AuthPage.SMS else onBack()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.auth_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(
                    if (page == AuthPage.SMS) {
                        R.string.auth_sms_subtitle
                    } else {
                        R.string.auth_password_subtitle
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { countryMenu = true }) {
                        Text("${country.code} ${country.iso}")
                    }
                    DropdownMenu(
                        expanded = countryMenu,
                        onDismissRequest = { countryMenu = false },
                    ) {
                        COUNTRIES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.code} ${option.iso} · ${option.maxDigits}") },
                                onClick = {
                                    country = option
                                    countryMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        phone = input.filter { it.isDigit() }.take(country.maxDigits)
                    },
                    label = { Text(stringResource(R.string.auth_phone)) },
                    placeholder = {
                        Text(stringResource(R.string.auth_phone_hint, country.maxDigits))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
            }

            if (page == AuthPage.SMS) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(6)
                        },
                        label = { Text(stringResource(R.string.auth_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            countdown = 30
                            Toast.makeText(
                                context,
                                context.getString(R.string.auth_code_sent),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        enabled = phoneValid && countdown == 0,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            if (countdown > 0) {
                                stringResource(R.string.auth_resend_in, countdown)
                            } else {
                                stringResource(R.string.auth_get_code)
                            },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            error?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text(stringResource(R.string.auth_login))
            }

            TextButton(
                onClick = {
                    page = if (page == AuthPage.SMS) AuthPage.PASSWORD else AuthPage.SMS
                    error = null
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    stringResource(
                        if (page == AuthPage.SMS) {
                            R.string.auth_use_password
                        } else {
                            R.string.auth_use_code
                        },
                    ),
                )
            }

            Text(
                text = stringResource(R.string.auth_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 抽屉里展示脱敏手机号。 */
fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 7) return phone
    return phone.replace(digits.substring(3, digits.length - 4), "****")
}
