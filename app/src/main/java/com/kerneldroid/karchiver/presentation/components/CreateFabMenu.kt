@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.kerneldroid.karchiver.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.kerneldroid.karchiver.R

enum class CreateKind { FOLDER, FILE }

@Composable
fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 45f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "fabRotation"
                )
                Icon(Icons.Filled.Add, stringResource(R.string.browser_create), Modifier.rotate(rotation))
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); onCreateFolder() },
            icon = { Icon(Icons.Filled.CreateNewFolder, null) },
            text = { Text(stringResource(R.string.browser_new_folder)) }
        )
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); onCreateFile() },
            icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
            text = { Text(stringResource(R.string.browser_new_file)) }
        )
    }
}

@Composable
fun CreateNameDialog(
    kind: CreateKind,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultFolderName = stringResource(R.string.browser_new_folder)
    val defaultFileName = stringResource(R.string.browser_new_file_name)
    var name by rememberSaveable(kind) {
        mutableStateOf(if (kind == CreateKind.FOLDER) defaultFolderName else defaultFileName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (kind == CreateKind.FOLDER) Icons.Filled.CreateNewFolder else Icons.AutoMirrored.Filled.NoteAdd,
                null
            )
        },
        title = {
            Text(
                stringResource(
                    if (kind == CreateKind.FOLDER) R.string.browser_new_folder else R.string.browser_new_file
                )
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.word_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.browser_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}