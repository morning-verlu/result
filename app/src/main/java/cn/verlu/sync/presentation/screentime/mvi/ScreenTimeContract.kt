package cn.verlu.sync.presentation.screentime.mvi

import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.SyncedScreenTimeReport

object ScreenTimeContract {
    data class State(
        val period: ScreenTimePeriod = ScreenTimePeriod.Today,
        val hasUsageAccess: Boolean = false,
        val isRefreshing: Boolean = false,
        val listError: String? = null,
        val syncedReports: List<SyncedScreenTimeReport> = emptyList(),
        val myUserId: String? = null,
        val expandedRowKey: String? = null
    )

    sealed interface Intent {
        data object Load : Intent
        data class Refresh(val isSilent: Boolean = false) : Intent
        data class SelectPeriod(val period: ScreenTimePeriod) : Intent
        data class ToggleExpand(val rowKey: String) : Intent
        data class SetMyUserId(val userId: String?) : Intent
    }
}
