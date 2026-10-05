package com.kerneldroid.karchiver.data

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NativeFallback {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val acknowledged = AtomicBoolean(false)

    fun report() {
        if (acknowledged.get()) return
        _active.value = true
    }

    fun acknowledge() {
        acknowledged.set(true)
        _active.value = false
    }
}
