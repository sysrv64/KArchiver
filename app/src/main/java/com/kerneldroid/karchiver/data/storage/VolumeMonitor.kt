package com.kerneldroid.karchiver.data.storage

import android.content.Context
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VolumeMonitor(
    private val context: Context,
    private val onVolumes: (List<AppVolume>) -> Unit
) {
    private val appContext: Context = try {
        context.applicationContext ?: context
    } catch (_: Exception) {
        context
    }

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private var storageManager: StorageManager? = null
    private var callback: StorageManager.StorageVolumeCallback? = null
    private var started = false
    private val lock = Any()

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            try {
                if (!scope.isActive) {
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                }
            } catch (_: Exception) {
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            debounceJob = null
        }
        try {
            val manager = try {
                appContext.getSystemService(StorageManager::class.java)
            } catch (_: Exception) {
                null
            }
            storageManager = manager
            if (manager != null) {
                try {
                    val executor = ContextCompat.getMainExecutor(appContext)
                    val volumeCallback = object : StorageManager.StorageVolumeCallback() {
                        override fun onStateChanged(volume: StorageVolume) {
                            try {
                                scheduleReload()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    callback = volumeCallback
                    try {
                        manager.registerStorageVolumeCallback(executor, volumeCallback)
                    } catch (_: Exception) {
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        loadAsync()
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
        }
        try {
            debounceJob?.cancel()
        } catch (_: Exception) {
        }
        debounceJob = null
        try {
            val manager = storageManager
            val volumeCallback = callback
            if (manager != null && volumeCallback != null) {
                try {
                    manager.unregisterStorageVolumeCallback(volumeCallback)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        storageManager = null
        callback = null
        try {
            scope.cancel()
        } catch (_: Exception) {
        }
    }

    private fun loadAsync() {
        try {
            scope.launch {
                val volumes = try {
                    loadAppVolumes(appContext)
                } catch (_: Exception) {
                    emptyList()
                }
                try {
                    onVolumes(volumes)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun scheduleReload() {
        try {
            debounceJob?.cancel()
        } catch (_: Exception) {
        }
        try {
            debounceJob = scope.launch {
                try {
                    delay(500)
                } catch (_: Exception) {
                    return@launch
                }
                val volumes = try {
                    loadAppVolumes(appContext)
                } catch (_: Exception) {
                    emptyList()
                }
                try {
                    onVolumes(volumes)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }
}
