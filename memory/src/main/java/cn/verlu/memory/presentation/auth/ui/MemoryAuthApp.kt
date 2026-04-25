package cn.verlu.memory.presentation.auth.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.memory.presentation.auth.vm.AuthEventManager
import cn.verlu.memory.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.memory.presentation.navigation.LocalSnackbarHostState

private enum class AuthStep {
    Main,
    Email,
    Password,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryAuthApp(
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var step by remember { mutableStateOf(AuthStep.Main) }
    var topBarActions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    val authSessionVm: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionVm.state.collectAsStateWithLifecycle()
    val showUpdatePasswordDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (step != AuthStep.Main) {
                    CenterAlignedTopAppBar(
                        title = { Text(text = if (step == AuthStep.Email) "邮箱" else "密码") },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    step = if (step == AuthStep.Password) AuthStep.Email else AuthStep.Main
                                },
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            topBarActions?.invoke(this)
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { padding ->
            when {
                authState.isAuthenticated -> AuthenticatedHome(
                    onSignOut = { authSessionVm.signOut() },
                    modifier = Modifier.fillMaxSize(),
                )
                step == AuthStep.Main -> {
                    topBarActions = null
                    AuthRoute(
                        modifier = Modifier.padding(padding),
                        onOpenEmailLogin = { step = AuthStep.Email },
                        onOpenEmailRegister = { step = AuthStep.Email },
                    )
                }
                step == AuthStep.Email -> AuthEmailRoute(
                    modifier = Modifier.padding(padding),
                    onNext = { step = AuthStep.Password },
                    setTopBarActions = { topBarActions = it },
                )
                else -> AuthPasswordRoute(
                    modifier = Modifier.padding(padding),
                    onDone = { step = AuthStep.Main },
                    setTopBarActions = { topBarActions = it },
                )
            }

            if (showUpdatePasswordDialog) {
                UpdatePasswordDialog(
                    onDismiss = { AuthEventManager.showPasswordResetDialog.value = false },
                )
            }
        }
    }
}
