package com.diniy.duit.core.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory toggle for UI preview mode.
 * Never persisted to Room or DataStore so sample data can never leak into real user records.
 */
class PreviewModeController {
    private val _isSamplePreviewEnabled = MutableStateFlow(false)
    val isSamplePreviewEnabled: StateFlow<Boolean> = _isSamplePreviewEnabled.asStateFlow()

    fun setSamplePreviewEnabled(enabled: Boolean) {
        _isSamplePreviewEnabled.value = enabled
    }
}
