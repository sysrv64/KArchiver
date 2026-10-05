@file:OptIn(ExperimentalMaterial3Api::class)

package com.kerneldroid.karchiver.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kerneldroid.karchiver.R

@Composable
fun NativeFallbackDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WarningAmber, null) },
        title = { Text(stringResource(R.string.library_native_fallback_title)) },
        text = { Text(stringResource(R.string.library_native_fallback_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_native_fallback_ok))
            }
        }
    )
}
