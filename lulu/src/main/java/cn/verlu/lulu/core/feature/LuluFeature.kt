package cn.verlu.lulu.core.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class LuluFeatureId {
    Sync,
    LifeStream,
    Talk,
    Music,
    Doctor,
    CnChess,
    CloudDrive,
    LuluChat,
}

enum class FeatureChrome {
    LuluShell,
    FullscreenApp,
    Editor,
}

data class LuluFeatureSpec(
    val id: LuluFeatureId,
    val title: String,
    val icon: ImageVector,
    val status: String,
    val detail: String,
    val chrome: FeatureChrome,
    val domainPackage: String,
    val databaseName: String? = null,
    val requiredPermissions: List<String> = emptyList(),
)

object LuluFeatureRegistry {
    val entries: List<LuluFeatureSpec> = listOf(
        LuluFeatureSpec(
            id = LuluFeatureId.Sync,
            title = "Sync",
            icon = Icons.Default.BatteryFull,
            status = "电量 / 温度 / 天气 / 屏幕时长",
            detail = "Today 的完整数据面板",
            chrome = FeatureChrome.FullscreenApp,
            domainPackage = "cn.verlu.lulu.feature.sync",
            databaseName = "sync.db",
            requiredPermissions = listOf("定位", "使用情况访问"),
        ),
        LuluFeatureSpec(
            id = LuluFeatureId.Talk,
            title = "Talk",
            icon = Icons.AutoMirrored.Filled.Chat,
            status = "好友 / 消息 / 扫码",
            detail = "完整 IM 已内置",
            chrome = FeatureChrome.FullscreenApp,
            domainPackage = "cn.verlu.lulu.feature.talk",
            databaseName = "talk.db",
            requiredPermissions = listOf("相机", "麦克风"),
        ),
        LuluFeatureSpec(
            id = LuluFeatureId.Music,
            title = "音乐",
            icon = Icons.Default.MusicNote,
            status = "本地 / 在线 / 下载",
            detail = "完整播放器与后台服务",
            chrome = FeatureChrome.FullscreenApp,
            domainPackage = "cn.verlu.lulu.feature.music",
            databaseName = "music.db",
            requiredPermissions = listOf("音频媒体", "通知"),
        ),
        LuluFeatureSpec(
            id = LuluFeatureId.Doctor,
            title = "Doctor",
            icon = Icons.Default.LocalHospital,
            status = "中药资料 / 搜索 / 收藏",
            detail = "阅读器与图片预览",
            chrome = FeatureChrome.FullscreenApp,
            domainPackage = "cn.verlu.lulu.feature.doctor",
            databaseName = "herb.db",
        ),
        LuluFeatureSpec(
            id = LuluFeatureId.CnChess,
            title = "CnChess",
            icon = Icons.Default.SportsEsports,
            status = "好友对局 / 邀请 / 复盘",
            detail = "实时对局与棋谱",
            chrome = FeatureChrome.FullscreenApp,
            domainPackage = "cn.verlu.lulu.feature.cnchess",
            requiredPermissions = listOf("网络"),
        ),
        LuluFeatureSpec(
            id = LuluFeatureId.CloudDrive,
            title = "云盘",
            icon = Icons.Default.Folder,
            status = "云端文件",
            detail = "最近文件和列表",
            chrome = FeatureChrome.LuluShell,
            domainPackage = "cn.verlu.lulu.presentation.cloud",
        ),
        LuluFeatureSpec(
            id = LuluFeatureId.LuluChat,
            title = "Lulu Chat",
            icon = Icons.AutoMirrored.Filled.Chat,
            status = "记忆上下文",
            detail = "把回答保存成记忆",
            chrome = FeatureChrome.LuluShell,
            domainPackage = "cn.verlu.lulu.presentation.chat",
            databaseName = "lulu.db",
        ),
    )

    fun require(id: LuluFeatureId): LuluFeatureSpec =
        entries.first { it.id == id }
}
