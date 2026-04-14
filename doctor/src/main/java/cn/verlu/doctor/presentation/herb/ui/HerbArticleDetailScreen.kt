package cn.verlu.doctor.presentation.herb.ui



import android.content.Intent

import androidx.compose.foundation.background

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Favorite

import androidx.compose.material.icons.filled.FavoriteBorder

import androidx.compose.material.icons.filled.Share

import androidx.compose.material.icons.filled.Tune

import androidx.compose.material.icons.outlined.North

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.FilterChip

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.ModalBottomSheet

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Slider

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.material3.darkColorScheme

import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.unit.Density

import androidx.compose.ui.unit.dp

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import cn.verlu.doctor.presentation.herb.vm.HerbDetailViewModel

import cn.verlu.doctor.presentation.ui.Material3ExpressiveLoadingIndicator

import com.mikepenz.markdown.m3.Markdown

import kotlinx.coroutines.launch



private enum class ReaderSurface {

    FollowApp,

    Paper,

    Sepia,

    Dark,

}



private data class GalleryState(

    val urls: List<String>,

    val index: Int,

)



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun HerbArticleDetailScreen(

    path: String,

    onBack: () -> Unit,

    modifier: Modifier = Modifier,

) {

    val vm: HerbDetailViewModel = hiltViewModel()

    val state by vm.state.collectAsStateWithLifecycle()

    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    val chipScrollState = rememberScrollState()



    var gallery by remember { mutableStateOf<GalleryState?>(null) }

    var showReaderSettings by remember { mutableStateOf(false) }



    var fontScale by rememberSaveable(path) { mutableFloatStateOf(1f) }

    var surfaceName by rememberSaveable(path) { mutableStateOf(ReaderSurface.FollowApp.name) }

    val surface = remember(surfaceName) {

        runCatching { ReaderSurface.valueOf(surfaceName) }.getOrDefault(ReaderSurface.FollowApp)

    }



    LaunchedEffect(path) {

        vm.load(path)

    }



    val sheetSettings = rememberModalBottomSheetState(skipPartiallyExpanded = true)



    val article = state.article

    val markdown = remember(article?.content) {

        val raw = article?.content?.ifBlank { "_（正文为空）_" } ?: ""

        normalizeMarkdownBlockImages(raw)

    }

    val imageUrls = remember(markdown) { extractMarkdownImageUrls(markdown) }



    val imageTransformer = remember(imageUrls) {

        HerbClickableCoil3ImageTransformer { url ->

            val idx = imageUrls.indexOf(url).takeIf { it >= 0 } ?: 0

            gallery = GalleryState(imageUrls, idx)

        }

    }



    val density = LocalDensity.current

    val readerDensity = remember(fontScale, density) {

        Density(density = density.density, fontScale = density.fontScale * fontScale)

    }



    Scaffold(

        modifier = modifier,

        topBar = {

            TopAppBar(

                title = {

                    Text(

                        state.article?.title ?: "条文详情",

                        maxLines = 1,

                    )

                },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")

                    }

                },

                actions = {

                    IconButton(

                        onClick = { vm.toggleFavorite() },

                        enabled = state.article != null,

                    ) {

                        Icon(

                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,

                            contentDescription = if (isFavorite) "取消收藏" else "收藏",

                        )

                    }

                },

            )

        },

    ) { innerPadding ->

        Box(

            Modifier

                .fillMaxSize()

                .padding(innerPadding),

        ) {

            when {

                state.isLoading -> Material3ExpressiveLoadingIndicator(Modifier.align(Alignment.Center))

                state.error != null -> Text(

                    state.error!!,

                    color = MaterialTheme.colorScheme.error,

                    modifier = Modifier.padding(16.dp),

                )

                article != null -> {

                    val bg = readerBackground(surface)

                    Box(

                        Modifier

                            .fillMaxSize()

                            .background(bg),

                    ) {

                        val markdownContent: @Composable () -> Unit = {

                            CompositionLocalProvider(LocalDensity provides readerDensity) {

                                LazyColumn(

                                    state = listState,

                                    modifier = Modifier.fillMaxSize(),

                                    contentPadding = PaddingValues(

                                        start = 16.dp,

                                        end = 16.dp,

                                        top = 12.dp,

                                        bottom = 88.dp,

                                    ),

                                ) {

                                    item {

                                        Markdown(

                                            content = markdown,

                                            imageTransformer = imageTransformer,

                                            modifier = Modifier.fillMaxWidth(),

                                        )

                                    }

                                }

                            }

                        }

                        when (surface) {

                            ReaderSurface.Dark -> {

                                MaterialTheme(

                                    colorScheme = darkColorScheme(),

                                ) {

                                    markdownContent()

                                }

                            }

                            else -> markdownContent()

                        }

                    }



                    ReaderToolsBar(

                        modifier = Modifier.align(Alignment.BottomCenter),

                        onReaderSettings = { showReaderSettings = true },

                        onShare = {

                            val send = Intent(Intent.ACTION_SEND).apply {

                                type = "text/plain"

                                putExtra(Intent.EXTRA_SUBJECT, article.title)

                                putExtra(

                                    Intent.EXTRA_TEXT,

                                    buildString {

                                        append(article.title)

                                        append("\n\n")

                                        append(article.content)

                                    },

                                )

                            }

                            context.startActivity(Intent.createChooser(send, "分享条文"))

                        },

                        onScrollTop = {

                            scope.launch { listState.scrollToItem(0, scrollOffset = 0) }

                        },

                    )

                }

            }

        }

    }



    gallery?.let { g ->

        HerbArticleImageGallery(

            urls = g.urls,

            initialIndex = g.index,

            onDismiss = { gallery = null },

        )

    }



    if (showReaderSettings) {

        ModalBottomSheet(

            onDismissRequest = { showReaderSettings = false },

            sheetState = sheetSettings,

        ) {

            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {

                Text("阅读设置", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))

                Text("字号", style = MaterialTheme.typography.labelLarge)

                Slider(

                    value = fontScale,

                    onValueChange = { fontScale = it },

                    valueRange = 0.85f..1.45f,

                    steps = 11,

                )

                Text(

                    "${(fontScale * 100).toInt()}%",

                    style = MaterialTheme.typography.bodySmall,

                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                )

                Spacer(Modifier.height(16.dp))

                Text("背景", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Row(

                    horizontalArrangement = Arrangement.spacedBy(8.dp),

                    modifier = Modifier

                        .fillMaxWidth()

                        .horizontalScroll(chipScrollState),

                ) {

                    ReaderSurface.entries.forEach { s ->

                        FilterChip(

                            selected = surface == s,

                            onClick = { surfaceName = s.name },

                            label = {

                                Text(

                                    when (s) {

                                        ReaderSurface.FollowApp -> "跟随"

                                        ReaderSurface.Paper -> "纸色"

                                        ReaderSurface.Sepia -> "护眼"

                                        ReaderSurface.Dark -> "夜间"

                                    },

                                )

                            },

                        )

                    }

                }

                Spacer(Modifier.height(24.dp))

            }

        }

    }

}



@Composable

private fun readerBackground(surface: ReaderSurface): Color {

    return when (surface) {

        ReaderSurface.FollowApp -> MaterialTheme.colorScheme.background

        ReaderSurface.Paper -> Color(0xFFF7F3EB)

        ReaderSurface.Sepia -> Color(0xFFF2E8CF)

        ReaderSurface.Dark -> Color(0xFF101010)

    }

}



@Composable

private fun ReaderToolsBar(

    modifier: Modifier = Modifier,

    onReaderSettings: () -> Unit,

    onShare: () -> Unit,

    onScrollTop: () -> Unit,

) {

    Surface(

        modifier = modifier.padding(16.dp),

        shape = RoundedCornerShape(28.dp),

        tonalElevation = 3.dp,

        shadowElevation = 2.dp,

    ) {

        Row(

            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),

            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.spacedBy(4.dp),

        ) {

            IconButton(onClick = onReaderSettings) {

                Icon(Icons.Default.Tune, contentDescription = "阅读设置")

            }

            IconButton(onClick = onShare) {

                Icon(Icons.Default.Share, contentDescription = "分享")

            }

            IconButton(onClick = onScrollTop) {

                Icon(Icons.Outlined.North, contentDescription = "回到顶部")

            }

        }

    }

}


