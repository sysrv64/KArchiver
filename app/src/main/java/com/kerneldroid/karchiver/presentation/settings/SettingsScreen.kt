package com.kerneldroid.karchiver.presentation.settings

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerneldroid.karchiver.BuildConfig
import com.kerneldroid.karchiver.data.AppSettings
import com.kerneldroid.karchiver.data.log.LogReport
import com.kerneldroid.karchiver.data.SettingsRepository
import com.kerneldroid.karchiver.data.SortBy
import com.kerneldroid.karchiver.data.ThemeMode
import com.kerneldroid.karchiver.data.elevation.RootEngine
import com.kerneldroid.karchiver.data.elevation.RootStatus
import com.kerneldroid.karchiver.data.elevation.ShizukuEngine
import com.kerneldroid.karchiver.data.elevation.ShizukuEngine.ShizukuStatus
import com.kerneldroid.karchiver.data.storage.AppVolume
import com.kerneldroid.karchiver.data.storage.VolumeKind
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold
import com.kerneldroid.karchiver.presentation.components.detectBarHold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SeedColor(val color: Color, val name: String)

private val seedColors = listOf(
    SeedColor(Color(0xFFFFA79B), "Red"),
    SeedColor(Color(0xFFFFB2BD), "Rose"),
    SeedColor(Color(0xFFD7BBFC), "Purple"),
    SeedColor(Color(0xFFC5C0FF), "Indigo"),
    SeedColor(Color(0xFFB0C6FF), "Blue"),
    SeedColor(Color(0xFF86D1EA), "Cyan"),
    SeedColor(Color(0xFF82D5C7), "Teal"),
    SeedColor(Color(0xFF9CD59F), "Green"),
    SeedColor(Color(0xFFC3CD7C), "Chartreuse"),
    SeedColor(Color(0xFFE8C16C), "Yellow"),
    SeedColor(Color(0xFFFFB68D), "Orange")
)

private data class ThemeOption(val mode: ThemeMode, val label: String, val icon: ImageVector)

private val themeOptions = listOf(
    ThemeOption(ThemeMode.SYSTEM, "System", Icons.Filled.BrightnessAuto),
    ThemeOption(ThemeMode.LIGHT, "Light", Icons.Filled.LightMode),
    ThemeOption(ThemeMode.DARK, "Dark", Icons.Filled.DarkMode),
    ThemeOption(ThemeMode.OLED, "OLED", Icons.Filled.Contrast)
)

private val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val scanSizes = listOf(1, 5, 20, 100)

enum class SettingsCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    APPEARANCE("settings/appearance", "Appearance", "Theme, dynamic color, palettes", Icons.Filled.Palette),
    FILES("settings/files", "Files", "Sorting, hidden files, RAR, history", Icons.Filled.SortByAlpha),
    SEARCH("settings/search", "Search", "Content search, archives, scan limit", Icons.Filled.Search),
    STORAGE("settings/storage", "Storage", "Access mode, granted folders", Icons.Filled.Storage),
    ELEVATION("settings/elevation", "Elevation", "Shizuku or root access", Icons.Filled.Security),
    ABOUT("settings/about", "About", "Version, license, links", Icons.Filled.Info)
}

