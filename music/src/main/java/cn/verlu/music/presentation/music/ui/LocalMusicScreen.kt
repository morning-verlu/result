package cn.verlu.music.presentation.music.ui

import android.Manifest
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.music.domain.model.LocalAudio
import cn.verlu.music.presentation.navigation.LocalOpenMusicDrawer
import cn.verlu.music.presentation.music.vm.LocalMusicViewModel

@Composable
fun LocalMusicRoute(
    modifier: Modifier = Modifier,
    viewModel: LocalMusicViewModel = hiltViewModel(),
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToHiddenTracks: () -> Unit
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) viewModel.ensureMusicLoaded()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.ensureMusicLoaded()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                LocalMusicScreen(
                    viewModel,
                    onNavigateToNowPlaying,
                    onNavigateToHiddenTracks
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "等待授权后可扫描本地音乐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            if (!hasPermission) {
                AlertDialog(
                    onDismissRequest = { },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    ),
                    title = { Text("需要读取音频文件") },
                    text = {
                        Text(
                            "用于扫描设备上的本地音乐。请点击下方按钮，在系统界面中授予权限。"
                        )
                    },
                    confirmButton = {
                        Button(onClick = { permissionLauncher.launch(permission) }) {
                            Text("去授权")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { (context as? ComponentActivity)?.finish() }
                        ) {
                            Text("退出应用")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToHiddenTracks: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF file picker for importing music files manually
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importTracks(uris)
    }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showHardDeleteConfirm by remember { mutableStateOf(false) }

    // Back press exits selection mode
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    // Launcher for Android 11+ MediaStore file deletion
    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.loadMusic() // Refresh after successful physical deletion
            selectionMode = false
            selectedIds = emptySet()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showHardDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showHardDeleteConfirm = false },
            title = { Text("彻底删除源文件？") },
            text = {
                Text("此操作将从您的设备存储中永久抹除选中的 ${selectedIds.size} 个音乐文件。删除后将无法通过本应用找回，请谨慎操作。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHardDeleteConfirm = false
                        val urisToDelete =
                            state.tracks.filter { selectedIds.contains(it.id) }.map { it.uri }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pendingIntent = MediaStore.createDeleteRequest(
                                context.contentResolver,
                                urisToDelete
                            )
                            deleteMediaLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        } else {
                            viewModel.hideTracks(selectedIds)
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHardDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = if (!selectionMode) {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        },
        topBar = @Composable {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    },
                    actions = {
                        // Select all
                        IconButton(onClick = {
                            selectedIds = if (selectedIds.size == state.tracks.size) emptySet()
                            else state.tracks.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.CheckBox, contentDescription = "全选")
                        }
                        // Soft Remove
                        IconButton(onClick = {
                            viewModel.hideTracks(selectedIds)
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = "移出列表")
                        }
                        // Hard Delete
                        IconButton(onClick = {
                            if (selectedIds.isNotEmpty()) {
                                showHardDeleteConfirm = true
                            }
                        }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "彻底删除源文件")
                        }
                    }
                )
            } else {
                var menuExpanded by remember { mutableStateOf(false) }
                LargeTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = LocalOpenMusicDrawer.current) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    title = { Text("本地音乐") },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("已隐藏的音乐") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToHiddenTracks()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导入音乐") },
                                onClick = {
                                    menuExpanded = false
                                    filePickerLauncher.launch(arrayOf("audio/*"))
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.NoteAdd,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            val track = playerState.currentTrack
            AnimatedVisibility(visible = !selectionMode && track != null) {
                if (track != null) {
                    PlayerBottomBar(
                        track = track,
                        isPlaying = playerState.isPlaying,
                        progress = state.progress,
                        onPlayPause = { viewModel.audioPlayerManager.togglePlayPause() },
                        onNext = { viewModel.audioPlayerManager.next() },
                        onPrevious = { viewModel.audioPlayerManager.previous() },
                        onBarClick = onNavigateToNowPlaying
                    )
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.loadMusic() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.error != null && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "扫描出错: ${state.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (state.tracks.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "您的设备中没有找到音乐文件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.tracks, key = { it.id }) { track ->
                        val isSelected = selectedIds.contains(track.id)
                        val isFav = state.favoriteIds.contains(track.id)
                        TrackItem(
                            track = track,
                            isPlaying = playerState.currentTrack?.id == track.id,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            isFavorite = isFav,
                            onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                            onClick = {
                                if (selectionMode) {
                                    if (isSelected) selectedIds -= track.id else selectedIds += track.id
                                } else {
                                    viewModel.playTrack(track)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = setOf(track.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: LocalAudio,
    isPlaying: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    secondaryActionContentDescription: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Favorite icon (visible only outside selection mode)
        if (!selectionMode) {
            if (onSecondaryAction != null && secondaryActionIcon != null) {
                IconButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = secondaryActionIcon,
                        contentDescription = secondaryActionContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PlayerBottomBar(
    track: LocalAudio,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBarClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBarClick),
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
