@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kerneldroid.karchiver.presentation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kerneldroid.karchiver.data.AppSettings
import com.kerneldroid.karchiver.data.DEFAULT_DRAWER_TABS
import com.kerneldroid.karchiver.data.SettingsRepository
import com.kerneldroid.karchiver.data.insertDrawerTab
import com.kerneldroid.karchiver.data.formatBytes
import com.kerneldroid.karchiver.data.history.HistoryEntry
import com.kerneldroid.karchiver.data.loadVolumeStats
import com.kerneldroid.karchiver.data.storage.AppVolume
import com.kerneldroid.karchiver.data.storage.VolumeKind
import com.kerneldroid.karchiver.presentation.browser.BrowserScreen
import com.kerneldroid.karchiver.presentation.browser.BrowserViewModel
import com.kerneldroid.karchiver.presentation.browser.SearchSettings
import com.kerneldroid.karchiver.presentation.browser.ViewMode
import com.kerneldroid.karchiver.presentation.components.CustomNavigationDrawerItem
import com.kerneldroid.karchiver.presentation.components.ReorderableColumn
import com.kerneldroid.karchiver.presentation.components.holdToReveal
import com.kerneldroid.karchiver.presentation.home.HomeScreen
import com.kerneldroid.karchiver.presentation.history.HistoryScreen
import com.kerneldroid.karchiver.presentation.recents.RecentsScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsAboutScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsAppearanceScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsCategory
import com.kerneldroid.karchiver.presentation.settings.SettingsElevationScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsFilesScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsSearchScreen
import com.kerneldroid.karchiver.presentation.settings.SettingsStorageScreen
import com.kerneldroid.karchiver.presentation.trash.TrashScreen
import java.io.File
import kotlinx.coroutines.launch

private object RootRoute {
    const val BROWSER = "browser"
    const val HOME = "home"
    const val HISTORY = "history"
    const val RECENTS = "recents"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
}

private val DrawerSheetWidth = 280.dp
private val DeviceUsageBarWidth = 168.dp
private val DrawerTabHeight = 56.dp
private val RestoreHoldHeight = 56.dp
private val DrawerTabSpacing = 4.dp

private enum class DrawerTab(
    val id: String,
    val route: String,
    val title: String,
    val icon: ImageVector,
    val mandatory: Boolean = false
) {
    FILES("files", RootRoute.BROWSER, "Files", Icons.Filled.Folder, mandatory = true),
    HOME("home", RootRoute.HOME, "Home", Icons.Filled.Home),
    RECENTS("recents", RootRoute.RECENTS, "Recents", Icons.Filled.Schedule),
    HISTORY("history", RootRoute.HISTORY, "History", Icons.Filled.History),
    TRASH("trash", RootRoute.TRASH, "Trash", Icons.Filled.Delete),
    SETTINGS("settings", RootRoute.SETTINGS, "Settings", Icons.Filled.Settings, mandatory = true);

    companion object {
        fun fromId(id: String): DrawerTab? = entries.firstOrNull { it.id == id }
    }
}


