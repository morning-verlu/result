package cn.verlu.sync.presentation.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.ui.SyncLoadingIndicator

/**
 * OAuth / 深链回跳后，Supabase 可能短暂处于 [AuthSessionState.isInitializing]，
 * 此时仍停留在登录子页面，本遮罩提示「正在完成登录」。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthSessionLoadingOverlay(
    modifier: Modifier = Modifier,
    sessionViewModel: AuthSessionViewModel = hiltViewModel(),
    /** 表单提交中（含点击 OAuth 后、浏览器尚未返回的短窗口）也显示加载。 */
    alsoWhen: Boolean = false
) {
    val auth by sessionViewModel.state.collectAsStateWithLifecycle()
    val show = (auth.isInitializing && !auth.isAuthenticated) || alsoWhen
    if (!show) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SyncLoadingIndicator(modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "正在完成登录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "正在验证账号，请稍候…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
