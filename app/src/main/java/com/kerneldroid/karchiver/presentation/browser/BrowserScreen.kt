@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)

package com.kerneldroid.karchiver.presentation.browser

import android.os.Environment
import android.text.format.Formatter
import android.content.pm.ResolveInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerneldroid.karchiver.R
import com.kerneldroid.karchiver.data.CompressFormat
import com.kerneldroid.karchiver.data.ConflictPolicy
import com.kerneldroid.karchiver.data.FileItem
import com.kerneldroid.karchiver.data.FormatRegistry
import com.kerneldroid.karchiver.data.FileSystemRepository
import com.kerneldroid.karchiver.data.isRarArchive
import com.kerneldroid.karchiver.data.SortBy
import com.kerneldroid.karchiver.data.nameWithoutArchiveExtension
import com.kerneldroid.karchiver.data.normalizeArchiveName
import com.kerneldroid.karchiver.data.archive.TaskKind
import com.kerneldroid.karchiver.data.archive.TaskStatus
import com.kerneldroid.karchiver.presentation.LocalQuoteCopyPath
import com.kerneldroid.karchiver.presentation.components.CreateFabMenu
import com.kerneldroid.karchiver.presentation.components.CreateKind
import com.kerneldroid.karchiver.presentation.components.CreateNameDialog
import com.kerneldroid.karchiver.presentation.components.FileSearchField
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold
import com.kerneldroid.karchiver.presentation.components.ScrollTopButton
import com.kerneldroid.karchiver.presentation.components.detectBarHold
import com.kerneldroid.karchiver.data.storage.AppVolume
import com.kerneldroid.karchiver.data.storage.VolumeKind
import com.kerneldroid.karchiver.presentation.storage.deepestVolumeFor
import com.kerneldroid.karchiver.presentation.storage.isWithin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class ExtractDest { HERE, NEW_FOLDER, CUSTOM }

private const val SCROLL_TOP_JUMP_THRESHOLD = 12
private val FabMenuEdgeInset = 16.dp
private const val TRIPLE_TAP_WINDOW_MILLIS = 450L

