package com.kerneldroid.karchiver.presentation.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kerneldroid.karchiver.data.elevation.elevationEngineFor
import com.kerneldroid.karchiver.data.trash.TrashEntry
import com.kerneldroid.karchiver.data.trash.TrashRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TrashRepository.get(application)

    val entries: StateFlow<List<TrashEntry>> = repo.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val missing: StateFlow<Set<String>> = entries
        .map { list ->
            withContext(Dispatchers.IO) {
                list.filter { !File(it.storedPath).exists() }.mapTo(HashSet()) { it.id }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun restore(entry: TrashEntry, elevationMode: String, onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                onDone(repo.restore(entry, elevationEngineFor(elevationMode)).map { it.name })
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteForever(entry: TrashEntry, elevationMode: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                onDone(repo.deletePermanently(entry, elevationEngineFor(elevationMode)))
            } finally {
                _busy.value = false
            }
        }
    }

    fun empty(elevationMode: String, onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                onDone(repo.empty(elevationEngineFor(elevationMode)))
            } finally {
                _busy.value = false
            }
        }
    }
}
