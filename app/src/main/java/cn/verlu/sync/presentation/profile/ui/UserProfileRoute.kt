package cn.verlu.sync.presentation.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.profile.vm.UserProfileViewModel
import cn.verlu.sync.presentation.ui.SyncLoadingIndicator
import coil3.compose.SubcomposeAsyncImage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserProfileRoute(
    modifier: Modifier = Modifier,
    sessionViewModel: AuthSessionViewModel = hiltViewModel(),
    profileViewModel: UserProfileViewModel = hiltViewModel()
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()

    val user = sessionState.user
    // 尝试从 metadata 或者 identities 里找头像 URL
    val avatarUrl = user?.userMetadata?.get("avatar_url")?.toString()?.trim('"')
    val email = user?.email ?: "未知邮箱"
    // 可能是 full_name 或者 user_name
    val name = user?.userMetadata?.get("full_name")?.toString()?.trim('"') 
            ?: user?.userMetadata?.get("user_name")?.toString()?.trim('"') 
            ?: email.substringBefore("@")

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Surface(
                shape = CircleShape,
                shadowElevation = 8.dp,
                modifier = Modifier.size(120.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (avatarUrl.isNullOrEmpty()) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = avatarUrl,
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        loading = { SyncLoadingIndicator(modifier = Modifier.padding(32.dp)) },
                        error = {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Error Avatar",
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(8.dp))

            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(64.dp))

            Button(
                onClick = { profileViewModel.logOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                enabled = !profileState.isLoggingOut
            ) {
                if (profileState.isLoggingOut) {
                    SyncLoadingIndicator(
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(text = "退出登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            profileState.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