private fun normalizeDrawerTabs(order: List<String>): List<DrawerTab> {
    val parsed = order.mapNotNull { DrawerTab.fromId(it) }.toMutableList()
    if (parsed.none { it == DrawerTab.FILES }) parsed.add(0, DrawerTab.FILES)
    if (parsed.none { it == DrawerTab.SETTINGS }) parsed.add(DrawerTab.SETTINGS)
    return parsed
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun DeviceDrawerRow(
    volume: AppVolume,
    usedBytes: Long?,
    totalBytes: Long?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (volume.isPrimary) Icons.Filled.Smartphone
                else if (volume.kind == VolumeKind.USB) Icons.Filled.Usb
                else Icons.Filled.SdStorage,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = volume.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (usedBytes != null && totalBytes != null && totalBytes > 0) {
            Text(
                text = "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            LinearProgressIndicator(
                progress = { (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.width(DeviceUsageBarWidth).padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun KArchiverRoot() {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    val settings by produceState<AppSettings?>(initialValue = null, settingsRepo) {
        settingsRepo.settings.collect { value = it }
    }
    val vm: BrowserViewModel = viewModel()
    val browserState by vm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val motionScheme = MaterialTheme.motionScheme
    val recents by settingsRepo.recentFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    var ready by remember { mutableStateOf(false) }
    var historyPrimed by remember { mutableStateOf(false) }
    var barLifted by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val activeOp by vm.archiveOp.collectAsStateWithLifecycle()
    val progressDialogVisible by vm.progressDialogVisible.collectAsStateWithLifecycle()
    val storageVolumes by vm.volumes.collectAsStateWithLifecycle()
    val safGrants by vm.safGrants.collectAsStateWithLifecycle()
    val forcedSaf by vm.forcedSaf.collectAsStateWithLifecycle()
    val safAutoFallback by vm.safAutoFallback.collectAsStateWithLifecycle()
    val favorites by settingsRepo.favorites.collectAsStateWithLifecycle(initialValue = emptySet())

    val drawerOrder = settings?.drawerTabs ?: DEFAULT_DRAWER_TABS
    val storedTabs = remember(drawerOrder) { normalizeDrawerTabs(drawerOrder) }
    val drawerTabs = remember {
        mutableStateListOf<DrawerTab>().apply { addAll(storedTabs) }
    }
    LaunchedEffect(storedTabs) {
        val ids = storedTabs.map { it.id }
        if (drawerTabs.map { it.id } != ids) {
            drawerTabs.clear()
            drawerTabs.addAll(storedTabs)
        }
    }
    var tabMenuId by remember { mutableStateOf<String?>(null) }
    var emptyMenu by remember { mutableStateOf(false) }

    fun openDrawer() {
        drawerScope.launch { drawerState.open() }
    }

    fun selectDestination(route: String) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        drawerScope.launch { drawerState.close() }
    }

    fun openFavorite(path: String) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        val parent = File(path).parentFile
        if (parent == null || !parent.exists()) {
            drawerScope.launch {
                settingsRepo.removeFavorite(path)
                drawerState.close()
            }
        } else {
            vm.navigateTo(parent)
            selectDestination(RootRoute.BROWSER)
        }
    }

    fun openHistoryEntry(entry: HistoryEntry): Boolean {
        val file = File(entry.path)
        val dir = when {
            entry.isDirectory && file.isDirectory -> file
            file.exists() -> file.parentFile
            else -> null
        }
        if (dir == null || !dir.isDirectory) return false
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        vm.navigateTo(dir)
        navController.navigate(RootRoute.BROWSER) {
            popUpTo(navController.graph.startDestinationId)
            launchSingleTop = true
        }
        return true
    }

    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        vm.initialize(
            s.lastPath.takeIf { s.openLastFolder },
            s.hideHidden,
            s.defaultSort,
            if (s.defaultView == "grid") ViewMode.GRID else ViewMode.LIST,
            s.foldersFirst,
            s.rarEnabled,
            s.rarWriteEnabled,
            s.elevationMode,
            context.applicationContext,
            s.safAutoFallback,
            s.systemBrowsing
        )
        vm.syncSafPrefs(s.safAutoFallback)
        vm.setHideHidden(s.hideHidden)
        vm.setRarEnabled(s.rarEnabled)
        vm.setRarWriteEnabled(s.rarWriteEnabled)
        vm.setElevationMode(s.elevationMode)
        vm.setSystemBrowsing(s.systemBrowsing)
        vm.setHistoryEnabled(s.historyEnabled)
        vm.setTrashEnabled(s.trashEnabled)
        vm.setSearchSettings(
            SearchSettings(
                searchInContent = s.searchInContent,
                searchInArchives = s.searchInArchives,
                caseSensitive = s.searchCaseSensitive,
                maxScanMb = s.searchMaxScanMb
            )
        )
        ready = true
    }

    LaunchedEffect(
        settings?.defaultSort,
        settings?.defaultView,
        settings?.foldersFirst,
        ready
    ) {
        val s = settings ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        vm.applyExplorerPrefs(
            s.defaultSort,
            if (s.defaultView == "grid") ViewMode.GRID else ViewMode.LIST,
            s.foldersFirst
        )
    }

    LaunchedEffect(Unit) {
        vm.setTempDir(context.cacheDir)
        vm.bindFavorites(settingsRepo)
    }

    LaunchedEffect(browserState.currentDir.absolutePath, ready) {
        if (ready) {
            val path = browserState.currentDir.absolutePath
            settingsRepo.setLastPath(path)
            if (historyPrimed) settingsRepo.pushRecentFolder(path)
            historyPrimed = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = DrawerSheetWidth)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(30.dp))
                    ReorderableColumn(
                        items = drawerTabs,
                        itemKey = { it.id },
                        itemHeight = DrawerTabHeight,
                        itemSpacing = DrawerTabSpacing,
                        onMove = { from, to ->
                            if (from in drawerTabs.indices && to in drawerTabs.indices) {
                                val moved = drawerTabs.removeAt(from)
                                drawerTabs.add(to, moved)
                                drawerScope.launch { settingsRepo.setDrawerTabs(drawerTabs.map { it.id }) }
                            }
                        },
                        onHoldStill = { tab -> tabMenuId = tab.id }
                    ) { tab, _, _ ->
                        DrawerTabRow(
                            tab = tab,
                            selected = currentRoute == tab.route,
                            onSelected = { selectDestination(tab.route) },
                            menuExpanded = tabMenuId == tab.id,
                            onMenuDismiss = { tabMenuId = null },
                            onRemove = {
                                tabMenuId = null
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                drawerTabs.removeAll { it.id == tab.id }
                                drawerScope.launch { settingsRepo.setDrawerTabs(drawerTabs.map { it.id }) }
                            }
                        )
                    }
                    RestoreHoldArea(
                        shown = drawerTabs,
                        expanded = emptyMenu,
                        onExpandedChange = { emptyMenu = it },
                        onRestore = { tab ->
                            emptyMenu = false
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            if (drawerTabs.none { it.id == tab.id }) {
                                val ids = insertDrawerTab(drawerTabs.map { it.id }, tab.id)
                                drawerTabs.clear()
                                drawerTabs.addAll(ids.mapNotNull { DrawerTab.fromId(it) })
                                drawerScope.launch { settingsRepo.setDrawerTabs(ids) }
                            }
                        }
                    )
                    if (favorites.isNotEmpty()) {
                        DrawerSectionLabel("Favorites")
                        val favFiles = remember(favorites) { favorites.map { File(it) } }
                        val favDirs = favFiles.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                        val favRest = (favFiles - favDirs.toSet()).sortedBy { it.name.lowercase() }
                        favDirs.forEach { f ->
                            CustomNavigationDrawerItem(
                                selected = false,
                                onSelected = { openFavorite(f.absolutePath) },
                                icon = Icons.Filled.Folder,
                                text = f.name.ifEmpty { f.absolutePath }
                            )
                        }
                        if (favDirs.isNotEmpty() && favRest.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                        }
                        favRest.forEach { f ->
                            CustomNavigationDrawerItem(
                                selected = false,
                                onSelected = { openFavorite(f.absolutePath) },
                                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                                text = f.name.ifEmpty { f.absolutePath }
                            )
                        }
                    }
                    if (settings?.seeDevicesInUi == true && storageVolumes.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                        DrawerSectionLabel("Devices")
                        val stats = remember(storageVolumes) {
                            loadVolumeStats(context).associateBy { it.path }
                        }
                        storageVolumes.forEach { v ->
                            val stat = stats[v.root.absolutePath]
                            DeviceDrawerRow(
                                volume = v,
                                usedBytes = stat?.usedBytes,
                                totalBytes = stat?.totalBytes,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    vm.switchVolume(v)
                                    selectDestination(RootRoute.BROWSER)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = RootRoute.BROWSER,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeIn(animationSpec = motionScheme.fastEffectsSpec())
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeIn(animationSpec = motionScheme.fastEffectsSpec())
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        }
    ) {
        composable(RootRoute.BROWSER) {
            BrowserScreen(
                vm = vm,
                confirmDelete = settings?.confirmDelete != false,
                rarEnabled = settings?.rarEnabled == true,
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted },
                onOpenDrawer = ::openDrawer,
                onOpenSettings = { navController.navigate(RootRoute.SETTINGS) }
            )
        }
        composable(RootRoute.HOME) {
            HomeScreen(
                onOpenPath = { path ->
                    vm.navigateTo(File(path))
                    navController.navigate(RootRoute.BROWSER) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                onOpenDrawer = ::openDrawer,
                onBack = { navController.popBackStack() },
                recentFolders = recents,
                historyEnabled = settings?.historyEnabled != false,
                onOpenHistory = { navController.navigate(RootRoute.HISTORY) },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(RootRoute.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = ::openHistoryEntry,
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(RootRoute.TRASH) {
            TrashScreen(
                onBack = { navController.popBackStack() },
                elevationMode = settings?.elevationMode ?: "off",
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(RootRoute.RECENTS) {
            RecentsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenBrowser = {
                    navController.navigate(RootRoute.BROWSER) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(RootRoute.SETTINGS) {
            SettingsScreen(
                settings = settings ?: AppSettings(),
                onBack = { navController.popBackStack() },
                onOpen = { category -> navController.navigate(category.route) },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(SettingsCategory.APPEARANCE.route) {
            SettingsAppearanceScreen(
                settings = settings ?: AppSettings(),
                repo = settingsRepo,
                onBack = { navController.popBackStack() },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(SettingsCategory.FILES.route) {
            SettingsFilesScreen(
                settings = settings ?: AppSettings(),
                repo = settingsRepo,
                onBack = { navController.popBackStack() },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(SettingsCategory.SEARCH.route) {
            SettingsSearchScreen(
                settings = settings ?: AppSettings(),
                repo = settingsRepo,
                onBack = { navController.popBackStack() },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(SettingsCategory.STORAGE.route) {
            SettingsStorageScreen(
                safAutoFallback = safAutoFallback,
                onSetSafAutoFallback = { value ->
                    drawerScope.launch {
                        settingsRepo.setSafAutoFallback(value)
                        vm.syncSafPrefs(value)
                    }
                },
                safGrants = safGrants,
                storageVolumes = storageVolumes,
                forcedSaf = forcedSaf,
                onForgetGrant = vm::forgetGrant,
                onSetForceSaf = vm::setForceSaf,
                onGrantPicked = { uri, volumeId ->
                    drawerScope.launch { vm.onTreeGranted(uri, volumeId) }
                },
                onBack = { navController.popBackStack() },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(SettingsCategory.ELEVATION.route) {
            SettingsElevationScreen(
                settings = settings ?: AppSettings(),
                repo = settingsRepo,
                onBack = { navController.popBackStack() },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
        composable(SettingsCategory.ABOUT.route) {
            SettingsAboutScreen(
                onBack = { navController.popBackStack() },
                barLifted = barLifted,
                onToggleBar = { barLifted = !barLifted }
            )
        }
    }
    if (activeOp != null && !progressDialogVisible && currentRoute != RootRoute.BROWSER) {
        val op = activeOp
        if (op != null) {
            val fraction = if (op.total > 0L) {
                (op.done.toFloat() / op.total.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            Surface(
                onClick = {
                    navController.navigate(RootRoute.BROWSER) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    vm.showProgressDialog()
                },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (fraction != null) {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = op.label + " • " + (fraction * 100).toInt() + "%",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = op.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun DrawerTabRow(
    tab: DrawerTab,
    selected: Boolean,
    onSelected: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        CustomNavigationDrawerItem(
            selected = selected,
            onSelected = onSelected,
            icon = tab.icon,
            text = tab.title
        )
        DropdownMenuPopup(
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(if (tab.mandatory) "${tab.title} can't be removed" else "Remove ${tab.title}")
                    },
                    trailingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(20.dp)) },
                    enabled = !tab.mandatory,
                    onClick = onRemove
                )
            }
        }
    }
}

@Composable
private fun RestoreHoldArea(
    shown: List<DrawerTab>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRestore: (DrawerTab) -> Unit
) {
    val shownIds = shown.map { it.id }.toSet()
    val restorable = DrawerTab.entries.filter { it.id !in shownIds && !it.mandatory }
    if (restorable.isEmpty()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RestoreHoldHeight)
            .holdToReveal { onExpandedChange(true) }
    ) {
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1)
            ) {
                restorable.forEach { tab ->
                    DropdownMenuItem(
                        text = { Text("Add ${tab.title}") },
                        trailingIcon = { Icon(tab.icon, null, Modifier.size(20.dp)) },
                        onClick = { onRestore(tab) }
                    )
                }
            }
        }
    }
}
