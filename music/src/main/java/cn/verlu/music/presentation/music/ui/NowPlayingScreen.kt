package cn.verlu.music.presentation.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min as minDp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.music.domain.lyrics.LrcParser
import cn.verlu.music.domain.model.LocalAudio
import cn.verlu.music.domain.player.PlaybackMode
import cn.verlu.music.presentation.music.vm.LocalMusicViewModel
import coil.compose.AsyncImage

@Composable
fun NowPlayingRoute(
    modifier: Modifier = Modifier,
    viewModel: LocalMusicViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val musicState by viewModel.state.collectAsStateWithLifecycle()
    val positionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.playbackDurationMs.collectAsStateWithLifecycle()
    val track = playerState.currentTrack
    val config = LocalConfiguration.current

    val gradientTop = MaterialTheme.colorScheme.primaryContainer
    val gradientBottom = MaterialTheme.colorScheme.surface

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var lyricOffsetMs by remember { mutableIntStateOf(0) }

    LaunchedEffect(track?.id) {
        if (track == null) {
            sliderPosition = 0f
            isSeeking = false
        }
    }

    LaunchedEffect(positionMs, durationMs, isSeeking, track?.id) {
        if (!isSeeking && track != null && durationMs > 0) {
            sliderPosition = positionMs.toFloat() / durationMs.toFloat()
        }
    }

    val isFavorite = track?.id?.let { musicState.favoriteIds.contains(it) } ?: false

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(gradientTop, gradientBottom)
                )
            )
    ) {
        if (track == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前未播放任何音乐", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val durForSeek = durationMs.coerceAtLeast(1L)
            val displayedPositionMs =
                if (isSeeking && durationMs > 0) (sliderPosition * durationMs).toLong() else positionMs

            val useWide =
                config.screenWidthDp >= 600 && config.screenHeightDp >= 480

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                // 平板横宽下封面不再占满整行：限制边长，避免「超级大图」
                val artSide = minDp(maxWidth, 360.dp).coerceAtLeast(176.dp)

                @Composable
                fun AlbumArtBox() {
                    Box(
                        modifier = Modifier
                            .size(artSide)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!track.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = track.coverUrl,
                                contentDescription = "歌曲封面",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size((artSide * 0.35f).coerceAtLeast(48.dp)),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                @Composable
                fun TrackMeta() {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${track.artist} · ${track.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                @Composable
                fun ProgressAndControls(modifier: Modifier = Modifier) {
                    Column(modifier = modifier.fillMaxWidth()) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = {
                                isSeeking = true
                                sliderPosition = it
                            },
                            onValueChangeFinished = {
                                viewModel.audioPlayerManager.seekTo((sliderPosition * durForSeek).toLong())
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(displayedPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.audioPlayerManager.cyclePlaybackMode() }) {
                                Icon(
                                    imageVector = when (playerState.playbackMode) {
                                        PlaybackMode.SEQUENCE -> Icons.Default.Repeat
                                        PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                        PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                                    },
                                    contentDescription = "播放模式",
                                    tint = when (playerState.playbackMode) {
                                        PlaybackMode.SEQUENCE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.audioPlayerManager.previous() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "上一首",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { viewModel.audioPlayerManager.togglePlayPause() },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "播放/暂停",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.audioPlayerManager.next() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "下一首",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            val favColor by animateColorAsState(
                                targetValue = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(300),
                                label = "fav_color_ctrl"
                            )
                            IconButton(onClick = { viewModel.toggleFavorite(track.id) }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = favColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                val controlsColumnMaxWidth = minDp(maxWidth - artSide - 56.dp, 480.dp)
                if (useWide && maxWidth >= 600.dp) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtBox()
                        Spacer(modifier = Modifier.width(28.dp))
                        Column(
                            modifier = Modifier
                                .widthIn(max = controlsColumnMaxWidth)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            TrackMeta()
                            Spacer(modifier = Modifier.height(16.dp))
                            LyricsSection(
                                track = track,
                                positionMs = displayedPositionMs + lyricOffsetMs,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LyricOffsetControls(
                                offsetMs = lyricOffsetMs,
                                onMinus = { lyricOffsetMs = (lyricOffsetMs - 500).coerceAtLeast(-5000) },
                                onPlus = { lyricOffsetMs = (lyricOffsetMs + 500).coerceAtMost(5000) },
                                onReset = { lyricOffsetMs = 0 }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            QueueSection(
                                queue = playerState.queue,
                                currentId = track.id
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ProgressAndControls()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AlbumArtBox()
                        Spacer(modifier = Modifier.height(20.dp))
                        TrackMeta()
                        LyricsSection(
                            track = track,
                            positionMs = displayedPositionMs + lyricOffsetMs,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        LyricOffsetControls(
                            offsetMs = lyricOffsetMs,
                            onMinus = { lyricOffsetMs = (lyricOffsetMs - 500).coerceAtLeast(-5000) },
                            onPlus = { lyricOffsetMs = (lyricOffsetMs + 500).coerceAtMost(5000) },
                            onReset = { lyricOffsetMs = 0 }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        QueueSection(
                            queue = playerState.queue,
                            currentId = track.id
                        )
                        ProgressAndControls()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricOffsetControls(
    offsetMs: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("歌词偏移: ${offsetMs}ms", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus) { Text("-0.5s") }
            IconButton(onClick = onReset) { Text("重置") }
            IconButton(onClick = onPlus) { Text("+0.5s") }
        }
    }
}

@Composable
private fun QueueSection(
    queue: List<LocalAudio>,
    currentId: Long
) {
    if (queue.isEmpty()) return
    val preview = queue.take(8)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("播放队列", style = MaterialTheme.typography.titleSmall)
        preview.forEach { item ->
            Text(
                text = "${if (item.id == currentId) "▶ " else ""}${item.title}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.id == currentId) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricsSection(
    track: LocalAudio,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val lines = remember(track.id, track.lrc) { LrcParser.parse(track.lrc) }
    val lyricListState = rememberLazyListState()
    val currentIndex = remember(lines, positionMs) {
        if (lines.isEmpty()) -1
        else lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(track.id) {
        lyricListState.scrollToItem(0)
    }

    LaunchedEffect(currentIndex, lines.size, track.id) {
        if (lines.isNotEmpty() && currentIndex >= 0) {
            val target = currentIndex.coerceIn(0, lines.lastIndex)
            lyricListState.animateScrollToItem(index = target, scrollOffset = 0)
        }
    }

    if (lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = lyricListState,
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(lines, key = { i, line -> line.timeMs to i }) { i, line ->
            val isCurrent = i == currentIndex
            Text(
                text = line.text,
                style = if (isCurrent) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
