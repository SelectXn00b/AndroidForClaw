package com.ai.assistance.operit.ui.features.memory.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.BatchDeleteConfirmDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.DocumentViewDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.LinkMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemoryInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EdgeInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditEdgeDialog
import com.ai.assistance.operit.ui.features.memory.viewmodel.AutoRootFilter
import com.ai.assistance.operit.ui.features.memory.viewmodel.GatewayFilter
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import android.provider.OpenableColumns
import android.widget.Toast
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSettingsDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSimulationDialog
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen

/** R-AGENT-003 后续：DedupCleanupDialog 入参打包，方便 when 分支解构。 */
private data class DedupDialogModel(
    val isScanning: Boolean,
    val isDeleting: Boolean,
    val groups: List<List<com.ai.assistance.operit.data.model.Memory>>,
    val deletedCount: Int,
)

@Composable
fun MemorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSettingsClick: () -> Unit,
    onCleanupClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Default.Folder, 
                contentDescription = "Toggle Folders",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.memory_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                onSearch()
            })
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.memory_search_settings_title),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        IconButton(onClick = onCleanupClick) {
            Icon(
                Icons.Default.CleaningServices,
                contentDescription = stringResource(R.string.memory_dedup_cleanup_cd),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
    preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getUserPreferencesFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var showFolderNavigator by remember { mutableStateOf(false) }

    LaunchedEffect(activeProfileId) { selectedProfileId = activeProfileId }

    val viewModel: MemoryViewModel =
        viewModel(
            key = selectedProfileId, // Recreate ViewModel when profile changes
            factory = MemoryViewModelFactory(context, selectedProfileId)
        )
    val uiState by viewModel.uiState.collectAsState()
    // R-AGENT-041-c: root 节点详情页冷归档行（非 root / 未选中时为 emptyList）
    val coldArchiveEntries by viewModel.coldArchiveEntries.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isCurrentScreen = LocalIsCurrentScreen.current

    LaunchedEffect(isCurrentScreen, selectedProfileId) {
        if (isCurrentScreen) {
            viewModel.loadMemoryGraph()
            viewModel.loadFolderPaths()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    // R-AGENT-025: 一键清理自动摘要后的 toast 反馈
    LaunchedEffect(uiState.lastCleanupResult) {
        val deleted = uiState.lastCleanupResult ?: return@LaunchedEffect
        Toast.makeText(
            context,
            "已清理 $deleted 条自动摘要（精确事实保留）",
            Toast.LENGTH_SHORT
        ).show()
        viewModel.clearCleanupResult()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { fileUri ->
                scope.launch {
                    var tempFile: File? = null
                    try {
                        // More robust file name extraction
                        val (fileName, mimeType) = withContext(Dispatchers.IO) {
                            // Execute ContentResolver operations on IO thread
                            var extractedFileName = "Untitled"
                            context.contentResolver.query(fileUri, null, null, null, null)
                                ?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val displayNameIndex =
                                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                        if (displayNameIndex != -1) {
                                            extractedFileName = cursor.getString(displayNameIndex)
                                        }
                                    }
                                }

                            val extractedMimeType = context.contentResolver.getType(fileUri)
                            Pair(extractedFileName, extractedMimeType)
                        }

                        if (mimeType != null && mimeType.startsWith("text")) {
                            val content = withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(fileUri)
                                val reader = BufferedReader(InputStreamReader(inputStream))
                                reader.readText()
                            }
                            viewModel.importDocument(fileName, fileUri.toString(), content)
                        } else {
                            // For binary files, use the tool
                            tempFile = File(context.cacheDir, fileName)
                            withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(fileUri)
                                val outputStream = FileOutputStream(tempFile)
                                inputStream?.use { input ->
                                    outputStream.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }

                            val result = withContext(Dispatchers.IO) {
                                val toolHandler = AIToolHandler.getInstance(context)
                                val tool = AITool(
                                    name = "read_file_full",
                                    parameters = listOf(ToolParameter("path", tempFile.absolutePath))
                                )
                                toolHandler.executeTool(tool)
                            }

                            if (result.success) {
                                // Assuming result.result can be cast to StringResultData
                                val resultData = result.result
                                val content = if (resultData is StringResultData) {
                                    resultData.value
                                } else {
                                    resultData.toString()
                                }
                                viewModel.importDocument(fileName, fileUri.toString(), content)
                            } else {
                                AppLogger.e("MemoryScreen", "Tool execution failed: ${result.error}")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("MemoryScreen", "Error processing file: $fileUri", e)
                    } finally {
                        tempFile?.delete()
                    }
                }
            }
        }
    )

    CustomScaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 只有在框选模式下才显示"确认删除"按钮
                if (uiState.isBoxSelectionMode) {
                    FloatingActionButton(
                        onClick = { viewModel.showBatchDeleteConfirm() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                    }
                }

                // 框选模式切换按钮
                FloatingActionButton(
                    onClick = {
                        com.ai.assistance.operit.util.AppLogger.d(
                            "MemoryScreen",
                            "Box selection button clicked. Current mode: ${uiState.isBoxSelectionMode}, toggling to ${!uiState.isBoxSelectionMode}"
                        )
                        viewModel.toggleBoxSelectionMode(!uiState.isBoxSelectionMode)
                    },
                    containerColor = if (uiState.isBoxSelectionMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Toggle Box Selection Mode")
                }

                FloatingActionButton(
                    onClick = {
                        com.ai.assistance.operit.util.AppLogger.d(
                            "MemoryScreen",
                            "Linking button clicked. Current mode: ${uiState.isLinkingMode}, toggling to ${!uiState.isLinkingMode}"
                        )
                        viewModel.toggleLinkingMode(!uiState.isLinkingMode)
                    },
                    containerColor = if (uiState.isLinkingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Toggle Linking Mode")
                }
                FloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        )
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import Document")
                }
                FloatingActionButton(
                    onClick = { viewModel.startEditing(null) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Memory")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                MemorySearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.searchMemories()
                    },
                    onSettingsClick = { viewModel.showSearchSettingsDialog(true) },
                    onCleanupClick = { viewModel.scanDuplicates() },
                    onMenuClick = { showFolderNavigator = !showFolderNavigator }
                )

                // R-AGENT-012 (2026-06-06): Gateway 来源过滤 chip 行。仅当用户跑过 gateway
                // (availableGatewayPlatforms 非空) 时显示，避免老用户看到空 chip 行。
                GatewayFilterChipRow(
                    availableGatewayPlatforms = uiState.availableGatewayPlatforms,
                    gatewayFilter = uiState.gatewayFilter,
                    onFilterChange = { viewModel.onGatewayFilterChange(it) }
                )

                // R-AGENT-041-b (2026-06-17): auto-root 三态过滤 chip 行。与 gateway chip 行平行
                // 渲染（同时存在）；仅当 graph 含至少一个 root bucket（availableAutoRootBuckets 非空）
                // 时显示，避免无自动归档的用户看到空 chip 行。
                AutoRootFilterChipRow(
                    availableAutoRootBuckets = uiState.availableAutoRootBuckets,
                    autoRootFilter = uiState.autoRootFilter,
                    onFilterChange = { viewModel.onAutoRootFilterChange(it) }
                )

                // R-AGENT-025 (2026-06-12): 一键清理所有 #auto_summary 节点
                // （治自动摘要堆积；不影响 #auto_extracted 精确事实）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { viewModel.showCleanupConfirm() },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("清理自动摘要", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    // 图谱区域（始终挂载，避免 isLoading 切换时重建 GraphVisualizer）
                    GraphVisualizer(
                        graph = uiState.graph,
                        modifier = Modifier.fillMaxSize(),
                        selectedNodeId = uiState.selectedNodeId,
                        boxSelectedNodeIds = uiState.boxSelectedNodeIds, // 传递框选节点
                        isBoxSelectionMode = uiState.isBoxSelectionMode, // 传递模式状态
                        linkingNodeIds = uiState.linkingNodeIds,
                        selectedEdgeId = uiState.selectedEdge?.id,
                        onNodeClick = { node -> viewModel.selectNode(node) },
                        onEdgeClick = { edge -> viewModel.selectEdge(edge) },
                        onNodesSelected = { nodeIds -> viewModel.addNodesToSelection(nodeIds) } // 传递回调
                    )

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            // 左侧文件夹导航 (Overlay)
            AnimatedVisibility(
                visible = showFolderNavigator,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                FolderNavigator(
                    folderPaths = uiState.folderPaths,
                    selectedFolderPath = uiState.selectedFolderPath,
                    onFolderSelected = { folderPath -> viewModel.selectFolder(folderPath) },
                    onFolderRename = { oldPath, newPath ->
                        viewModel.renameFolder(
                            oldPath,
                            newPath
                        )
                    },
                    onFolderDelete = { folderPath -> viewModel.deleteFolder(folderPath) },
                    onFolderCreate = { folderPath -> viewModel.createFolder(folderPath) },
                    onRefresh = { viewModel.refreshFolderList() },
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { selectedProfileId = it },
                    onDismissRequest = { showFolderNavigator = false }
                )
            }

            // 对话框层
            if (uiState.isSearchSettingsDialogVisible) {
                MemorySearchSettingsDialog(
                    currentConfig = uiState.searchConfig,
                    cloudConfig = uiState.cloudEmbeddingConfig,
                    dimensionUsage = uiState.embeddingDimensionUsage,
                    rebuildProgress = uiState.embeddingRebuildProgress,
                    error = uiState.error,
                    isRebuilding = uiState.isEmbeddingRebuildRunning,
                    onDismiss = { viewModel.showSearchSettingsDialog(false) },
                    onSave = { config, cloudConfig ->
                        viewModel.saveSearchSettings(config, cloudConfig)
                        viewModel.searchMemories()
                    },
                    onRebuild = { viewModel.rebuildVectorIndex() },
                    onSimulateSearch = { viewModel.openSearchSimulationDialog() }
                )
            }

            // R-AGENT-003 后续：手动重复清理对话框
            val dedupState = uiState.dedupScan
            if (dedupState !is com.ai.assistance.operit.ui.features.memory.viewmodel.DedupScanState.Idle) {
                val (isScanning, isDeleting, groups, deletedCount) = when (dedupState) {
                    is com.ai.assistance.operit.ui.features.memory.viewmodel.DedupScanState.Scanning ->
                        DedupDialogModel(true, false, emptyList(), 0)
                    is com.ai.assistance.operit.ui.features.memory.viewmodel.DedupScanState.Deleting ->
                        DedupDialogModel(false, true, emptyList(), 0)
                    is com.ai.assistance.operit.ui.features.memory.viewmodel.DedupScanState.Result ->
                        DedupDialogModel(false, false, dedupState.groups, dedupState.deletedCount)
                    else -> DedupDialogModel(false, false, emptyList(), 0)
                }
                com.ai.assistance.operit.ui.features.memory.screens.dialogs.DedupCleanupDialog(
                    isScanning = isScanning,
                    isDeleting = isDeleting,
                    groups = groups,
                    lastDeletedCount = deletedCount,
                    onDismiss = { viewModel.dismissDedupDialog() },
                    onDelete = { ids -> viewModel.deleteSelectedDuplicates(ids) },
                )
            }

            // R-AGENT-025 (2026-06-12): 一键清理自动摘要的确认弹窗
            if (uiState.showCleanupConfirm) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissCleanupConfirm() },
                    title = { Text("清理自动摘要") },
                    text = {
                        Text(
                            "将删除所有「#auto_summary」标记的对话摘要节点。\n\n" +
                                "AI 抽出的精确事实（#auto_extracted）会保留。\n\n" +
                                "此操作不可撤销，确定要清理吗？"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.dismissCleanupConfirm()
                            viewModel.cleanupAutoSummaries()
                        }) { Text("确定清理") }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissCleanupConfirm() }) {
                            Text("取消")
                        }
                    }
                )
            }

            if (uiState.isSearchSimulationDialogVisible) {
                MemorySearchSimulationDialog(
                    query = uiState.searchSimulationQuery,
                    isRunning = uiState.isSearchSimulationRunning,
                    result = uiState.searchSimulationResult,
                    error = uiState.searchSimulationError,
                    onQueryChange = { viewModel.onSearchSimulationQueryChange(it) },
                    onRun = { viewModel.runSearchSimulation() },
                    onDismiss = { viewModel.showSearchSimulationDialog(false) }
                )
            }

            if (uiState.isDocumentViewOpen && uiState.selectedMemory != null) {
                var memoryTitle by remember { mutableStateOf(uiState.selectedMemory!!.title) }
                val chunkStates = remember {
                    mutableStateMapOf<Long, String>().apply {
                        uiState.selectedDocumentChunks.forEach { put(it.id, it.content) }
                    }
                }
                // 当chunks列表变化时，同步状态
                LaunchedEffect(uiState.selectedDocumentChunks) {
                    chunkStates.clear()
                    uiState.selectedDocumentChunks.forEach { chunk ->
                        chunkStates[chunk.id] = chunk.content
                    }
                }

                DocumentViewDialog(
                    memoryTitle = memoryTitle,
                    onTitleChange = { memoryTitle = it },
                    chunks = uiState.selectedDocumentChunks,
                    chunkStates = chunkStates,
                    onChunkChange = { id, content -> chunkStates[id] = content },
                    searchQuery = uiState.documentSearchQuery,
                    onSearchQueryChange = { viewModel.onDocumentSearchQueryChange(it) },
                    onPerformSearch = { viewModel.performSearchInDocument() },
                    onDismiss = { viewModel.closeDocumentView() },
                    onSave = {
                        // 保存标题
                        if (memoryTitle != uiState.selectedMemory!!.title) {
                            viewModel.updateMemory(
                                memory = uiState.selectedMemory!!,
                                newTitle = memoryTitle,
                                newContent = uiState.selectedMemory!!.content,
                                newContentType = uiState.selectedMemory!!.contentType,
                                newSource = uiState.selectedMemory!!.source,
                                newCredibility = uiState.selectedMemory!!.credibility,
                                newImportance = uiState.selectedMemory!!.importance,
                                newFolderPath = uiState.selectedMemory!!.folderPath ?: "",
                                newTags = uiState.selectedMemory!!.tags.map { it.name }
                            )
                        }
                        // 保存有变动的chunks
                        chunkStates.forEach { (id, content) ->
                            val originalContent =
                                uiState.selectedDocumentChunks.find { it.id == id }?.content
                            if (content != originalContent) {
                                viewModel.updateChunkContent(id, content)
                            }
                        }
                        viewModel.closeDocumentView()
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    folderPath = uiState.selectedMemory?.folderPath ?: ""
                )
            } else if (uiState.selectedMemory != null) {
                MemoryInfoDialog(
                    memory = uiState.selectedMemory!!,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditing(uiState.selectedMemory)
                        viewModel.clearSelection() // 关闭当前对话框
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    onTogglePersistent = { enabled ->
                        viewModel.togglePersistentInstruction(uiState.selectedMemory!!.id, enabled)
                    },
                    coldArchiveEntries = coldArchiveEntries
                )
            }

            val selectedEdge = uiState.selectedEdge
            if (selectedEdge != null) {
                EdgeInfoDialog(
                    edge = selectedEdge,
                    graph = uiState.graph,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditingEdge(selectedEdge)
                        viewModel.clearSelection() // 同样, 点击编辑后关闭
                    },
                    onDelete = { viewModel.deleteEdge(selectedEdge.id) }
                )
            }

            if (uiState.linkingNodeIds.size == 2) {
                val sourceNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[0] }
                val targetNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[1] }
                if (sourceNode != null && targetNode != null) {
                    LinkMemoryDialog(
                        sourceNodeLabel = sourceNode.label,
                        targetNodeLabel = targetNode.label,
                        onDismiss = { viewModel.toggleLinkingMode(false) },
                        onLink = { type, weight, description ->
                            viewModel.linkMemories(
                                sourceNode.id,
                                targetNode.id,
                                type,
                                weight,
                                description
                            )
                        }
                    )
                }
            }

            if (uiState.isEditing) {
                EditMemoryDialog(
                    memory = uiState.editingMemory,
                    allFolderPaths = uiState.folderPaths,
                    onDismiss = { viewModel.cancelEditing() },
                    onSave = { memory, title, content, contentType, source, credibility, importance, folderPath, tags ->
                        if (memory == null) {
                            // 创建新记忆的逻辑（如果需要的话）
                             viewModel.createMemory(title, content, contentType)
                        } else {
                            viewModel.updateMemory(
                                memory = memory,
                                newTitle = title,
                                newContent = content,
                                newContentType = contentType,
                                newSource = source,
                                newCredibility = credibility,
                                newImportance = importance,
                                newFolderPath = folderPath,
                                newTags = tags
                            )
                        }
                    }
                )
            }

            val editingEdge = uiState.editingEdge
            if (uiState.isEditingEdge && editingEdge != null) {
                EditEdgeDialog(
                    edge = editingEdge,
                    onDismiss = { viewModel.cancelEditingEdge() },
                    onSave = { type, weight, description ->
                        viewModel.updateEdge(editingEdge, type, weight, description)
                    }
                )
            }

            if (uiState.showBatchDeleteConfirm) {
                BatchDeleteConfirmDialog(
                    selectedCount = uiState.boxSelectedNodeIds.size,
                    onDismiss = { viewModel.dismissBatchDeleteConfirm() },
                    onConfirm = { viewModel.deleteSelectedNodes() }
                )
            }
        }
    }}

