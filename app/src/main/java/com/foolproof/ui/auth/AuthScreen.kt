package com.foolproof.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foolproof.R

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

/**
 * 静态登录/注册页：手机号 + 密码或验证码。
 *
 * 演示账号规则：
 * - 密码固定为 `admin`
 * - 验证码固定为 `0000`
 * - 不联网、不发送真实短信；数据只保存在本机。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var register by remember { mutableStateOf(false) }
    var passwordMode by remember { mutableStateOf(true) }
    var country by remember { mutableStateOf(COUNTRIES.first()) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("admin") }
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var countryMenu by remember { mutableStateOf(false) }

    fun submit() {
        error = null
        val digits = phone.filter { it.isDigit() }
        if (digits.length !in 4..country.maxDigits) {
            error = R.string.auth_invalid_phone
            return
        }
        if (register || !passwordMode) {
            if (code != "0000") {
                error = R.string.auth_invalid_code
                return
            }
            if (register && password != confirm) {
                error = R.string.auth_password_mismatch
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
                            if (register) R.string.auth_title_register else R.string.auth_title_login,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                FilterChip(
                    selected = !register,
                    onClick = { register = false },
                    label = { Text(stringResource(R.string.auth_tab_login)) },
                )
                FilterChip(
                    selected = register,
                    onClick = { register = true },
                    label = { Text(stringResource(R.string.auth_tab_register)) },
                )
            }

            if (!register) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    FilterChip(
                        selected = passwordMode,
                        onClick = { passwordMode = true },
                        label = { Text(stringResource(R.string.auth_password_mode)) },
                    )
                    FilterChip(
                        selected = !passwordMode,
                        onClick = { passwordMode = false },
                        label = { Text(stringResource(R.string.auth_code_mode)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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

            if (register || !passwordMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.auth_code)) },
                        placeholder = { Text(stringResource(R.string.auth_code_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { code = "0000" },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.auth_send_code))
                    }
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                placeholder = { Text(stringResource(R.string.auth_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            if (register) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.auth_confirm_password)) },
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
                Text(
                    stringResource(
                        if (register) R.string.auth_register else R.string.auth_login,
                    ),
                )
            }

            Text(
                text = stringResource(
                    if (register) R.string.auth_switch_to_login else R.string.auth_switch_to_register,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable {
                        register = !register
                        error = null
                    }
                    .padding(vertical = 8.dp),
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