@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    confirmDelete: Boolean = true,
    rarEnabled: Boolean = false,
    autoRefresh: Boolean = false,
    equalShapes: Boolean = false,
    externalFile: File? = null,
    onExternalHandled: () -> Unit = {},
    barLifted: Boolean,
    onToggleBar: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val quoteCopyPath = LocalQuoteCopyPath.current
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val activeTaskCount by vm.activeTaskCount.collectAsStateWithLifecycle()
    val progressTaskId by vm.progressTaskId.collectAsStateWithLifecycle()
    val activeOp = tasks.firstOrNull { it.id == progressTaskId }
    val dialogVisible = progressTaskId != null
    val runningTask = tasks.firstOrNull { it.status == TaskStatus.RUNNING }
    val conflict by vm.conflict.collectAsStateWithLifecycle()
    val verifyActive by vm.verifyActive.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val verify by vm.verify.collectAsStateWithLifecycle()
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val grantRequest by vm.grantRequest.collectAsStateWithLifecycle()
    val defaultCompressFormat by vm.defaultCompressFormat.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showVolumePicker by rememberSaveable { mutableStateOf(false) }
    var grantTarget by remember { mutableStateOf<AppVolume?>(null) }
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val target = grantTarget
        grantTarget = null
        if (uri != null && target != null) {
            scope.launch {
                vm.onTreeGranted(uri, target.id)
                snackbar.showSnackbar(context.getString(R.string.browser_access_granted, target.label))
            }
        }
    }

    LaunchedEffect(grantRequest) {
        val requested = grantRequest ?: return@LaunchedEffect
        val res = snackbar.showSnackbar(
            context.getString(R.string.browser_direct_access_failed, requested.label),
            actionLabel = context.getString(R.string.browser_grant_access)
        )
        if (res == SnackbarResult.ActionPerformed) {
            grantTarget = requested
            treePicker.launch(null)
        }
        vm.dismissGrantRequest()
    }

    LifecycleResumeEffect(autoRefresh, state.currentDir) {
        if (autoRefresh) vm.startWatching()
        onPauseOrDispose { vm.stopWatching() }
    }

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var propsFile by remember { mutableStateOf<File?>(null) }
    var openWithFile by remember { mutableStateOf<File?>(null) }
    var openWithApps by remember { mutableStateOf<List<ResolveInfo>>(emptyList()) }
    var pendingExtract by remember { mutableStateOf<File?>(null) }
    var extractDest by remember { mutableStateOf(ExtractDest.NEW_FOLDER) }
    var extractCustomDir by remember { mutableStateOf<File?>(null) }
    var extractDeleteAfter by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var fabMenuHeight by remember { mutableIntStateOf(0) }
    var fabMenuCollapsedHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val showScrollTop by remember {
        derivedStateOf {
            if (state.viewMode == ViewMode.LIST) listState.firstVisibleItemIndex > 3
            else gridState.firstVisibleItemIndex > 5
        }
    }

    val selectedItems = remember(state.items, state.selected) {
        state.items.filter { state.selected.contains(it.file.absolutePath) }
    }
    val singleArchive = selectedItems.singleOrNull()?.takeIf { FormatRegistry.isArchive(it.extension) }

    fun notifyRarDisabled() {
        scope.launch {
            val res = snackbar.showSnackbar(context.getString(R.string.browser_rar_disabled), actionLabel = context.getString(R.string.action_settings))
            if (res == SnackbarResult.ActionPerformed) onOpenSettings()
        }
    }

    fun openItem(item: FileItem) {
        when {
            state.isSelectionMode -> vm.toggleSelect(item.file.absolutePath)
            item.isDirectory -> vm.navigateTo(item.file)
            FormatRegistry.isArchive(item.extension) -> {
                if (isRarArchive(item.file) && !rarEnabled) notifyRarDisabled()
                else {
                    vm.recordInteraction(item.file)
                    pendingExtract = item.file
                }
            }
            else -> vm.openFile(context, item.file)
        }
    }

    var tapCount by remember { mutableStateOf(0) }
    var tapPath by remember { mutableStateOf<String?>(null) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var pendingItem by remember { mutableStateOf<FileItem?>(null) }
    var tapDir by remember { mutableStateOf<String?>(null) }

    fun cancelPendingTap() {
        tapJob?.cancel()
        tapJob = null
        pendingItem = null
        tapCount = 0
        tapPath = null
        tapDir = null
    }

    val handleItemClick: (FileItem) -> Unit = { item ->
        if (state.isSelectionMode) {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            cancelPendingTap()
            vm.toggleSelect(item.file.absolutePath)
        } else {
            val path = item.file.absolutePath
            if (tapPath == path) tapCount++ else {
                cancelPendingTap()
                tapCount = 1
                tapPath = path
            }
            if (tapCount >= 3) {
                cancelPendingTap()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    val added = vm.toggleFavorite(path)
                    snackbar.showSnackbar(
                        context.getString(
                            if (added) R.string.browser_added_favorites else R.string.browser_removed_favorites
                        )
                    )
                }
            } else {
                pendingItem = item
                tapDir = state.currentDir.absolutePath
                tapJob?.cancel()
                tapJob = scope.launch {
                    delay(TRIPLE_TAP_WINDOW_MILLIS)
                    val pending = pendingItem
                    val dir = tapDir
                    cancelPendingTap()
                    if (pending != null && dir == vm.state.value.currentDir.absolutePath) {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        openItem(pending)
                    }
                }
            }
        }
    }

    val handleItemLongClick: (FileItem) -> Unit = { item ->
        cancelPendingTap()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.toggleSelect(item.file.absolutePath)
    }

    BackHandler(enabled = searchActive || state.isSelectionMode || vm.canGoUp()) {
        when {
            searchActive -> {
                searchActive = false
                vm.setQuery("")
            }
            state.isSelectionMode -> vm.clearSelection()
            else -> {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                vm.navigateUp()
            }
        }
    }

    RoundedTopScaffold(
        barLifted = barLifted,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!searchActive && !state.isSelectionMode && vm.clipboard == null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!fabMenuExpanded && fabMenuHeight <= fabMenuCollapsedHeight) {
                        ScrollTopButton(
                            visible = showScrollTop,
                            modifier = Modifier.padding(end = FabMenuEdgeInset),
                            onClick = {
                                scope.launch {
                                    if (state.viewMode == ViewMode.LIST) {
                                        if (listState.firstVisibleItemIndex > SCROLL_TOP_JUMP_THRESHOLD) {
                                            listState.scrollToItem(SCROLL_TOP_JUMP_THRESHOLD)
                                        }
                                        listState.animateScrollToItem(0)
                                    } else {
                                        if (gridState.firstVisibleItemIndex > SCROLL_TOP_JUMP_THRESHOLD) {
                                            gridState.scrollToItem(SCROLL_TOP_JUMP_THRESHOLD)
                                        }
                                        gridState.animateScrollToItem(0)
                                    }
                                }
                            }
                        )
                    }
                    CreateFabMenu(
                        expanded = fabMenuExpanded,
                        onExpandedChange = { fabMenuExpanded = it },
                        onCreateFolder = { createKind = CreateKind.FOLDER },
                        onCreateFile = { createKind = CreateKind.FILE },
                        modifier = Modifier.onSizeChanged { size ->
                            fabMenuHeight = size.height
                            if (!fabMenuExpanded && size.height < fabMenuCollapsedHeight) {
                                fabMenuCollapsedHeight = size.height
                            }
                        }
                    )
                }
            }
        },
        topBar = {
            Box(
                Modifier.detectBarHold {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleBar()
                }
            ) {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    count = state.selected.size,
                    allSelected = state.items.isNotEmpty() && state.items.all { it.file.absolutePath in state.selected },
                    onClose = vm::clearSelection,
                    onSelectAll = vm::selectAll
                )
            } else {
                val scheme = MaterialTheme.motionScheme
                Box {
                    AnimatedVisibility(
                        visible = !searchActive,
                        enter = fadeIn(scheme.defaultEffectsSpec()) +
                            slideInVertically(scheme.fastSpatialSpec()) { it / 4 },
                        exit = fadeOut(scheme.defaultEffectsSpec()) +
                            slideOutVertically(scheme.fastSpatialSpec()) { -it / 4 }
                    ) {
                Column {
                    BrowserTopBar(
                        current = state.currentDir,
                        itemCount = state.items.size,
                        canGoUp = vm.showsUpArrow(),
                        onNavigateUp = { vm.navigateUp() },
                        onOpenDrawer = onOpenDrawer,
                        onOpenVolumes = { showVolumePicker = true },
                        onToggleSearch = { searchActive = true },
                        onOpenSort = { showSortSheet = true },
                        showProgress = activeTaskCount > 0 && !dialogVisible,
                        progressFraction = runningTask?.fraction ?: 0f,
                        progressDeterminate = (runningTask?.total ?: 0L) > 0L,
                        onShowProgress = vm::showProgressDialog
                    )
                    Breadcrumbs(
                        current = state.currentDir,
                        volumes = volumes,
                        systemBrowsing = vm.canBrowseSystem(),
                        onNavigate = vm::navigateTo,
                        onOpenVolumes = { showVolumePicker = true }
                    )
                    }
                    }
                    AnimatedVisibility(
                        visible = searchActive,
                        enter = fadeIn(scheme.defaultEffectsSpec()) +
                            slideInVertically(scheme.fastSpatialSpec()) { -it / 4 },
                        exit = fadeOut(scheme.defaultEffectsSpec()) +
                            slideOutVertically(scheme.fastSpatialSpec()) { it / 4 }
                    ) {
                        FileSearchField(
                            query = state.query,
                            onQueryChange = vm::setQuery,
                            onClose = { searchActive = false; vm.setQuery("") }
                        )
                    }
                }
            }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.searchDeep && state.query.isNotBlank()) {
                Text(
                    text = if (state.searchCapped) {
                        pluralStringResource(R.plurals.browser_scanned_files_limit, state.searchScanned, state.searchScanned)
                    } else {
                        pluralStringResource(R.plurals.browser_scanned_files, state.searchScanned, state.searchScanned)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.refresh()
                    },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {}
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val upAction: (() -> Unit)? = if (vm.canGoUp()) {
                            { vm.navigateUp() }
                        } else {
                            null
                        }
                        when {
                            state.isLoading && state.items.isEmpty() -> CenterLoading()
                            state.items.isEmpty() && upAction == null -> EmptyState(query = state.query)
                            state.viewMode == ViewMode.LIST -> FileList(
                                state = state,
                                listState = listState,
                                onItemClick = handleItemClick,
                                onItemLongClick = handleItemLongClick,
                                onNavigateUp = upAction,
                                onSelectionChange = vm::setSelection
                            )
                            else -> FileGrid(
                                state = state,
                                gridState = gridState,
                                onItemClick = handleItemClick,
                                onItemLongClick = handleItemLongClick,
                                onNavigateUp = upAction,
                                equalShapes = equalShapes,
                                onSelectionChange = vm::setSelection
                            )
                        }
                    }
                }
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
                SelectionBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    visible = state.isSelectionMode,
                    canExtract = singleArchive != null,
                    onCopy = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        vm.copySelection()
                    },
                    onCut = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        vm.cutSelection()
                    },
                    onDelete = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (confirmDelete) showDeleteConfirm = true
                        else vm.deleteSelection { r ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (r.isSuccess) {
                                        context.getString(
                                            if (vm.state.value.trashEnabled) R.string.browser_moved_to_trash else R.string.browser_deleted
                                        )
                                    } else {
                                        r.exceptionOrNull()?.message ?: context.getString(R.string.browser_delete_failed)
                                    }
                                )
                            }
                        }
                    },
                    onCompress = { showCompressDialog = true },
                    onExtract = {
                        val target = singleArchive?.file
                        if (target != null && isRarArchive(target) && !rarEnabled) notifyRarDisabled()
                        else pendingExtract = target
                    },
                    onOpenOverflow = { showOverflow = true },
                    overflowContent = {
                        val files = selectedItems.map { it.file }
                        val single = files.singleOrNull()
                        FileOverflowMenu(
                            expanded = showOverflow,
                            onDismiss = { showOverflow = false },
                            single = single != null,
                            onProperties = { if (single != null) propsFile = single },
                            onShare = {
                                shareFiles(context, files).onFailure {
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.browser_cannot_share)) }
                                }
                            },
                            onOpenWith = {
                                if (single != null) {
                                    scope.launch {
                                        val mime = if (single.isDirectory) "*/*"
                                        else FormatRegistry.forExtension(single.extension).mime
                                        openWithApps = queryOpenWith(context, single, mime)
                                        openWithFile = single
                                    }
                                }
                            },
                            onCopyPath = {
                                copyPaths(context, files, quoteCopyPath)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(
                                            if (files.size == 1) R.string.browser_path_copied else R.string.browser_paths_copied
                                        )
                                    )
                                }
                            }
                        )
                    }
                )
                ClipboardFloatingBar(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 16.dp),
                    visible = !state.isSelectionMode && vm.clipboard != null,
                    count = vm.clipboard?.first?.size ?: 0,
                    onPaste = {
                        vm.paste { r ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    context.getString(if (r.isSuccess) R.string.browser_pasted else R.string.browser_paste_failed)
                                )
                            }
                        }
                    },
                    onCancel = vm::cancelClipboard
                )
            }
        }
    }

    if (showSortSheet) {
        SortSheet(state = state, vm = vm, onDismiss = { showSortSheet = false })
    }

    if (showVolumePicker) {
        VolumePickerDialog(
            current = state.currentDir,
            volumes = volumes,
            onPick = { vm.switchVolume(it); showVolumePicker = false },
            onDismiss = { showVolumePicker = false }
        )
    }

    propsFile?.let { file ->
        val target = remember(file, state.elevationMode) {
            FilePropertiesTarget(
                file = file,
                repo = FileSystemRepository(),
                elevated = state.elevationMode != "off",
                onRenameRequest = { newName, onDone ->
                    vm.renameFile(file, newName) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                if (r.isSuccess) context.getString(R.string.browser_renamed_to, r.getOrNull()?.name ?: newName)
                                else context.getString(
                                    vm.archiveOpMessage(r.exceptionOrNull(), R.string.browser_renamed, R.string.browser_could_not_rename)
                                )
                            )
                        }
                        if (r.isSuccess) propsFile = null
                        onDone(r.map { })
                    }
                },
                onSetModifiedRequest = { millis, onDone ->
                    vm.setFileModified(file, millis) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    if (r.isSuccess) R.string.browser_date_updated else R.string.browser_could_not_change_date
                                )
                            )
                        }
                        onDone(r)
                    }
                },
                onChmodRequest = { mode, onDone ->
                    vm.chmodFile(file, mode) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    if (r.isSuccess) R.string.browser_permissions_updated else R.string.browser_could_not_set_permissions
                                )
                            )
                        }
                        onDone(r)
                    }
                }
            )
        }
        PropertiesSheet(target = target, onDismiss = { propsFile = null })
    }

    val openWithTarget = openWithFile
    if (openWithTarget != null) {
        OpenWithDialog(
            fileName = openWithTarget.name,
            apps = openWithApps,
            packageManager = context.packageManager,
            onDismiss = { openWithFile = null },
            onPick = { app ->
                val mime = if (openWithTarget.isDirectory) "*/*"
                else FormatRegistry.forExtension(openWithTarget.extension).mime
                launchOpenWith(context, openWithTarget, mime, app).onFailure {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.browser_cannot_open_with)) }
                }
                openWithFile = null
            }
        )
    }

    if (conflict != null) {
        val request = conflict!!
        val itemCount = request.names.size
        AlertDialog(
            onDismissRequest = { vm.dismissConflict() },
            icon = { Icon(Icons.Filled.ContentCopy, null) },
            title = {
                Text(pluralStringResource(R.plurals.browser_conflict_items_exist, itemCount, itemCount))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.browser_destination_contains),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    request.names.take(5).forEach { name ->
                        Text(
                            text = stringResource(R.string.browser_bullet_item, name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (itemCount > 5) {
                        Text(
                            text = pluralStringResource(R.plurals.browser_and_more, itemCount - 5, itemCount - 5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.resolveConflict(ConflictPolicy.SKIP) }) {
                        Text(stringResource(R.string.word_skip))
                    }
                    TextButton(onClick = { vm.resolveConflict(ConflictPolicy.KEEP_BOTH) }) {
                        Text(stringResource(R.string.word_keep_both))
                    }
                    TextButton(onClick = { vm.resolveConflict(ConflictPolicy.REPLACE) }) {
                        Text(stringResource(R.string.word_replace))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissConflict() }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDeleteConfirm) {
        val count = state.selected.size
        val trash = state.trashEnabled
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    if (trash) {
                        pluralStringResource(R.plurals.browser_move_to_trash_title, count, count)
                    } else {
                        pluralStringResource(R.plurals.browser_delete_title, count, count)
                    }
                )
            },
            text = {
                Text(
                    stringResource(
                        if (trash) R.string.browser_trash_restore_hint else R.string.browser_delete_irreversible
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteSelection { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                if (r.isSuccess) {
                                    context.getString(if (trash) R.string.browser_moved_to_trash else R.string.browser_deleted)
                                } else {
                                    r.exceptionOrNull()?.message ?: context.getString(R.string.browser_delete_failed)
                                }
                            )
                        }
                    }
                }) { Text(stringResource(if (trash) R.string.action_move else R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showCompressDialog) {
        val defaultArchiveName = stringResource(R.string.browser_default_archive_name)
        val defaultArchiveBase = stringResource(R.string.browser_default_archive_base)
        var name by rememberSaveable { mutableStateOf(defaultArchiveName) }
        var password by remember { mutableStateOf("") }
        var format by remember { mutableStateOf(defaultCompressFormat) }
        val formatScroll = rememberScrollState()
        val finalName = normalizeArchiveName(name.ifBlank { defaultArchiveBase }, format)
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            icon = { Icon(Icons.Filled.Archive, null) },
            title = { Text(stringResource(R.string.browser_compress_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(formatScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompressFormat.entries.forEach { entry ->
                            FilterChip(
                                selected = format == entry,
                                onClick = {
                                    format = entry
                                    name = normalizeArchiveName(name.ifBlank { defaultArchiveBase }, entry)
                                },
                                label = { Text(entry.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.browser_archive_name)) },
                        singleLine = true
                    )
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.browser_password_optional)
                    )
                    if (format.supportsPassword) {
                        Text(
                            stringResource(R.string.browser_password_aes_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            stringResource(R.string.browser_password_unsupported, format.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!format.supportsPassword && password.isNotEmpty()) {
                        Text(
                            stringResource(R.string.browser_password_will_fail, format.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.browser_will_be_created, "${state.currentDir.absolutePath}/$finalName"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val chosenName = finalName
                    val chosenPassword = password
                    val chosenFormat = format
                    showCompressDialog = false
                    vm.startCompress(context, chosenName, chosenFormat, chosenPassword) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    vm.archiveOpMessage(r.exceptionOrNull(), R.string.browser_archive_created, R.string.browser_compression_failed)
                                )
                            )
                        }
                    }
                }) { Text(stringResource(R.string.action_compress)) }
            },
            dismissButton = { TextButton(onClick = { showCompressDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    pendingExtract?.let { file ->
        var password by remember(file.absolutePath) { mutableStateOf("") }
        val newFolder = remember(file.absolutePath) {
            File(file.parentFile, nameWithoutArchiveExtension(file.name))
        }
        AlertDialog(
            onDismissRequest = { pendingExtract = null },
            icon = { Icon(Icons.Filled.FolderOpen, null) },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.browser_destination), style = MaterialTheme.typography.labelLarge)
                    ExtractDestRow(
                        selected = extractDest == ExtractDest.HERE,
                        title = stringResource(R.string.browser_extract_here),
                        subtitle = file.parentFile?.absolutePath ?: "",
                        onSelect = { extractDest = ExtractDest.HERE }
                    )
                    ExtractDestRow(
                        selected = extractDest == ExtractDest.NEW_FOLDER,
                        title = stringResource(R.string.browser_new_folder),
                        subtitle = newFolder.absolutePath,
                        onSelect = { extractDest = ExtractDest.NEW_FOLDER }
                    )
                    ExtractDestRow(
                        selected = extractDest == ExtractDest.CUSTOM,
                        title = stringResource(R.string.browser_choose_folder),
                        subtitle = extractCustomDir?.absolutePath ?: stringResource(R.string.browser_tap_to_pick_folder),
                        onSelect = {
                            extractDest = ExtractDest.CUSTOM
                            showFolderPicker = true
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { extractDeleteAfter = !extractDeleteAfter }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = extractDeleteAfter, onCheckedChange = { extractDeleteAfter = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.browser_delete_after_extraction))
                    }
                    Spacer(Modifier.height(4.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.browser_password_if_required)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = extractDest != ExtractDest.CUSTOM || extractCustomDir != null,
                    onClick = {
                        val chosenPassword = password
                        val dest = when (extractDest) {
                            ExtractDest.HERE -> file.parentFile
                            ExtractDest.NEW_FOLDER -> newFolder
                            ExtractDest.CUSTOM -> extractCustomDir
                        } ?: return@Button
                        val deleteAfter = extractDeleteAfter
                        pendingExtract = null
                        vm.startExtractTo(context, file, dest, chosenPassword) { r ->
                            if (r.isSuccess && deleteAfter) {
                                vm.deleteFile(file) { }
                            }
                            scope.launch {
                                snackbar.showSnackbar(
                                    context.getString(
                                        vm.archiveOpMessage(r.exceptionOrNull(), R.string.browser_extracted, R.string.browser_extraction_failed)
                                    )
                                )
                            }
                        }
                    }
                ) { Text(stringResource(R.string.action_extract)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        pendingExtract = null
                        vm.openPreview(file)
                    }) { Text(stringResource(R.string.browser_preview)) }
                    TextButton(onClick = {
                        pendingExtract = null
                        vm.verifyArchive(file)
                    }) { Text(stringResource(R.string.browser_verify)) }
                    TextButton(onClick = { pendingExtract = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            startDir = extractCustomDir ?: pendingExtract?.parentFile ?: state.currentDir,
            onPick = {
                extractCustomDir = it
                extractDest = ExtractDest.CUSTOM
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    externalFile?.let { file ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onExternalHandled, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    file.parentFile?.absolutePath ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                if (FormatRegistry.isArchive(file.extension)) {
                    SheetActionRow(Icons.Filled.Visibility, stringResource(R.string.browser_preview)) {
                        onExternalHandled()
                        vm.openPreview(file)
                    }
                    SheetActionRow(Icons.Filled.FolderOpen, stringResource(R.string.action_extract)) {
                        pendingExtract = file
                        onExternalHandled()
                    }
                    SheetActionRow(Icons.Filled.Verified, stringResource(R.string.browser_verify)) {
                        onExternalHandled()
                        vm.verifyArchive(file)
                    }
                }
                SheetActionRow(Icons.Filled.OpenInNew, stringResource(R.string.action_open_with)) {
                    scope.launch {
                        val mime = FormatRegistry.forExtension(file.extension).mime
                        openWithApps = queryOpenWith(context, file, mime)
                        openWithFile = file
                    }
                    onExternalHandled()
                }
                SheetActionRow(Icons.Filled.Share, stringResource(R.string.action_share)) {
                    shareFiles(context, listOf(file))
                    onExternalHandled()
                }
                SheetActionRow(Icons.Filled.Info, stringResource(R.string.action_properties)) {
                    propsFile = file
                    onExternalHandled()
                }
                SheetActionRow(Icons.Filled.ContentCopy, stringResource(R.string.browser_copy_path)) {
                    copyPaths(context, listOf(file), quoteCopyPath)
                    onExternalHandled()
                }
            }
        }
    }

    preview.file?.let { file ->
        PreviewSheet(
            archive = file,
            preview = preview,
            viewMode = state.viewMode,
            onDismiss = vm::closePreview,
            onExitToFolder = { vm.closePreview(); vm.navigateTo(it) },
            onUnlock = { password -> vm.openPreview(file, password) }
        )
    }

    LaunchedEffect(preview.error) {
        val err = preview.error
        if (preview.file != null && err != null &&
            err != R.string.browser_password_required && err != R.string.browser_wrong_password
        ) {
            snackbar.showSnackbar(context.getString(err))
        }
    }

    val verifyFile = verify.file
    val verifyReport = verify.report
    val verifyError = verify.error
    if (verifyFile != null && !verify.isLoading && (verifyReport != null || verifyError != null)) {
        var verifyPassword by remember(verifyFile.absolutePath, verify.passwordUsed) { mutableStateOf("") }
        val needsVerifyPassword = (verifyReport?.passwordRequired == true) ||
            verifyError == R.string.browser_password_required || verifyError == R.string.browser_wrong_password
        AlertDialog(
            onDismissRequest = vm::closeVerify,
            icon = {
                Icon(
                    if (verifyReport != null && verifyReport.ok) Icons.Filled.Verified else Icons.Filled.ErrorOutline,
                    null
                )
            },
            title = { Text(verifyFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                when {
                    verifyError != null && !needsVerifyPassword -> Text(stringResource(verifyError))
                    verifyReport == null && !needsVerifyPassword -> Text(stringResource(R.string.browser_verification_failed))
                    verifyReport != null && !verifyReport.passwordRequired && verifyReport.ok ->
                        Text(pluralStringResource(R.plurals.browser_archive_ok, verifyReport.entries, verifyReport.entries))
                    verifyReport != null && !verifyReport.passwordRequired -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.browser_archive_damaged, verifyReport.failures.size, verifyReport.entries))
                            verifyReport.failures.take(5).forEach { failure ->
                                Text(
                                    stringResource(R.string.browser_failure_line, failure.name, failure.reason),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (verifyReport.failures.size > 5) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.browser_and_more_failures,
                                        verifyReport.failures.size - 5,
                                        verifyReport.failures.size - 5
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(verifyError ?: R.string.browser_password_required),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (verifyError == R.string.browser_wrong_password) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                            PasswordField(
                                value = verifyPassword,
                                onValueChange = { verifyPassword = it },
                                label = stringResource(R.string.word_password)
                            )
                            Button(
                                onClick = {
                                    val entered = verifyPassword
                                    verifyPassword = ""
                                    vm.verifyArchive(verifyFile, entered)
                                },
                                enabled = verifyPassword.isNotEmpty()
                            ) { Text(stringResource(R.string.browser_verify_with_password)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::closeVerify) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }

    if ((activeOp != null && dialogVisible) || verifyActive) {
        val op = activeOp
        if (op != null && dialogVisible) {
            val hasTotal = op.total > 0L
            val fraction = op.fraction ?: 0f
            val percent = (fraction * 100).toInt()
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        stringResource(
                            if (op.kind == TaskKind.COMPRESS) R.string.browser_compressing else R.string.browser_extracting
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(op.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (hasTotal) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                stringResource(
                                    R.string.browser_progress_of,
                                    Formatter.formatShortFileSize(context, op.done),
                                    Formatter.formatShortFileSize(context, op.total),
                                    percent
                                )
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.browser_working))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.hideProgressDialog() }) { Text(stringResource(R.string.browser_hide)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.cancelArchiveOp(context) }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.browser_working_with_archive)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.browser_verifying_archive))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.cancelArchiveOp() }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    }

    createKind?.let { kind ->
        CreateNameDialog(
            kind = kind,
            onConfirm = { name ->
                createKind = null
                if (kind == CreateKind.FOLDER) {
                    vm.createFolder(name) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    if (r.isSuccess) R.string.browser_folder_created else R.string.browser_create_folder_failed
                                )
                            )
                        }
                    }
                } else {
                    vm.createFile(name) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    if (r.isSuccess) R.string.browser_file_created else R.string.browser_create_file_failed
                                )
                            )
                        }
                    }
                }
            },
            onDismiss = { createKind = null }
        )
    }
}

@Composable
private fun VolumePickerDialog(
    current: File,
    volumes: List<AppVolume>,
    onPick: (AppVolume) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Storage, null) },
        title = { Text(stringResource(R.string.browser_storage_volumes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (volumes.isEmpty()) {
                    Text(
                        stringResource(R.string.browser_no_volumes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                volumes.forEach { volume ->
                    VolumeRow(volume = volume, current = current, onPick = { onPick(volume) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun VolumeRow(
    volume: AppVolume,
    current: File,
    onPick: () -> Unit
) {
    val selected = current.isWithin(volume.root)
    ListItem(
        supportingContent = {
            Text(volume.root.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(
                if (volume.isPrimary) Icons.Filled.Smartphone
                else if (volume.kind == VolumeKind.USB) Icons.Filled.Usb
                else Icons.Filled.SdStorage,
                null
            )
        },
        trailingContent = {
            if (selected) Icon(Icons.Filled.Check, null)
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else Color.Transparent
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onPick)
    ) {
        Text(volume.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BrowserTopBar(
    current: File,
    itemCount: Int,
    canGoUp: Boolean,
    onNavigateUp: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenVolumes: () -> Unit = {},
    onToggleSearch: () -> Unit,
    onOpenSort: () -> Unit,
    showProgress: Boolean = false,
    progressFraction: Float = 0f,
    progressDeterminate: Boolean = false,
    onShowProgress: () -> Unit = {}
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        title = {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onOpenVolumes)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = current.name.ifEmpty { "/" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(R.plurals.browser_item_count, itemCount, itemCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            when {
                canGoUp -> IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
                }
                else -> IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Menu, stringResource(R.string.browser_menu))
                }
            }
        },
        actions = {
            if (showProgress) {
                val showProgressDescription = stringResource(R.string.browser_show_progress)
                IconButton(
                    onClick = onShowProgress,
                    modifier = Modifier.semantics { contentDescription = showProgressDescription }
                ) {
                    if (progressDeterminate) {
                        CircularProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            IconButton(onClick = onToggleSearch) { Icon(Icons.Filled.Search, stringResource(R.string.action_search)) }
            IconButton(onClick = onOpenSort) { Icon(Icons.Filled.SortByAlpha, stringResource(R.string.browser_sort_and_view)) }
        }
    )
}

@Composable
internal fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.browser_selected_count, count), style = MaterialTheme.typography.titleLarge) },
        actions = {
            IconButton(onClick = onSelectAll) {
                if (allSelected) {
                    Icon(Icons.Filled.Deselect, stringResource(R.string.browser_deselect_all))
                } else {
                    Icon(Icons.Filled.SelectAll, stringResource(R.string.action_select_all))
                }
            }
        }
    )
}

@Composable
internal fun Breadcrumbs(
    current: File,
    volumes: List<AppVolume> = emptyList(),
    systemBrowsing: Boolean = false,
    onNavigate: (File) -> Unit,
    onOpenVolumes: () -> Unit = {}
) {
    val systemLabel = stringResource(R.string.browser_system)
    val internalStorageLabel = stringResource(R.string.browser_internal_storage)
    val segments = remember(current, volumes, systemBrowsing, systemLabel, internalStorageLabel) {
        ancestorsOf(current, volumes, systemBrowsing, systemLabel, internalStorageLabel)
    }
    if (segments.size <= 1) return
    val scroll = rememberScrollState()
    LaunchedEffect(current.absolutePath) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (file, label) ->
            if (index > 0) {
                Icon(
                    Icons.Filled.ChevronRight, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val isCurrent = index == segments.lastIndex
            val isRoot = index == 0
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = !isCurrent || isRoot,
                        onClick = { if (isRoot && !systemBrowsing) onOpenVolumes() else onNavigate(file) }
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

private fun LazyListLayoutInfo.itemIndexAtY(y: Float): Int? {
    val py = y.roundToInt()
    return visibleItemsInfo.lastOrNull { it.offset <= py }?.index
}

private fun LazyGridLayoutInfo.itemIndexAt(x: Float, y: Float): Int? {
    val px = x.roundToInt()
    val py = y.roundToInt()
    val row = visibleItemsInfo.lastOrNull {
        it.row != LazyGridItemInfo.UnknownRow && it.offset.y <= py
    } ?: return null
    val rowTop = row.offset.y
    return visibleItemsInfo.asSequence()
        .filter { it.offset.y == rowTop && it.offset.x <= px }
        .maxByOrNull { it.offset.x }
        ?.index
}

private fun Modifier.dragSelectList(
    listState: LazyListState,
    longPressTick: State<Int>,
    anchorIndex: State<Int>,
    baseSelection: State<Set<String>>,
    pathForIndex: State<(Int) -> String?>,
    onSelectionChange: State<(Set<String>) -> Unit>
): Modifier = pointerInput(listState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tickAtDown = longPressTick.value
        var tracking = false
        var lastLo = -1
        var lastHi = -1
        var lastEmitted: Set<String>? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (tracking) event.changes.forEach { it.consume() }
                break
            }
            if (!tracking && longPressTick.value != tickAtDown) tracking = true
            if (!tracking) continue
            event.changes.forEach { it.consume() }
            val y = change.position.y
            val edge = 56.dp.toPx()
            val scrollDelta = when {
                y < edge -> y - edge
                y > size.height - edge -> y - (size.height - edge)
                else -> 0f
            }
            if (scrollDelta != 0f) listState.dispatchRawDelta(scrollDelta * 0.5f)
            val idx = listState.layoutInfo.itemIndexAtY(y) ?: continue
            val lo = minOf(anchorIndex.value, idx)
            val hi = maxOf(anchorIndex.value, idx)
            if (lo == lastLo && hi == lastHi) continue
            lastLo = lo
            lastHi = hi
            val paths = baseSelection.value + (lo..hi).mapNotNull(pathForIndex.value)
            if (paths != lastEmitted) {
                lastEmitted = paths
                onSelectionChange.value(paths)
            }
        }
    }
}

private fun Modifier.dragSelectGrid(
    gridState: LazyGridState,
    longPressTick: State<Int>,
    anchorIndex: State<Int>,
    baseSelection: State<Set<String>>,
    pathForIndex: State<(Int) -> String?>,
    onSelectionChange: State<(Set<String>) -> Unit>
): Modifier = pointerInput(gridState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tickAtDown = longPressTick.value
        var tracking = false
        var lastLo = -1
        var lastHi = -1
        var lastEmitted: Set<String>? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (tracking) event.changes.forEach { it.consume() }
                break
            }
            if (!tracking && longPressTick.value != tickAtDown) tracking = true
            if (!tracking) continue
            event.changes.forEach { it.consume() }
            val x = change.position.x
            val y = change.position.y
            val edge = 56.dp.toPx()
            val scrollDelta = when {
                y < edge -> y - edge
                y > size.height - edge -> y - (size.height - edge)
                else -> 0f
            }
            if (scrollDelta != 0f) gridState.dispatchRawDelta(scrollDelta * 0.5f)
            val idx = gridState.layoutInfo.itemIndexAt(x, y) ?: continue
            val lo = minOf(anchorIndex.value, idx)
            val hi = maxOf(anchorIndex.value, idx)
            if (lo == lastLo && hi == lastHi) continue
            lastLo = lo
            lastHi = hi
            val paths = baseSelection.value + (lo..hi).mapNotNull(pathForIndex.value)
            if (paths != lastEmitted) {
                lastEmitted = paths
                onSelectionChange.value(paths)
            }
        }
    }
}

@Composable
internal fun FileList(
    state: BrowserUiState,
    listState: LazyListState,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    enableThumbnails: Boolean = true,
    onSelectionChange: (Set<String>) -> Unit = {}
) {
    val extra = if (onNavigateUp != null) 1 else 0
    val longPressTick = remember { mutableIntStateOf(0) }
    val anchorIndex = remember { mutableIntStateOf(0) }
    val baseSelection = remember { mutableStateOf<Set<String>>(emptySet()) }
    val latestSelected = rememberUpdatedState(state.selected)
    val pathForIndex: (Int) -> String? = { i -> state.items.getOrNull(i - extra)?.file?.absolutePath }
    val latestPathForIndex = rememberUpdatedState(pathForIndex)
    val latestOnSelectionChange = rememberUpdatedState(onSelectionChange)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragSelectList(
                listState = listState,
                longPressTick = longPressTick,
                anchorIndex = anchorIndex,
                baseSelection = baseSelection,
                pathForIndex = latestPathForIndex,
                onSelectionChange = latestOnSelectionChange
            ),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        if (onNavigateUp != null) {
            item(key = "parent-folder") {
                ParentFolderRow(
                    index = 0,
                    count = state.items.size + 1,
                    onClick = onNavigateUp,
                    modifier = Modifier.animateItem()
                )
            }
        }
        items(state.items.size, key = { state.items[it].file.absolutePath }) { index ->
            val item = state.items[index]
            FileRow(
                item = item,
                index = index + extra,
                count = state.items.size + extra,
                selected = state.selected.contains(item.file.absolutePath),
                favorite = state.favorites.contains(item.file.absolutePath),
                onClick = { onItemClick(item) },
                onLongClick = {
                    baseSelection.value = latestSelected.value
                    anchorIndex.value = index + extra
                    onItemLongClick(item)
                    longPressTick.value++
                },
                enableThumbnails = enableThumbnails,
                modifier = Modifier.animateItem()
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun FileGrid(
    state: BrowserUiState,
    gridState: LazyGridState,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    enableThumbnails: Boolean = true,
    equalShapes: Boolean = false,
    onSelectionChange: (Set<String>) -> Unit = {}
) {
    val extra = if (onNavigateUp != null) 1 else 0
    val longPressTick = remember { mutableIntStateOf(0) }
    val anchorIndex = remember { mutableIntStateOf(0) }
    val baseSelection = remember { mutableStateOf<Set<String>>(emptySet()) }
    val latestSelected = rememberUpdatedState(state.selected)
    val pathForIndex: (Int) -> String? = { i -> state.items.getOrNull(i - extra)?.file?.absolutePath }
    val latestPathForIndex = rememberUpdatedState(pathForIndex)
    val latestOnSelectionChange = rememberUpdatedState(onSelectionChange)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .dragSelectGrid(
                gridState = gridState,
                longPressTick = longPressTick,
                anchorIndex = anchorIndex,
                baseSelection = baseSelection,
                pathForIndex = latestPathForIndex,
                onSelectionChange = latestOnSelectionChange
            ),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onNavigateUp != null) {
            item(key = "parent-folder") {
                ParentFolderCard(onClick = onNavigateUp, modifier = Modifier.animateItem())
            }
        }
        itemsIndexed(state.items, key = { _, item -> item.file.absolutePath }) { index, item ->
            FileGridCard(
                item = item,
                selected = state.selected.contains(item.file.absolutePath),
                favorite = state.favorites.contains(item.file.absolutePath),
                onClick = { onItemClick(item) },
                onLongClick = {
                    baseSelection.value = latestSelected.value
                    anchorIndex.value = index + extra
                    onItemLongClick(item)
                    longPressTick.value++
                },
                enableThumbnails = enableThumbnails,
                equalShapes = equalShapes,
                modifier = Modifier.animateItem()
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ParentFolderRow(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Text(
                stringResource(R.string.browser_parent_folder),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Text(
            stringResource(R.string.browser_parent_folder_symbol),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ParentFolderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(stringResource(R.string.browser_parent_folder_symbol), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.browser_parent_folder),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
internal fun FileRow(
    item: FileItem,
    index: Int,
    count: Int,
    selected: Boolean,
    favorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableThumbnails: Boolean = true,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else if (favorite) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier,
        leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(if (item.isDirectory) CircleShape else RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FileLeadingContent(
                        item = item,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        iconModifier = Modifier,
                        enableThumbnails = enableThumbnails
                    )
                }
            },
            supportingContent = {
                Text(
                    item.snippet ?: metaText(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (favorite) {
                        Icon(
                            Icons.Filled.Star, stringResource(R.string.browser_favorite),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                when {
                    selected -> Icon(
                        Icons.Filled.CheckCircle, stringResource(R.string.browser_selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    item.isDirectory -> Icon(
                        Icons.Filled.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
}

@Composable
private fun FileGridCard(
    item: FileItem,
    selected: Boolean,
    favorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableThumbnails: Boolean = true,
    equalShapes: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(if (selected) 20.dp else 16.dp)
    Box(modifier = modifier) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else if (favorite) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (equalShapes) Modifier.height(160.dp) else Modifier)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                FileLeadingContent(
                    item = item,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconModifier = Modifier.size(26.dp),
                    enableThumbnails = enableThumbnails
                )
            }
            Text(
                item.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (equalShapes) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = if (equalShapes) {
                    Modifier.basicMarquee(initialDelayMillis = 1200, repeatDelayMillis = 2200)
                } else {
                    Modifier
                }
            )
            Text(
                item.snippet ?: (if (item.isDirectory) stringResource(R.string.word_folder) else item.extension.uppercase()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (selected) {
                Icon(Icons.Filled.CheckCircle, stringResource(R.string.browser_selected), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (favorite) {
        Icon(
            Icons.Filled.Star, stringResource(R.string.browser_favorite),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp)
        )
    }
    }
}

@Composable
internal fun SelectionBottomBar(
    modifier: Modifier = Modifier,
    visible: Boolean,
    canExtract: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onOpenOverflow: (() -> Unit)? = null,
    overflowContent: (@Composable () -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface
            ),
            expandedShadowElevation = 6.dp,
            collapsedShadowElevation = 6.dp,
            content = {
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy)) }
                IconButton(onClick = onCut) { Icon(Icons.Filled.ContentCut, stringResource(R.string.action_cut)) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onCompress) { Icon(Icons.Filled.Archive, stringResource(R.string.action_compress)) }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canExtract) {
                        FilledTonalIconButton(
                            onClick = onExtract,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Filled.FolderOpen, stringResource(R.string.action_extract))
                        }
                    }
                    if (onOpenOverflow != null) {
                        Box {
                            IconButton(onClick = onOpenOverflow) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.browser_more_actions))
                            }
                            overflowContent?.invoke()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ClipboardFloatingBar(
    visible: Boolean,
    count: Int,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onPaste,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.browser_paste_count, count))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel), Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExtractDestRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderPickerDialog(
    startDir: File,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf(startDir) }
    val dirs by produceState(initialValue = emptyList<File>(), current) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                current.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FolderOpen, null) },
        title = { Text(stringResource(R.string.browser_choose_folder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { current.parentFile?.let { current = it } },
                        enabled = current.parentFile != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
                    }
                    Text(
                        current.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                if (dirs.isEmpty()) {
                    Text(
                        stringResource(R.string.browser_no_subfolders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(dirs, key = { it.absolutePath }) { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { current = dir }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onPick(current) }) { Text(stringResource(R.string.browser_select_this_folder)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun SortSheet(state: BrowserUiState, vm: BrowserViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.browser_sort_and_view), style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SortBy.entries.forEachIndexed { index, sort ->
                    SegmentedButton(
                        selected = state.sortBy == sort,
                        onClick = { vm.setSort(sort) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = SortBy.entries.size),
                        label = { Text(sortLabel(sort), maxLines = 1) }
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.ascending,
                    onClick = { vm.setAscending(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.ArrowUpward, null) },
                    label = { Text(stringResource(R.string.browser_sort_ascending), maxLines = 1) }
                )
                SegmentedButton(
                    selected = !state.ascending,
                    onClick = { vm.setAscending(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.ArrowDownward, null) },
                    label = { Text(stringResource(R.string.browser_sort_descending), maxLines = 1) }
                )
            }
            Text(stringResource(R.string.browser_view), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.viewMode == ViewMode.LIST,
                    onClick = { vm.setViewMode(ViewMode.LIST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.ViewAgenda, null) },
                    label = { Text(stringResource(R.string.view_list)) }
                )
                SegmentedButton(
                    selected = state.viewMode == ViewMode.GRID,
                    onClick = { vm.setViewMode(ViewMode.GRID) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.GridView, null) },
                    label = { Text(stringResource(R.string.view_grid)) }
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label ?: stringResource(R.string.word_password)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (visible) stringResource(R.string.browser_hide_password) else stringResource(R.string.browser_show_password)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PreviewSheet(
    archive: File,
    preview: ArchivePreviewUiState,
    viewMode: ViewMode,
    onDismiss: () -> Unit,
    onExitToFolder: (File) -> Unit,
    onUnlock: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ArchiveExplorerRoute(
            archive = archive,
            password = preview.passwordUsed,
            viewMode = viewMode,
            onClose = onDismiss,
            modifier = Modifier.fillMaxHeight(),
            onExitToFolder = onExitToFolder
        )
    }
    val needsPassword = preview.error == R.string.browser_password_required || preview.error == R.string.browser_wrong_password ||
        (preview.listing != null && preview.listing.encrypted && preview.passwordUsed.isEmpty())
    if (needsPassword) {
        var password by remember(archive.absolutePath) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Lock, null) },
            title = { Text(archive.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            if (preview.error == R.string.browser_wrong_password) R.string.browser_wrong_password
                            else R.string.browser_password_required
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (preview.error == R.string.browser_wrong_password) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.word_password)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entered = password
                        password = ""
                        onUnlock(entered)
                    },
                    enabled = password.isNotEmpty()
                ) { Text(stringResource(R.string.browser_unlock)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun CenterLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

@Composable
private fun EmptyState(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.FolderOpen, null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(if (query.isBlank()) R.string.empty_folder else R.string.browser_nothing_found),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ancestorsOf(
    current: File,
    volumes: List<AppVolume> = emptyList(),
    systemBrowsing: Boolean = false,
    systemLabel: String,
    internalStorageLabel: String
): List<Pair<File, String>> {
    val internalRoot = Environment.getExternalStorageDirectory()
    val volumeRoot = deepestVolumeFor(current, volumes)
    val systemRoot = File("/")
    val root = if (systemBrowsing) systemRoot else (volumeRoot?.root ?: internalRoot)
    val rootLabel = if (systemBrowsing) systemLabel else (volumeRoot?.label ?: internalStorageLabel)
    val stack = ArrayDeque<File>()
    var f: File? = current
    while (f != null) {
        stack.addFirst(f)
        if (f.absolutePath == root.absolutePath) break
        if (!systemBrowsing && f.absolutePath.length < root.absolutePath.length) break
        f = f.parentFile
    }
    return stack.map { file ->
        file to when {
            file.absolutePath == root.absolutePath -> rootLabel
            volumeRoot != null && file.absolutePath == volumeRoot.root.absolutePath -> volumeRoot.label
            file.parentFile == null -> "/"
            else -> file.name
        }
    }
}

@Composable
internal fun metaText(item: FileItem): String {
    val type = if (item.isDirectory) {
        stringResource(R.string.word_folder)
    } else {
        val ext = item.extension.uppercase()
        if (ext.isEmpty()) stringResource(R.string.word_file) else ext
    }
    val size = if (item.isDirectory) "" else " | ${formatSize(item.size)}"
    val date = if (item.lastModified > 0) " | ${formatDate(item.lastModified)}" else ""
    return "$type$size$date"
}

@Composable
private fun sortLabel(sort: SortBy): String = when (sort) {
    SortBy.NAME -> stringResource(R.string.sort_name)
    SortBy.DATE -> stringResource(R.string.sort_date)
    SortBy.SIZE -> stringResource(R.string.sort_size)
    SortBy.TYPE -> stringResource(R.string.sort_type)
}

internal fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0; if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0; if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0; return String.format("%.2f GB", gb)
}

private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
}

private fun formatDate(ms: Long): String = dateFormat.get()!!.format(Date(ms))