/**
 * R-AGENT-012 (2026-06-06): Gateway 来源过滤 chip 行。
 * - `availableGatewayPlatforms.isEmpty()` 时整行不渲染（老用户无视觉残留）
 * - chip 顺序：「全部」「无网关」「<platform1>」「<platform2>」...
 * - 多选 platform → `GatewayFilter.OnlyGateway(platforms = set)`
 * - 选「全部」→ `GatewayFilter.All`
 * - 选「无网关」→ `GatewayFilter.ExcludeGateway`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayFilterChipRow(
    availableGatewayPlatforms: List<String>,
    gatewayFilter: GatewayFilter,
    onFilterChange: (GatewayFilter) -> Unit
) {
    if (availableGatewayPlatforms.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = gatewayFilter is GatewayFilter.All,
            onClick = { onFilterChange(GatewayFilter.All) },
            label = { Text(stringResource(R.string.memory_filter_all)) }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = gatewayFilter is GatewayFilter.ExcludeGateway,
            onClick = { onFilterChange(GatewayFilter.ExcludeGateway) },
            label = { Text(stringResource(R.string.memory_filter_no_gateway)) }
        )
        val selectedPlatforms = (gatewayFilter as? GatewayFilter.OnlyGateway)?.platforms ?: emptySet()
        availableGatewayPlatforms.forEach { platform ->
            Spacer(Modifier.width(8.dp))
            val isSelected = platform in selectedPlatforms
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSet = if (isSelected) {
                        selectedPlatforms - platform
                    } else {
                        selectedPlatforms + platform
                    }
                    if (newSet.isEmpty()) {
                        // 取消所有 platform → 回到 All（避免空集合等同 "看全部 gateway" 造成歧义）
                        onFilterChange(GatewayFilter.All)
                    } else {
                        onFilterChange(GatewayFilter.OnlyGateway(newSet))
                    }
                },
                label = {
                    Text(stringResource(R.string.memory_filter_gateway_platform_format, platform))
                }
            )
        }
    }
}

/**
 * R-AGENT-041-b (2026-06-17): MemoryScreen auto-root 三态过滤 chip 行。
 *
 * 三态：
 *  - 自动:全部（[AutoRootFilter.All]）—— 默认，不过滤
 *  - 隐藏自动（[AutoRootFilter.HideAuto]）—— 屏蔽所有 `#auto_root` 节点（只看用户原创 + gateway）
 *  - 三个 per-bucket chip（多选 → [AutoRootFilter.OnlyAuto]）：摘要 / 抽取 / 历史
 *
 * 早返回：当 [availableAutoRootBuckets] 空（graph 没有任何 `#auto_root` 节点）时整 row 不显示，
 * 避免无自动归档的用户看到空 chip 行（与 [GatewayFilterChipRow] 同款守门）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoRootFilterChipRow(
    availableAutoRootBuckets: List<String>,
    autoRootFilter: AutoRootFilter,
    onFilterChange: (AutoRootFilter) -> Unit
) {
    if (availableAutoRootBuckets.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = autoRootFilter is AutoRootFilter.All,
            onClick = { onFilterChange(AutoRootFilter.All) },
            label = { Text(stringResource(R.string.memory_filter_auto_root_all)) }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = autoRootFilter is AutoRootFilter.HideAuto,
            onClick = { onFilterChange(AutoRootFilter.HideAuto) },
            label = { Text(stringResource(R.string.memory_filter_auto_root_hide)) }
        )
        val selectedBuckets = (autoRootFilter as? AutoRootFilter.OnlyAuto)?.buckets ?: emptySet()
        // 三个 per-bucket chip：仅当 graph 实际含该 bucket 时才渲染（与 availableAutoRootBuckets
        // 同源，避免显示永远空的 bucket chip）
        val bucketLabelMap = mapOf(
            "#auto_summary_root" to R.string.memory_filter_auto_root_summary,
            "#auto_extracted_root" to R.string.memory_filter_auto_root_extracted,
            "#auto_summary_id_root" to R.string.memory_filter_auto_root_summary_id
        )
        availableAutoRootBuckets.forEach { bucketTag ->
            val labelRes = bucketLabelMap[bucketTag] ?: return@forEach
            Spacer(Modifier.width(8.dp))
            val isSelected = bucketTag in selectedBuckets
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSet = if (isSelected) {
                        selectedBuckets - bucketTag
                    } else {
                        selectedBuckets + bucketTag
                    }
                    if (newSet.isEmpty()) {
                        // 取消所有 bucket → 回到 All（避免空集合等同 "看全部 root" 造成歧义）
                        onFilterChange(AutoRootFilter.All)
                    } else {
                        onFilterChange(AutoRootFilter.OnlyAuto(newSet))
                    }
                },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}
