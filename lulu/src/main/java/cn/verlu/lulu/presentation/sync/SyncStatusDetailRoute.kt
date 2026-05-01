package cn.verlu.lulu.presentation.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.lulu.domain.sync.SyncStatusType

@Composable
fun SyncStatusDetailRoute(
    type: SyncStatusType,
    modifier: Modifier = Modifier,
    viewModel: TodayStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(type) {
        viewModel.refresh()
    }
    val card = when (type) {
        SyncStatusType.Weather -> state.weather
        SyncStatusType.Battery -> state.battery
        SyncStatusType.ScreenTime -> state.screenTime
        SyncStatusType.DeviceTemperature -> state.deviceTemperature
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = type.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Text(
                    text = type.title(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = card.value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = card.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = type.detailHint(card),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}

fun SyncStatusType.title(): String = when (this) {
    SyncStatusType.Weather -> "天气"
    SyncStatusType.Battery -> "电量"
    SyncStatusType.ScreenTime -> "屏幕时间"
    SyncStatusType.DeviceTemperature -> "设备温度"
}

private fun SyncStatusType.icon(): ImageVector = when (this) {
    SyncStatusType.Weather -> Icons.Default.Cloud
    SyncStatusType.Battery -> Icons.Default.BatteryFull
    SyncStatusType.ScreenTime -> Icons.Default.Schedule
    SyncStatusType.DeviceTemperature -> Icons.Default.Thermostat
}

private fun SyncStatusType.detailHint(card: SyncStatusCardUiState): String = when (this) {
    SyncStatusType.Weather ->
        if (card.value == "--") {
            "授予定位权限后，Lulu 会通过当前位置读取实时天气。"
        } else {
            "天气来自当前位置的实时数据，定位不可用时会保持明确提示。"
        }

    SyncStatusType.Battery -> "电量读取自本机电池状态。"
    SyncStatusType.ScreenTime ->
        if (card.value == "--") {
            "在系统设置授予“使用情况访问权限”后，可以显示今天的前台使用时长。"
        } else {
            "屏幕时间按今天 0 点至当前的前台使用时长估算。"
        }

    SyncStatusType.DeviceTemperature -> "设备温度读取自系统电池温度传感器，不可用时会显示状态提示。"
}
