package com.kerneldroid.karchiver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerTabsTest {

    @Test
    fun insertsBeforeSettings() {
        val result = insertDrawerTab(listOf("files", "home", "settings"), "recents")
        assertEquals(listOf("files", "home", "recents", "settings"), result)
    }

    @Test
    fun appendsWhenSettingsAbsent() {
        val result = insertDrawerTab(listOf("files", "home"), "recents")
        assertEquals(listOf("files", "home", "recents"), result)
    }

    @Test
    fun parseTrimsAndDeduplicates() {
        val parsed = parseDrawerTabs(" files \nhome\n\nfiles\nsettings ")
        assertEquals(listOf("files", "home", "settings"), parsed)
    }

    @Test
    fun parseHandlesNull() {
        assertTrue(parseDrawerTabs(null).isEmpty())
    }

    @Test
    fun defaultsContainHistoryAndRecentsWithoutTrash() {
        assertTrue(DEFAULT_DRAWER_TABS.contains("recents"))
        assertTrue(DEFAULT_DRAWER_TABS.contains(DRAWER_TAB_HISTORY))
        assertFalse(DEFAULT_DRAWER_TABS.contains(DRAWER_TAB_TRASH))
    }

    @Test
    fun migrationKeepsHistoryAndTrashPreferences() {
        val tabs = migrateDrawerTabs(historyEnabled = true, trashEnabled = true)
        assertEquals(
            listOf("files", "home", "recents", "history", "trash", "settings"),
            tabs
        )
    }

    @Test
    fun migrationDropsDisabledTabs() {
        val tabs = migrateDrawerTabs(historyEnabled = false, trashEnabled = false)
        assertEquals(listOf("files", "home", "recents", "settings"), tabs)
    }
}
