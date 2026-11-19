package com.diniy.duit.support

import android.content.Context
import androidx.compose.runtime.Composable

/** The personal edition has no support page. */
internal fun isSupportPageAvailable(context: Context): Boolean = false

@Composable
internal fun SupportDuitScreen(onClose: () -> Unit) = Unit
