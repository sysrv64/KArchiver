package com.kerneldroid.karchiver.data.trash

import com.kerneldroid.karchiver.data.search.SearchQuery

fun TrashEntry.trashExtension(): String = name.substringAfterLast('.', "")

fun SearchQuery.matchesTrashEntry(entry: TrashEntry): Boolean =
    matches(entry.name, entry.trashExtension(), entry.isDirectory, entry.size, entry.deletedAt)

fun filterTrash(entries: List<TrashEntry>, query: SearchQuery): List<TrashEntry> {
    if (query.isEmpty) return entries
    return entries.filter { query.matchesTrashEntry(it) }
}