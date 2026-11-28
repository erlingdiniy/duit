package com.diniy.duit.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Offline builds (debug/fluid/release/benchmark) exclude Firebase Remote Config completely. */
object RemoteAppConfig {
    const val isSupported: Boolean = false

    private val _snapshot = MutableStateFlow(RemoteAppConfigSnapshot())
    val snapshot: StateFlow<RemoteAppConfigSnapshot> = _snapshot.asStateFlow()

    fun initialize(context: Context) = Unit
}
