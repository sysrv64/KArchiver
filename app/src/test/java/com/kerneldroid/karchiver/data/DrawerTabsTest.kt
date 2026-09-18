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
    fun enablingTrashAddsTabBeforeSettings() {
        val result = syncTrashTab(listOf("files", "home", "recents", "settings"), true)
        assertEquals(listOf("files", "home", "recents", "trash", "settings"), result)
    }

    @Test
    fun enablingTrashKeepsExistingPosition() {
        val current = listOf("files", "trash", "home", "settings")
        assertEquals(current, syncTrashTab(current, true))
    }

    @Test
    fun disablingTrashRemovesTab() {
        val result = syncTrashTab(listOf("files", "trash", "home", "settings"), false)
        assertEquals(listOf("files", "home", "settings"), result)
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
    fun defaultsContainRecentsAndNoTrash() {
        assertTrue(DEFAULT_DRAWER_TABS.contains("recents"))
        assertFalse(DEFAULT_DRAWER_TABS.contains(DRAWER_TAB_TRASH))
    }
}
