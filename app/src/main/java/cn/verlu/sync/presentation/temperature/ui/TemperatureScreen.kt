package cn.verlu.sync.presentation.temperature.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.temperature.mvi.TemperatureContract
import cn.verlu.sync.presentation.temperature.vm.TemperatureViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TemperatureRoute(
    modifier: Modifier = Modifier,
    viewModel: TemperatureViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.dispatch(TemperatureContract.Intent.Start)
    }

    val authSessionViewModel: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionViewModel.state.collectAsStateWithLifecycle()
    val myUserId = authState.user?.id

    LaunchedEffect(myUserId) {
        viewModel.dispatch(TemperatureContract.Intent.SetMyUserId(myUserId))
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        TemperatureScreen(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onRefresh = { viewModel.dispatch(TemperatureContract.Intent.Refresh(isSilent = false)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemperatureScreen(
    state: TemperatureContract.State,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.userId }) { item ->
                    val highlight = item.userId == state.myUserId
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (highlight) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.displayLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    val model = item.deviceModel.trim()
                                    if (model.isNotEmpty() && !model.equals(
                                            item.displayLabel.trim(),
                                            ignoreCase = true
                                        )
                                    ) {
                                        Text(
                                            text = model,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = item.displayTemperature,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = "更新时间: ${formatTime(item.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(Date(ts))
}
