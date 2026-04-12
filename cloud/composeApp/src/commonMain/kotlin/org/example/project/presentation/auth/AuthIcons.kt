package cn.verlu.cloud.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun githubIconPainter(): Painter?

@Composable
expect fun googleIconPainter(): Painter?
