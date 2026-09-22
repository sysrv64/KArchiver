package com.kerneldroid.karchiver.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "karchiver_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }

const val DRAWER_TAB_TRASH = "trash"
const val DRAWER_TAB_HISTORY = "history"
const val DRAWER_TAB_PROCESSES = "processes"

val DEFAULT_DRAWER_TABS = listOf("files", "home", "recents", "history", "settings")

internal fun insertDrawerTab(current: List<String>, id: String): List<String> {
    val settingsIndex = current.indexOf("settings")
    val updated = current.toMutableList()
    if (settingsIndex >= 0) updated.add(settingsIndex, id) else updated.add(id)
    return updated
}

internal fun migrateDrawerTabs(historyEnabled: Boolean, trashEnabled: Boolean): List<String> {
    val tabs = mutableListOf("files", "home", "recents")
    if (historyEnabled) tabs.add(DRAWER_TAB_HISTORY)
    if (trashEnabled) tabs.add(DRAWER_TAB_TRASH)
    tabs.add("settings")
    return tabs
}

internal fun parseDrawerTabs(raw: String?): List<String> = raw
    ?.split('\n')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?: emptyList()

data class AppSettings(
    val openLastFolder: Boolean = true,
    val hideHidden: Boolean = false,
    val lastPath: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val seedColor: Long? = null,
    val defaultSort: SortBy = SortBy.NAME,
    val defaultView: String = "list",
    val foldersFirst: Boolean = true,
    val confirmDelete: Boolean = true,
    val rarEnabled: Boolean = false,
    val rarWriteEnabled: Boolean = false,
    val elevationMode: String = "off",
    val safAutoFallback: Boolean = true,
    val seeDevicesInUi: Boolean = false,
    val autoRefresh: Boolean = false,
    val equalShapes: Boolean = false,
    val copyPathQuotes: Boolean = false,
    val systemBrowsing: Boolean = false,
    val drawerTabs: List<String> = DEFAULT_DRAWER_TABS,
    val searchInContent: Boolean = false,
    val searchInArchives: Boolean = true,
    val searchCaseSensitive: Boolean = false,
    val searchMaxScanMb: Int = 5
) {
    val trashEnabled: Boolean get() = drawerTabs.contains(DRAWER_TAB_TRASH)
    val historyEnabled: Boolean get() = drawerTabs.contains(DRAWER_TAB_HISTORY)
}

class SettingsRepository(private val appContext: Context) {

    companion object {
        const val MAX_RECENT_FOLDERS = 5
    }

    private object Keys {
        val OPEN_LAST_FOLDER = booleanPreferencesKey("open_last_folder")
        val HIDE_HIDDEN = booleanPreferencesKey("hide_hidden")
        val LAST_PATH = stringPreferencesKey("last_path")
        val RECENT_FOLDERS = stringPreferencesKey("recent_folders")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = longPreferencesKey("seed_color")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val TRASH_ENABLED = booleanPreferencesKey("trash_enabled")
        val RAR_ENABLED = booleanPreferencesKey("rar_enabled")
        val ELEVATION_MODE = stringPreferencesKey("elevation_mode")
        val RAR_WRITE_ENABLED = booleanPreferencesKey("rar_write_enabled")
        val SAF_AUTO_FALLBACK = booleanPreferencesKey("saf_auto_fallback")
        val SEE_DEVICES_IN_UI = booleanPreferencesKey("see_devices_in_ui")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        val EQUAL_SHAPES = booleanPreferencesKey("equal_shapes")
        val COPY_PATH_QUOTES = booleanPreferencesKey("copy_path_quotes")
        val SYSTEM_BROWSING = booleanPreferencesKey("system_browsing")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val DRAWER_TABS = stringPreferencesKey("drawer_tabs")
        val SEARCH_IN_CONTENT = booleanPreferencesKey("search_in_content")
        val SEARCH_IN_ARCHIVES = booleanPreferencesKey("search_in_archives")
        val SEARCH_CASE_SENSITIVE = booleanPreferencesKey("search_case_sensitive")
        val SEARCH_MAX_SCAN_MB = intPreferencesKey("search_max_scan_mb")
        val FAVORITES = stringSetPreferencesKey("favorite_paths")
        val FAVORITE_ORDER = stringPreferencesKey("favorite_order")
    }

