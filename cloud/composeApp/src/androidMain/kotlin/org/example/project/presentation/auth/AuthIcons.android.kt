package cn.verlu.cloud.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import cn.verlu.cloud.R

@Composable
actual fun githubIconPainter(): Painter? = painterResource(R.drawable.ic_github)

@Composable
actual fun googleIconPainter(): Painter? = painterResource(R.drawable.ic_google)
