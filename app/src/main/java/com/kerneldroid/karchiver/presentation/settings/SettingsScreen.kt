package com.kerneldroid.karchiver.presentation.settings

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerneldroid.karchiver.BuildConfig
import com.kerneldroid.karchiver.R
import com.kerneldroid.karchiver.data.AppSettings
import com.kerneldroid.karchiver.data.AppLanguage
import com.kerneldroid.karchiver.data.LocaleStore
import com.kerneldroid.karchiver.data.findActivity
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

private data class SeedColor(val color: Color, @StringRes val name: Int)

private val seedColors = listOf(
    SeedColor(Color(0xFFFFA79B), R.string.settings_seed_red),
    SeedColor(Color(0xFFFFB2BD), R.string.settings_seed_rose),
    SeedColor(Color(0xFFD7BBFC), R.string.settings_seed_purple),
    SeedColor(Color(0xFFC5C0FF), R.string.settings_seed_indigo),
    SeedColor(Color(0xFFB0C6FF), R.string.settings_seed_blue),
    SeedColor(Color(0xFF86D1EA), R.string.settings_seed_cyan),
    SeedColor(Color(0xFF82D5C7), R.string.settings_seed_teal),
    SeedColor(Color(0xFF9CD59F), R.string.settings_seed_green),
    SeedColor(Color(0xFFC3CD7C), R.string.settings_seed_chartreuse),
    SeedColor(Color(0xFFE8C16C), R.string.settings_seed_yellow),
    SeedColor(Color(0xFFFFB68D), R.string.settings_seed_orange)
)

private data class ThemeOption(val mode: ThemeMode, @StringRes val label: Int, val icon: ImageVector)

private val themeOptions = listOf(
    ThemeOption(ThemeMode.SYSTEM, R.string.settings_theme_system, Icons.Filled.BrightnessAuto),
    ThemeOption(ThemeMode.LIGHT, R.string.settings_theme_light, Icons.Filled.LightMode),
    ThemeOption(ThemeMode.DARK, R.string.settings_theme_dark, Icons.Filled.DarkMode),
    ThemeOption(ThemeMode.OLED, R.string.settings_theme_oled, Icons.Filled.Contrast)
)

private val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val scanSizes = listOf(1, 5, 20, 100)

