package cn.verlu.sync.domain.repository

import cn.verlu.sync.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun observeAll(): Flow<List<WeatherSnapshot>>


    suspend fun startSync()

    /** 拉取全员快照到本地数据库。 */
    suspend fun refreshFromRemote()

    /** 获取指定用户的最后一次快照（含位置）。 */
    suspend fun getByUserId(userId: String): WeatherSnapshot?

    /** 使用指定位置拉取和风最新实况并上传。 */
    suspend fun refreshWithLocation(lat: Double, lon: Double, cityLabel: String)

    /** 使用当前物理定位拉取并上传。 */
    suspend fun refreshWithCurrentLocation()

    /** 获取当前登录用户的 ID。 */
    fun getCurrentUserId(): String?
}
