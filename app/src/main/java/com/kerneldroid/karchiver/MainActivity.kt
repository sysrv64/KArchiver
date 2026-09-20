package com.kerneldroid.karchiver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerneldroid.karchiver.data.SettingsRepository
import com.kerneldroid.karchiver.data.ThemeMode
import com.kerneldroid.karchiver.presentation.KArchiverRoot
import com.kerneldroid.karchiver.presentation.onboard.OnboardScreen
import com.kerneldroid.karchiver.presentation.onboard.hasStoragePermission
import com.kerneldroid.karchiver.ui.theme.KArchiverTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val incomingFile = mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        incomingFile.value = resolveIncoming(intent)
        setContent {
            val settingsRepo = remember { SettingsRepository(applicationContext) }
            val prefs by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = null)
            KArchiverTheme(
                themeMode = prefs?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = prefs?.dynamicColor ?: true,
                seedColor = prefs?.seedColor?.let { Color(it.toULong()) }
            ) {
                var hasPerm by remember { mutableStateOf(hasStoragePermission(this)) }
                LifecycleResumeEffect(Unit) {
                    val now = hasStoragePermission(this@MainActivity)
                    if (now != hasPerm) hasPerm = now
                    onPauseOrDispose { }
                }
                if (hasPerm) {
                    KArchiverRoot(
                        incomingFile = incomingFile.value,
                        onIncomingHandled = { incomingFile.value = null }
                    )
                } else {
                    OnboardScreen(onGranted = { hasPerm = hasStoragePermission(this) })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingFile.value = resolveIncoming(intent)
    }

    private fun resolveIncoming(intent: Intent?): File? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it) }
            "content" -> resolveDocumentPath(uri) ?: copyToCache(uri)
            else -> null
        }
    }

    private fun resolveDocumentPath(uri: Uri): File? = runCatching {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = DocumentsContract.getDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val root = if (parts[0].equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage", parts[0])
        }
        File(root, parts[1]).takeIf { it.isFile }
    }.getOrNull()

    private fun copyToCache(uri: Uri): File? = runCatching {
        val name = queryDisplayName(uri) ?: "incoming-${System.currentTimeMillis()}"
        val dir = File(cacheDir, "incoming").apply { mkdirs() }
        val out = File(dir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        out
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