enum class SettingsCategory(
    val route: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector
) {
    APPEARANCE("settings/appearance", R.string.settings_category_appearance, R.string.settings_category_appearance_subtitle, Icons.Filled.Palette),
    FILES("settings/files", R.string.settings_category_files, R.string.settings_category_files_subtitle, Icons.Filled.SortByAlpha),
    SEARCH("settings/search", R.string.settings_category_search, R.string.settings_category_search_subtitle, Icons.Filled.Search),
    STORAGE("settings/storage", R.string.settings_category_storage, R.string.settings_category_storage_subtitle, Icons.Filled.Storage),
    ELEVATION("settings/elevation", R.string.settings_category_elevation, R.string.settings_category_elevation_subtitle, Icons.Filled.Security),
    ABOUT("settings/about", R.string.settings_category_about, R.string.settings_category_about_subtitle, Icons.Filled.Info)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
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
    SettingsScaffold(stringResource(R.string.settings_title), onBack, barLifted, onToggleBar) {
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
                            text = stringResource(category.subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, null) }
                ) {
                    Text(stringResource(category.title), style = MaterialTheme.typography.labelLarge)
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
    val context = LocalContext.current
    var language by remember { mutableStateOf(LocaleStore.selected(context)) }
    var showLanguage by remember { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.settings_category_appearance), onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
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
                                Icon(option.icon, stringResource(option.label))
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_theme))
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
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Palette, null) },
                supportingContent = {
                    Text(
                        if (supportsDynamic) stringResource(R.string.settings_dynamic_color_subtitle)
                        else stringResource(R.string.settings_dynamic_color_requires)
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
                Text(stringResource(R.string.settings_dynamic_color))
            }
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                supportingContent = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            if (settings.dynamicColor || settings.seedColor == null) stringResource(R.string.settings_palette_dynamic)
                            else seedColors.firstOrNull { it.color.value.toLong() == settings.seedColor }?.let { stringResource(it.name) }
                                ?: stringResource(R.string.settings_palette_custom)
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
                                    colors = ToggleButtonDefaults.colors(
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
                                                Text(stringResource(seed.name), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_custom_color))
            }
            SegmentedListItem(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    showLanguage = true
                },
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Translate, null) },
                supportingContent = { Text(stringResource(R.string.settings_language_subtitle)) },
                trailingContent = {
                    Text(
                        languageLabel(language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
            ) {
                Text(stringResource(R.string.settings_language))
            }
        }
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (option != language) {
                                        language = option
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        context.findActivity()?.let { LocaleStore.applyLanguage(it, option) }
                                    }
                                    showLanguage = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = language == option, onClick = null)
                            Text(
                                languageLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.language_en)
    AppLanguage.RUSSIAN -> stringResource(R.string.language_ru)
    AppLanguage.CHINESE -> stringResource(R.string.language_zh)
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
    SettingsScaffold(stringResource(R.string.settings_category_files), onBack, barLifted, onToggleBar) {
        SectionHeader(stringResource(R.string.settings_section_sorting_view))
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
                Text(stringResource(R.string.settings_default_sort))
            }
            SettingSwitch(
                index = 1,
                count = 4,
                title = stringResource(R.string.settings_folders_first),
                subtitle = stringResource(R.string.settings_folders_first_subtitle),
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
                            Text(stringResource(R.string.settings_view_list), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            Text(stringResource(R.string.settings_view_grid), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_default_view))
            }
            SettingSwitch(
                index = 3,
                count = 4,
                title = stringResource(R.string.settings_equal_grid_cells),
                subtitle = stringResource(R.string.settings_equal_grid_cells_subtitle),
                checked = settings.equalShapes,
                onCheckedChange = { scope.launch { repo.setEqualShapes(it) } }
            )
        }
        SectionHeader(stringResource(R.string.settings_section_behaviour))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingSwitch(
                index = 0,
                count = 7,
                title = stringResource(R.string.settings_open_last_folder),
                subtitle = stringResource(R.string.settings_open_last_folder_subtitle),
                checked = settings.openLastFolder,
                onCheckedChange = { scope.launch { repo.setOpenLastFolder(it) } }
            )
            SettingSwitch(
                index = 1,
                count = 7,
                title = stringResource(R.string.settings_auto_refresh),
                subtitle = stringResource(R.string.settings_auto_refresh_subtitle),
                checked = settings.autoRefresh,
                onCheckedChange = { scope.launch { repo.setAutoRefresh(it) } }
            )
            SettingSwitch(
                index = 2,
                count = 7,
                title = stringResource(R.string.settings_hide_hidden),
                subtitle = stringResource(R.string.settings_hide_hidden_subtitle),
                checked = settings.hideHidden,
                onCheckedChange = { scope.launch { repo.setHideHidden(it) } }
            )
            SettingSwitch(
                index = 3,
                count = 7,
                title = stringResource(R.string.settings_confirm_delete),
                subtitle = stringResource(R.string.settings_confirm_delete_subtitle),
                checked = settings.confirmDelete,
                onCheckedChange = { scope.launch { repo.setConfirmDelete(it) } }
            )
            SettingSwitch(
                index = 4,
                count = 7,
                title = stringResource(R.string.settings_see_devices),
                subtitle = stringResource(R.string.settings_see_devices_subtitle),
                checked = settings.seeDevicesInUi,
                onCheckedChange = { scope.launch { repo.setSeeDevicesInUi(it) } }
            )
            SettingSwitch(
                index = 5,
                count = 7,
                title = stringResource(R.string.settings_rar_support),
                subtitle = if (settings.rarWriteEnabled) stringResource(R.string.settings_rar_subtitle_rw)
                    else stringResource(R.string.settings_rar_subtitle_ro),
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
                title = stringResource(R.string.settings_quote_paths),
                subtitle = stringResource(R.string.settings_quote_paths_subtitle),
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
    SettingsScaffold(stringResource(R.string.settings_category_search), onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingSwitch(
                index = 0,
                count = 4,
                title = stringResource(R.string.settings_search_in_content),
                subtitle = stringResource(R.string.settings_search_in_content_subtitle),
                checked = settings.searchInContent,
                onCheckedChange = { scope.launch { repo.setSearchInContent(it) } }
            )
            SettingSwitch(
                index = 1,
                count = 4,
                title = stringResource(R.string.settings_search_in_archives),
                subtitle = stringResource(R.string.settings_search_in_archives_subtitle),
                checked = settings.searchInArchives,
                onCheckedChange = { scope.launch { repo.setSearchInArchives(it) } }
            )
            SettingSwitch(
                index = 2,
                count = 4,
                title = stringResource(R.string.settings_search_case_sensitive),
                subtitle = stringResource(R.string.settings_search_case_sensitive_subtitle),
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
                                Text(stringResource(R.string.settings_scan_size_mb, size), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_scan_limit))
            }
            Text(
                stringResource(R.string.settings_search_help),
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
    SettingsScaffold(stringResource(R.string.settings_category_storage), onBack, barLifted, onToggleBar) {
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
    SettingsScaffold(stringResource(R.string.settings_category_elevation), onBack, barLifted, onToggleBar) {
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
                            "shizuku" -> stringResource(R.string.settings_elevation_shizuku_status, shizukuStatusLabel(shizukuStatus))
                            "root" -> stringResource(R.string.settings_elevation_root_status, rootStatusLabel(rootStatus))
                            else -> stringResource(R.string.settings_elevation_off)
                        }
                    )
                },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) }
            ) {
                Text(stringResource(R.string.settings_category_elevation))
            }
            SettingSwitch(
                index = 1,
                count = 2,
                title = stringResource(R.string.settings_browse_system_paths),
                subtitle = stringResource(R.string.settings_browse_system_paths_subtitle),
                checked = settings.systemBrowsing,
                onCheckedChange = { scope.launch { repo.setSystemBrowsing(it) } }
            )
        }
    }
    if (showElevationDialog) {
        AlertDialog(
            onDismissRequest = { showElevationDialog = false },
            icon = { Icon(Icons.Filled.Security, null) },
            title = { Text(stringResource(R.string.settings_category_elevation)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ElevationOption(
                        selected = settings.elevationMode == "off",
                        title = stringResource(R.string.settings_elevation_off),
                        subtitle = stringResource(R.string.settings_elevation_off_subtitle),
                        onSelect = { scope.launch { repo.setElevationMode("off") } }
                    )
                    ElevationOption(
                        selected = settings.elevationMode == "shizuku",
                        title = stringResource(R.string.settings_elevation_shizuku),
                        subtitle = shizukuStatusLabel(shizukuStatus),
                        onSelect = { scope.launch { repo.setElevationMode("shizuku") } }
                    )
                    ElevationOption(
                        selected = settings.elevationMode == "root",
                        title = stringResource(R.string.settings_elevation_root),
                        subtitle = rootStatusLabel(rootStatus),
                        onSelect = { scope.launch { repo.setElevationMode("root") } }
                    )
                    Text(
                        stringResource(R.string.settings_elevation_dialog_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showElevationDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (settings.elevationMode == "shizuku" && shizukuStatus == ShizukuStatus.PermissionRequired) {
                        TextButton(
                            enabled = activity != null,
                            onClick = { activity?.let { ShizukuEngine.requestPermission(it) } }
                        ) { Text(stringResource(R.string.settings_elevation_request)) }
                    }
                    if (settings.elevationMode == "root") {
                        TextButton(onClick = { scope.launch { RootEngine.refresh() } }) { Text(stringResource(R.string.settings_elevation_recheck)) }
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
        stringResource(R.string.settings_link_source) to "https://github.com/sysrv64/KArchiver",
        stringResource(R.string.settings_link_releases) to "https://github.com/sysrv64/KArchiver/releases",
        stringResource(R.string.settings_link_readme) to "https://github.com/sysrv64/KArchiver/blob/main/README.md",
        stringResource(R.string.settings_link_qa) to "https://github.com/sysrv64/KArchiver/blob/main/QA.md",
        stringResource(R.string.settings_link_issue) to "https://github.com/sysrv64/KArchiver/issues"
    )
    SettingsScaffold(stringResource(R.string.settings_category_about), onBack, barLifted, onToggleBar) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Tune, null) },
                supportingContent = { Text(stringResource(R.string.settings_about_android)) }
            ) {
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME))
            }
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Icon(Icons.Filled.Description, null) },
                supportingContent = { Text(stringResource(R.string.settings_about_license)) }
            ) {
                Text(stringResource(R.string.settings_license))
            }
        }
        SectionHeader(stringResource(R.string.settings_section_diagnostics))
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
                        logResult = if (file != null) context.getString(R.string.settings_log_saved, file.absolutePath)
                        else context.getString(R.string.settings_log_save_failed)
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
                Text(if (savingLogs) stringResource(R.string.settings_log_collecting) else stringResource(R.string.settings_log_save))
            }
            Text(
                stringResource(R.string.settings_log_description),
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
        SectionHeader(stringResource(R.string.settings_section_links))
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

@Composable
private fun sortLabel(sort: SortBy): String = when (sort) {
    SortBy.NAME -> stringResource(R.string.word_name)
    SortBy.DATE -> stringResource(R.string.word_date)
    SortBy.SIZE -> stringResource(R.string.word_size)
    SortBy.TYPE -> stringResource(R.string.word_type)
}

@Composable
private fun rootStatusLabel(status: RootStatus): String = when (status) {
    RootStatus.Available -> stringResource(R.string.settings_root_available)
    RootStatus.Denied -> stringResource(R.string.settings_root_denied)
    RootStatus.Unavailable -> stringResource(R.string.settings_root_none)
    RootStatus.Unknown -> stringResource(R.string.settings_status_checking)
}

@Composable
private fun shizukuStatusLabel(status: ShizukuStatus): String = when (status) {
    ShizukuStatus.Ready -> stringResource(R.string.settings_status_ready)
    ShizukuStatus.PermissionRequired -> stringResource(R.string.settings_status_permission_required)
    ShizukuStatus.NoBinder -> stringResource(R.string.settings_shizuku_not_running)
    ShizukuStatus.Unavailable -> stringResource(R.string.settings_status_unavailable)
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
                Text(stringResource(R.string.settings_storage_direct_note))
            }
        ) {
            Text(stringResource(R.string.settings_storage_native_access))
        }
        SettingSwitch(
            index = 1,
            count = count,
            title = stringResource(R.string.settings_storage_auto_saf),
            subtitle = stringResource(R.string.settings_storage_auto_saf_subtitle),
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
                supportingContent = { Text(stringResource(R.string.settings_granted_note)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = volumeId in forcedSaf,
                            onCheckedChange = { onSetForceSaf(volumeId, it) }
                        )
                        TextButton(onClick = { onForgetGrant(volumeId) }) { Text(stringResource(R.string.settings_forget)) }
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
                supportingContent = { Text(stringResource(R.string.settings_storage_ungranted_note)) },
                trailingContent = {
                    TextButton(
                        onClick = {
                            grantTargetId = volume.id
                            treePicker.launch(null)
                        }
                    ) { Text(stringResource(R.string.settings_grant)) }
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
