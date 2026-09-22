package com.kerneldroid.karchiver.data.archive

enum class OpKind { COMPRESS, EXTRACT }

sealed interface OpOutcome {
    data object Success : OpOutcome
    data class Failed(val message: String) : OpOutcome
    data object Cancelled : OpOutcome
}