    val favoriteOrder: Flow<List<String>> = appContext.dataStore.data.map { p ->
        favoriteOrderOf(p)
    }

    val favorites: Flow<Set<String>> = favoriteOrder.map { it.toSet() }

    suspend fun toggleFavorite(path: String): Boolean {
        var added = false
        appContext.dataStore.edit { p ->
            val current = favoriteOrderOf(p)
            added = !current.contains(path)
            p[Keys.FAVORITE_ORDER] = (if (added) current + path else current - path).joinToString("\n")
        }
        return added
    }

    suspend fun removeFavorite(path: String) = appContext.dataStore.edit { p ->
        p[Keys.FAVORITE_ORDER] = favoriteOrderOf(p).filter { it != path }.joinToString("\n")
    }

    suspend fun setFavoriteOrder(paths: List<String>) = appContext.dataStore.edit { p ->
        p[Keys.FAVORITE_ORDER] = paths.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    }

    private fun favoriteOrderOf(p: Preferences): List<String> {
        p[Keys.FAVORITE_ORDER]?.let { raw ->
            return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
        return p[Keys.FAVORITES]?.filter { it.isNotEmpty() }?.sorted() ?: emptyList()
    }

    val recentFolders: Flow<List<String>> = appContext.dataStore.data.map { p ->
        p[Keys.RECENT_FOLDERS]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun pushRecentFolder(path: String) = appContext.dataStore.edit { p ->
        val updated = (listOf(path) + (p[Keys.RECENT_FOLDERS]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()))
            .distinct()
            .take(MAX_RECENT_FOLDERS)
        p[Keys.RECENT_FOLDERS] = updated.joinToString("\n")
    }

    val settings: Flow<AppSettings> = appContext.dataStore.data.map { p ->
        AppSettings(
            openLastFolder = p[Keys.OPEN_LAST_FOLDER] ?: true,
            hideHidden = p[Keys.HIDE_HIDDEN] ?: false,
            lastPath = p[Keys.LAST_PATH],
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            seedColor = p[Keys.SEED_COLOR],
            defaultSort = p[Keys.DEFAULT_SORT]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() }
                ?: SortBy.NAME,
            defaultView = p[Keys.DEFAULT_VIEW] ?: "list",
            foldersFirst = p[Keys.FOLDERS_FIRST] ?: true,
            confirmDelete = p[Keys.CONFIRM_DELETE] ?: true,
            rarEnabled = p[Keys.RAR_ENABLED] ?: false,
            rarWriteEnabled = p[Keys.RAR_WRITE_ENABLED] ?: false,
            elevationMode = p[Keys.ELEVATION_MODE] ?: "off",
            safAutoFallback = p[Keys.SAF_AUTO_FALLBACK] ?: true,
            seeDevicesInUi = p[Keys.SEE_DEVICES_IN_UI] ?: false,
            autoRefresh = p[Keys.AUTO_REFRESH] ?: false,
            equalShapes = p[Keys.EQUAL_SHAPES] ?: false,
            copyPathQuotes = p[Keys.COPY_PATH_QUOTES] ?: false,
            systemBrowsing = p[Keys.SYSTEM_BROWSING] ?: false,
            drawerTabs = parseDrawerTabs(p[Keys.DRAWER_TABS]).ifEmpty {
                migrateDrawerTabs(p[Keys.HISTORY_ENABLED] ?: true, p[Keys.TRASH_ENABLED] ?: false)
            },
            searchInContent = p[Keys.SEARCH_IN_CONTENT] ?: false,
            searchInArchives = p[Keys.SEARCH_IN_ARCHIVES] ?: true,
            searchCaseSensitive = p[Keys.SEARCH_CASE_SENSITIVE] ?: false,
            searchMaxScanMb = p[Keys.SEARCH_MAX_SCAN_MB] ?: 5
        )
    }

    suspend fun setOpenLastFolder(value: Boolean) =
        appContext.dataStore.edit { it[Keys.OPEN_LAST_FOLDER] = value }

    suspend fun setHideHidden(value: Boolean) =
        appContext.dataStore.edit { it[Keys.HIDE_HIDDEN] = value }

    suspend fun setLastPath(value: String?) = appContext.dataStore.edit { p ->
        if (value == null) p.remove(Keys.LAST_PATH) else p[Keys.LAST_PATH] = value
    }

    suspend fun setThemeMode(value: ThemeMode) =
        appContext.dataStore.edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setDynamicColor(value: Boolean) = appContext.dataStore.edit { p ->
        p[Keys.DYNAMIC_COLOR] = value
        if (value) p.remove(Keys.SEED_COLOR)
    }

    suspend fun setSeedColor(argb: Long) = appContext.dataStore.edit { p ->
        p[Keys.SEED_COLOR] = argb
        p[Keys.DYNAMIC_COLOR] = false
    }

    suspend fun setDefaultSort(value: SortBy) =
        appContext.dataStore.edit { it[Keys.DEFAULT_SORT] = value.name }

    suspend fun setDefaultView(value: String) =
        appContext.dataStore.edit { it[Keys.DEFAULT_VIEW] = value }

    suspend fun setFoldersFirst(value: Boolean) =
        appContext.dataStore.edit { it[Keys.FOLDERS_FIRST] = value }

    suspend fun setConfirmDelete(value: Boolean) =
        appContext.dataStore.edit { it[Keys.CONFIRM_DELETE] = value }

    suspend fun setDrawerTabs(ids: List<String>) = appContext.dataStore.edit { p ->
        p[Keys.DRAWER_TABS] = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    }

    suspend fun setRarEnabled(value: Boolean) =
        appContext.dataStore.edit {
            it[Keys.RAR_ENABLED] = value
            if (!value) it[Keys.RAR_WRITE_ENABLED] = false
        }

    suspend fun setRarWriteEnabled(value: Boolean) =
        appContext.dataStore.edit {
            it[Keys.RAR_WRITE_ENABLED] = value
            if (value) it[Keys.RAR_ENABLED] = true
        }

    suspend fun setElevationMode(value: String) =
        appContext.dataStore.edit { it[Keys.ELEVATION_MODE] = value }

    suspend fun setSafAutoFallback(value: Boolean) =
        appContext.dataStore.edit { it[Keys.SAF_AUTO_FALLBACK] = value }

    suspend fun setSeeDevicesInUi(value: Boolean) =
        appContext.dataStore.edit { it[Keys.SEE_DEVICES_IN_UI] = value }

    suspend fun setAutoRefresh(value: Boolean) =
        appContext.dataStore.edit { it[Keys.AUTO_REFRESH] = value }

    suspend fun setSystemBrowsing(value: Boolean) =
        appContext.dataStore.edit { it[Keys.SYSTEM_BROWSING] = value }

    suspend fun setEqualShapes(value: Boolean) =
        appContext.dataStore.edit { it[Keys.EQUAL_SHAPES] = value }

    suspend fun setCopyPathQuotes(value: Boolean) =
        appContext.dataStore.edit { it[Keys.COPY_PATH_QUOTES] = value }

    suspend fun setSearchInContent(value: Boolean) =
        appContext.dataStore.edit { it[Keys.SEARCH_IN_CONTENT] = value }

    suspend fun setSearchInArchives(value: Boolean) =
        appContext.dataStore.edit { it[Keys.SEARCH_IN_ARCHIVES] = value }

    suspend fun setSearchCaseSensitive(value: Boolean) =
        appContext.dataStore.edit { it[Keys.SEARCH_CASE_SENSITIVE] = value }

    suspend fun setSearchMaxScanMb(value: Int) =
        appContext.dataStore.edit { it[Keys.SEARCH_MAX_SCAN_MB] = value }

}
