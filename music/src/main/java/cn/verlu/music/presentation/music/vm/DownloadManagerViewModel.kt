package cn.verlu.music.presentation.music.vm

import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.music.data.repository.DownloadManagerRepository
import cn.verlu.music.data.repository.DownloadTaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DownloadManagerState(
    val items: List<DownloadTaskItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class DownloadManagerViewModel @Inject constructor(
    private val repository: DownloadManagerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadManagerState())
    val state: StateFlow<DownloadManagerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(2000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.queryAll() }
            _state.update { it.copy(items = list, isLoading = false, message = null) }
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { repository.remove(id) }
            if (removed > 0) {
                _state.update { it.copy(message = "已删除下载记录") }
                refresh()
            } else {
                _state.update { it.copy(message = "删除失败，请重试") }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showMessage(msg: String) {
        _state.update { it.copy(message = msg) }
    }

    fun statusText(status: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "等待中"
        DownloadManager.STATUS_RUNNING -> "下载中"
        DownloadManager.STATUS_PAUSED -> "已暂停"
        DownloadManager.STATUS_SUCCESSFUL -> "已完成"
        DownloadManager.STATUS_FAILED -> "失败"
        else -> "未知"
    }
}
