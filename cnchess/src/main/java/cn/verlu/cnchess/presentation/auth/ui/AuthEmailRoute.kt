package cn.verlu.cnchess.presentation.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.cnchess.presentation.auth.vm.AuthFormViewModel
import cn.verlu.cnchess.presentation.auth.vm.AuthMode
import cn.verlu.cnchess.presentation.navigation.LocalSnackbarHostState
import kotlinx.coroutines.launch

@Composable
fun AuthEmailRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthFormViewModel = hiltViewModel(),
    onNext: () -> Unit,
    setTopBarActions: ((@Composable RowScope.() -> Unit)?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.email, state.isSubmitting) {
        setTopBarActions {
            TextButton(
                onClick = {
                    val email = state.email.trim()
                    if (email.isEmpty()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请输入邮箱")
                        }
                        return@TextButton
                    }
                    onNext()
                },
                enabled = !state.isSubmitting,
            ) {
                Text(text = "下一步")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.mode == AuthMode.Register) "注册" else "登录",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("邮箱") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        AuthSessionLoadingOverlay(alsoWhen = state.isSubmitting)
    }
}
