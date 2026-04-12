package cn.verlu.doctor.presentation.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.doctor.presentation.auth.vm.UpdatePasswordViewModel
import cn.verlu.doctor.presentation.navigation.LocalSnackbarHostState

@Composable
fun UpdatePasswordDialog(
    modifier: Modifier = Modifier,
    viewModel: UpdatePasswordViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(state.success) {
        if (state.success) {
            snackbarHostState.showSnackbar("密码修改成功！")
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { },
        modifier = modifier,
        title = {
            Text(text = "设置新密码")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "您正在通过邮箱链接找回密码，请输入并妥善保存您的新密码。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting && state.password.length >= 6,
            ) {
                Text(if (state.isSubmitting) "提交中..." else "确认修改")
            }
        },
    )
}
