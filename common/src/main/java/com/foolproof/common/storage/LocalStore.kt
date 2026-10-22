package com.foolproof.common.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_data")

/**
 * 全项目统一的本地键值存储，底层是 DataStore Preferences。
 *
 * - 初始化一次；读取在启动阶段同步完成（`runBlocking`，只读一个小文件）；
 * - 写入异步落盘，调用方先更新内存状态，界面不等待磁盘；
 * - key 用普通 String，调用方自己保证命名唯一。
 */
object LocalStore {

    private lateinit var store: DataStore<Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            store = context.applicationContext.appDataStore
            initialized = true
        }
    }

    fun readString(key: String, defaultValue: String): String =
        read { it[stringPreferencesKey(key)] ?: defaultValue }

    fun readStringOrNull(key: String): String? =
        read { it[stringPreferencesKey(key)] }

    fun readInt(key: String, defaultValue: Int): Int =
        read { it[intPreferencesKey(key)] ?: defaultValue }

    fun readLong(key: String, defaultValue: Long): Long =
        read { it[longPreferencesKey(key)] ?: defaultValue }

    fun readFloat(key: String, defaultValue: Float): Float =
        read { it[floatPreferencesKey(key)] ?: defaultValue }

    fun readBoolean(key: String, defaultValue: Boolean): Boolean =
        read { it[booleanPreferencesKey(key)] ?: defaultValue }

    fun writeString(key: String, value: String) {
        scope.launch { store.edit { it[stringPreferencesKey(key)] = value } }
    }

    fun writeInt(key: String, value: Int) {
        scope.launch { store.edit { it[intPreferencesKey(key)] = value } }
    }

    fun writeLong(key: String, value: Long) {
        scope.launch { store.edit { it[longPreferencesKey(key)] = value } }
    }

    fun writeFloat(key: String, value: Float) {
        scope.launch { store.edit { it[floatPreferencesKey(key)] = value } }
    }

    fun writeBoolean(key: String, value: Boolean) {
        scope.launch { store.edit { it[booleanPreferencesKey(key)] = value } }
    }

    fun remove(key: String) {
        scope.launch {
            store.edit {
                it.remove(stringPreferencesKey(key))
                it.remove(intPreferencesKey(key))
                it.remove(longPreferencesKey(key))
                it.remove(floatPreferencesKey(key))
                it.remove(booleanPreferencesKey(key))
            }
        }
    }

    private fun <T> read(block: (Preferences) -> T): T {
        check(initialized) { "LocalStore.init(context) must be called first" }
        return runBlocking { block(store.data.first()) }
    }
}