@Composable
private fun categoryAccent(category: SettingsCategory): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (category.ordinal % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    BackHandler { onBack() }
    RoundedTopScaffold(
        barLifted = barLifted,
        topBar = {
            TopAppBar(
                modifier = Modifier.detectBarHold {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleBar()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpen: (SettingsCategory) -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    SettingsScaffold("Settings", onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingsCategory.entries.forEachIndexed { index, category ->
                val (container, onContainer) = categoryAccent(category)
                SegmentedListItem(
                    onClick = { onOpen(category) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = SettingsCategory.entries.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 14.dp),
                    leadingContent = {
                        Icon(
                            category.icon,
                            null,
                            tint = onContainer,
                            modifier = Modifier
                                .background(container, CircleShape)
                                .padding(10.dp)
                        )
                    },
                    supportingContent = {
                        Text(
                            text = category.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, null) }
                ) {
                    Text(category.title, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsAppearanceScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    SettingsScaffold("Appearance", onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = {
                    AnimatedContent(themeOptions.first { it.mode == settings.themeMode }.icon) {
                        Icon(it, null)
                    }
                },
                supportingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        themeOptions.forEachIndexed { index, option ->
                            val selected = settings.themeMode == option.mode
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    scope.launch { repo.setThemeMode(option.mode) }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    themeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton }
                            ) {
                                Icon(option.icon, option.label)
                            }
                        }
                    }
                }
            ) {
                Text("Theme")
            }
            SegmentedListItem(
                onClick = {
                    if (!supportsDynamic) return@SegmentedListItem
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    scope.launch {
                        if (settings.dynamicColor) {
                            repo.setSeedColor(seedColors.first().color.value.toLong())
                        } else {
                            repo.setDynamicColor(true)
                        }
                    }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Palette, null) },
                supportingContent = {
                    Text(
                        if (supportsDynamic) "Follow the wallpaper colors"
                        else "Requires Android 12 or newer"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.dynamicColor,
                        enabled = supportsDynamic,
                        onCheckedChange = { checked ->
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            scope.launch {
                                if (checked) repo.setDynamicColor(true)
                                else repo.setSeedColor(
                                    settings.seedColor ?: seedColors.first().color.value.toLong()
                                )
                            }
                        }
                    )
                },
            ) {
                Text("Dynamic color")
            }
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                supportingContent = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            if (settings.dynamicColor || settings.seedColor == null) "Dynamic"
                            else seedColors.firstOrNull { it.color.value.toLong() == settings.seedColor }?.name
                                ?: "Custom"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            itemsIndexed(seedColors) { index, seed ->
                                val argb = seed.color.value.toLong()
                                val selected = !settings.dynamicColor && settings.seedColor == argb
                                val onSeedColor =
                                    if (seed.color.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White
                                ToggleButton(
                                    checked = selected,
                                    onCheckedChange = {
                                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        scope.launch { repo.setSeedColor(argb) }
                                    },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        seedColors.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = seed.color,
                                        contentColor = onSeedColor,
                                        checkedContainerColor = seed.color,
                                        checkedContentColor = onSeedColor
                                    ),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .widthIn(min = 40.dp)
                                        .semantics { role = Role.RadioButton }
                                ) {
                                    AnimatedContent(selected) { isSelected ->
                                        if (isSelected) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(seed.name, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Custom color")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsFilesScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var rarUnlockAt by remember { mutableLongStateOf(0L) }
    var rarUnlockJob by remember { mutableStateOf<Job?>(null) }
    val rarInteractions = remember { MutableInteractionSource() }
    LaunchedEffect(rarInteractions) {
        rarInteractions.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) {
                rarUnlockJob?.cancel()
                rarUnlockJob = null
            }
        }
    }
    SettingsScaffold("Files", onBack, barLifted, onToggleBar) {
        SectionHeader("Sorting and view")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.SortByAlpha, null) },
                supportingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        SortBy.entries.forEachIndexed { index, sort ->
                            val selected = settings.defaultSort == sort
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    scope.launch { repo.setDefaultSort(sort) }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    SortBy.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton }
                            ) {
                                Text(sortLabel(sort), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            ) {
                Text("Default sort")
            }
            SettingSwitch(
                index = 1,
                count = 4,
                title = "Folders first",
                subtitle = "Always list folders above files, no matter the sort order.",
                checked = settings.foldersFirst,
                onCheckedChange = { scope.launch { repo.setFoldersFirst(it) } }
            )
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = {
                    Icon(
                        if (settings.defaultView == "grid") Icons.Filled.ViewModule
                        else Icons.AutoMirrored.Filled.ViewList,
                        null
                    )
                },
                supportingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        val listSelected = settings.defaultView != "grid"
                        ToggleButton(
                            checked = listSelected,
                            onCheckedChange = {
                                if (!listSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                scope.launch { repo.setDefaultView("list") }
                            },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Text("List", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ToggleButton(
                            checked = !listSelected,
                            onCheckedChange = {
                                if (listSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                scope.launch { repo.setDefaultView("grid") }
                            },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Text("Grid", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            ) {
                Text("Default view")
            }
            SettingSwitch(
                index = 3,
                count = 4,
                title = "Equal grid cells",
                subtitle = "Give every grid cell the same size regardless of the name. Long names are clipped and scroll every few seconds.",
                checked = settings.equalShapes,
                onCheckedChange = { scope.launch { repo.setEqualShapes(it) } }
            )
        }
        SectionHeader("Behaviour")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingSwitch(
                index = 0,
                count = 7,
                title = "Open last folder",
                subtitle = "Return to the folder you were in when the app starts.",
                checked = settings.openLastFolder,
                onCheckedChange = { scope.launch { repo.setOpenLastFolder(it) } }
            )
            SettingSwitch(
                index = 1,
                count = 7,
                title = "Auto-refresh folder",
                subtitle = "Watch the open folder and load new or changed files automatically, without pull-to-refresh. Works in folders the app can read directly. Off by default.",
                checked = settings.autoRefresh,
                onCheckedChange = { scope.launch { repo.setAutoRefresh(it) } }
            )
            SettingSwitch(
                index = 2,
                count = 7,
                title = "Hide hidden files",
                subtitle = "Do not show files and folders whose name starts with a dot.",
                checked = settings.hideHidden,
                onCheckedChange = { scope.launch { repo.setHideHidden(it) } }
            )
            SettingSwitch(
                index = 3,
                count = 7,
                title = "Confirm before delete",
                subtitle = "Ask for confirmation before deleting files and folders.",
                checked = settings.confirmDelete,
                onCheckedChange = { scope.launch { repo.setConfirmDelete(it) } }
            )
            SettingSwitch(
                index = 4,
                count = 7,
                title = "See devices in UI",
                subtitle = "Show connected drives with used space in the navigation bar.",
                checked = settings.seeDevicesInUi,
                onCheckedChange = { scope.launch { repo.setSeeDevicesInUi(it) } }
            )
            SettingSwitch(
                index = 5,
                count = 7,
                title = "RAR support",
                subtitle = if (settings.rarWriteEnabled) "Read and write. Packing unlocked."
                    else "Read-only. Hold this row 5 seconds to unlock RAR packing.",
                checked = settings.rarEnabled,
                onCheckedChange = {
                    if (SystemClock.uptimeMillis() - rarUnlockAt < 1000L) return@SettingSwitch
                    scope.launch { repo.setRarEnabled(it) }
                },
                interactionSource = rarInteractions,
                onLongClick = {
                    if (!settings.rarWriteEnabled) {
                        rarUnlockJob?.cancel()
                        rarUnlockJob = scope.launch {
                            delay(5000L)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            rarUnlockAt = SystemClock.uptimeMillis()
                            repo.setRarWriteEnabled(true)
                        }
                    }
                }
            )
            SettingSwitch(
                index = 6,
                count = 7,
                title = "Quote copied paths",
                subtitle = "Wrap copied paths in single quotes, like '/sdcard/file.txt', for shell scripts and Termux.",
                checked = settings.copyPathQuotes,
                onCheckedChange = { scope.launch { repo.setCopyPathQuotes(it) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSearchScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    SettingsScaffold("Search", onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingSwitch(
                index = 0,
                count = 4,
                title = "Search inside file contents",
                subtitle = "Plain queries also match text inside files, not only names.",
                checked = settings.searchInContent,
                onCheckedChange = { scope.launch { repo.setSearchInContent(it) } }
            )
            SettingSwitch(
                index = 1,
                count = 4,
                title = "Search inside archives",
                subtitle = "Match entry names and entry contents inside zip, 7z, tar and rar archives.",
                checked = settings.searchInArchives,
                onCheckedChange = { scope.launch { repo.setSearchInArchives(it) } }
            )
            SettingSwitch(
                index = 2,
                count = 4,
                title = "Case-sensitive content search",
                subtitle = "Match the exact letter case when scanning file and entry contents.",
                checked = settings.searchCaseSensitive,
                onCheckedChange = { scope.launch { repo.setSearchCaseSensitive(it) } }
            )
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                supportingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        scanSizes.forEachIndexed { index, size ->
                            val selected = settings.searchMaxScanMb == size
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    scope.launch { repo.setSearchMaxScanMb(size) }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    scanSizes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton }
                            ) {
                                Text("$size MB", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            ) {
                Text("Per-file content scan limit")
            }
            Text(
                "Use content:\"text\" to match file contents and archive:\"name\" to match entries inside archives. Plain words match names, or contents when enabled above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsStorageScreen(
    safAutoFallback: Boolean = true,
    onSetSafAutoFallback: (Boolean) -> Unit = {},
    safGrants: Map<String, Uri> = emptyMap(),
    storageVolumes: List<AppVolume> = emptyList(),
    forcedSaf: Set<String> = emptySet(),
    onForgetGrant: (String) -> Unit = {},
    onSetForceSaf: (String, Boolean) -> Unit = { _, _ -> },
    onGrantPicked: (Uri, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    SettingsScaffold("Storage", onBack, barLifted, onToggleBar) {
        StorageSection(
            safAutoFallback = safAutoFallback,
            onSetSafAutoFallback = onSetSafAutoFallback,
            safGrants = safGrants,
            volumes = storageVolumes,
            forcedSaf = forcedSaf,
            onForgetGrant = onForgetGrant,
            onSetForceSaf = onSetForceSaf,
            onGrantPicked = onGrantPicked
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsElevationScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var showElevationDialog by remember { mutableStateOf(false) }
    val rootStatus by RootEngine.status.collectAsStateWithLifecycle(initialValue = RootStatus.Unknown)
    val shizukuStatus by ShizukuEngine.status.collectAsStateWithLifecycle(initialValue = ShizukuStatus.NoBinder)
    SettingsScaffold("Elevation", onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = { showElevationDialog = true },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Security, null) },
                supportingContent = {
                    Text(
                        when (settings.elevationMode) {
                            "shizuku" -> "Shizuku · ${shizukuStatusLabel(shizukuStatus)}"
                            "root" -> "Root · ${rootStatusLabel(rootStatus)}"
                            else -> "Off"
                        }
                    )
                },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) }
            ) {
                Text("Elevation")
            }
            SettingSwitch(
                index = 1,
                count = 2,
                title = "Browse system paths",
                subtitle = "Off by default. Lets you go above internal storage to /, /data, /data/data, /vendor and other users. /data and app data need Root; Shizuku can only read some system paths.",
                checked = settings.systemBrowsing,
                onCheckedChange = { scope.launch { repo.setSystemBrowsing(it) } }
            )
        }
    }
    if (showElevationDialog) {
        AlertDialog(
            onDismissRequest = { showElevationDialog = false },
            icon = { Icon(Icons.Filled.Security, null) },
            title = { Text("Elevation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ElevationOption(
                        selected = settings.elevationMode == "off",
                        title = "Off",
                        subtitle = "Never use elevated access.",
                        onSelect = { scope.launch { repo.setElevationMode("off") } }
                    )
                    ElevationOption(
                        selected = settings.elevationMode == "shizuku",
                        title = "Shizuku",
                        subtitle = shizukuStatusLabel(shizukuStatus),
                        onSelect = { scope.launch { repo.setElevationMode("shizuku") } }
                    )
                    ElevationOption(
                        selected = settings.elevationMode == "root",
                        title = "Root",
                        subtitle = rootStatusLabel(rootStatus),
                        onSelect = { scope.launch { repo.setElevationMode("root") } }
                    )
                    Text(
                        "Shell-backed Shizuku cannot open app-private data (SELinux policy). Root-backed Shizuku and Root see everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showElevationDialog = false }) { Text("Close") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (settings.elevationMode == "shizuku" && shizukuStatus == ShizukuStatus.PermissionRequired) {
                        TextButton(
                            enabled = activity != null,
                            onClick = { activity?.let { ShizukuEngine.requestPermission(it) } }
                        ) { Text("Request") }
                    }
                    if (settings.elevationMode == "root") {
                        TextButton(onClick = { scope.launch { RootEngine.refresh() } }) { Text("Recheck") }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsAboutScreen(
    onBack: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val links = listOf(
        "Source code" to "https://github.com/sysrv64/KArchiver",
        "Releases" to "https://github.com/sysrv64/KArchiver/releases",
        "Readme" to "https://github.com/sysrv64/KArchiver/blob/main/README.md",
        "Questions and answers" to "https://github.com/sysrv64/KArchiver/blob/main/QA.md",
        "Report an issue" to "https://github.com/sysrv64/KArchiver/issues"
    )
    SettingsScaffold("About", onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Tune, null) },
                supportingContent = { Text("Android 8.0 or newer") }
            ) {
                Text("KArchiver ${BuildConfig.VERSION_NAME}")
            }
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Description, null) },
                supportingContent = { Text("Application GPL-3.0-only, Rust core Apache-2.0") }
            ) {
                Text("License")
            }
        }
        SectionHeader("Diagnostics")
        val context = LocalContext.current
        val logScope = rememberCoroutineScope()
        var savingLogs by remember { mutableStateOf(false) }
        var logResult by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (savingLogs) return@Button
                    savingLogs = true
                    logResult = null
                    logScope.launch {
                        val content = LogReport.collect(context)
                        val file = LogReport.save(context, content)
                        logResult = if (file != null) "Saved to ${file.absolutePath}"
                        else "Could not save logs"
                        savingLogs = false
                    }
                },
                enabled = !savingLogs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (savingLogs) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (savingLogs) "Collecting logs…" else "Save logs to Downloads/KArchiver")
            }
            Text(
                "Saves app warnings and errors plus the Rust log into one file under Downloads/KArchiver.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            logResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        SectionHeader("Links")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            links.forEachIndexed { index, (label, url) ->
                SegmentedListItem(
                    onClick = { uriHandler.openUri(url) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = links.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    supportingContent = { Text(url.replace("https://", ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Icon(Icons.Filled.OpenInNew, null) }
                ) {
                    Text(label)
                }
            }
        }
    }
}

private fun sortLabel(sort: SortBy): String = when (sort) {
    SortBy.NAME -> "Name"
    SortBy.DATE -> "Date"
    SortBy.SIZE -> "Size"
    SortBy.TYPE -> "Type"
}

private fun rootStatusLabel(status: RootStatus): String = when (status) {
    RootStatus.Available -> "Root available"
    RootStatus.Denied -> "Root denied"
    RootStatus.Unavailable -> "No root"
    RootStatus.Unknown -> "Checking"
}

private fun shizukuStatusLabel(status: ShizukuStatus): String = when (status) {
    ShizukuStatus.Ready -> "Ready"
    ShizukuStatus.PermissionRequired -> "Permission required"
    ShizukuStatus.NoBinder -> "Shizuku not running"
    ShizukuStatus.Unavailable -> "Unavailable"
}

@Composable
private fun ElevationOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageSection(
    safAutoFallback: Boolean,
    onSetSafAutoFallback: (Boolean) -> Unit,
    safGrants: Map<String, Uri>,
    volumes: List<AppVolume>,
    forcedSaf: Set<String>,
    onForgetGrant: (String) -> Unit,
    onSetForceSaf: (String, Boolean) -> Unit,
    onGrantPicked: (Uri, String) -> Unit
) {
    var grantTargetId by remember { mutableStateOf<String?>(null) }
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val id = grantTargetId
        grantTargetId = null
        if (uri != null && id != null) onGrantPicked(uri, id)
    }
    fun labelFor(volumeId: String): String =
        volumes.firstOrNull { it.id == volumeId }?.label ?: volumeId
    fun iconFor(volumeId: String): ImageVector {
        val volume = volumes.firstOrNull { it.id == volumeId }
        return when {
            volume == null -> Icons.Filled.SdStorage
            volume.isPrimary -> Icons.Filled.Smartphone
            volume.kind == VolumeKind.USB -> Icons.Filled.Usb
            else -> Icons.Filled.SdStorage
        }
    }
    val ungranted = volumes.filter { it.isRemovable && !safGrants.containsKey(it.id) }
    val grantRows = safGrants.toList()
    val count = 2 + grantRows.size + ungranted.size
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            onClick = {},
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingContent = { Icon(Icons.Filled.Storage, null) },
            supportingContent = {
                Text("SD card and USB-OTG use direct file access on Android 11+. SAF is only a fallback.")
            }
        ) {
            Text("Native access")
        }
        SettingSwitch(
            index = 1,
            count = count,
            title = "Automatic SAF fallback",
            subtitle = "Use granted folders when direct access fails.",
            checked = safAutoFallback,
            onCheckedChange = onSetSafAutoFallback
        )
        grantRows.forEachIndexed { offset, (volumeId, _) ->
            val index = 2 + offset
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(iconFor(volumeId), null) },
                supportingContent = { Text("Granted folder. Switch prefers SAF over direct access.") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = volumeId in forcedSaf,
                            onCheckedChange = { onSetForceSaf(volumeId, it) }
                        )
                        TextButton(onClick = { onForgetGrant(volumeId) }) { Text("Forget") }
                    }
                }
            ) {
                Text(labelFor(volumeId), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ungranted.forEachIndexed { offset, volume ->
            val index = 2 + grantRows.size + offset
            SegmentedListItem(
                onClick = {
                    grantTargetId = volume.id
                    treePicker.launch(null)
                },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(iconFor(volume.id), null) },
                supportingContent = { Text("Direct access preferred. Grant a folder as fallback.") },
                trailingContent = {
                    TextButton(
                        onClick = {
                            grantTargetId = volume.id
                            treePicker.launch(null)
                        }
                    ) { Text("Grant") }
                }
            ) {
                Text(volume.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingSwitch(
    index: Int,
    count: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        interactionSource = interactionSource,
        onLongClick = onLongClick
    ) {
        Text(title)
    }
}
