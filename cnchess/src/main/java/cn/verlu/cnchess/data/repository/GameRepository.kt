package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.domain.chess.Position
import cn.verlu.cnchess.domain.model.ChessGame
import cn.verlu.cnchess.domain.model.GameHistoryItem
import kotlinx.coroutines.flow.StateFlow

interface GameRepository {
    val gameState: StateFlow<ChessGame?>

    suspend fun bindGame(gameId: String)
    suspend fun unbindGame()
    fun unbindGameFireAndForget()
    suspend fun makeMove(from: Position, to: Position)
    /**
     * 认输结束对局。[gameId] 可显式传入：退出对局页时会先 unbind，若仍不传则仓库内 gameId 可能为空导致写入失败。
     */
    suspend fun resign(gameId: String? = null)
    suspend fun offerDraw()
    suspend fun respondDraw(accept: Boolean)
    suspend fun listRecentGames(limit: Int = 50): List<GameHistoryItem>

    /** Returns the active game id for the current user, or null if none. */
    suspend fun findActiveGame(): String?

    /**
     * 当前账号在所有 `status=active` 的对局中的对手 user id。
     * 好友列表里「对局中」应对齐为：对手 id ∈ 本集合 ∩ 好友列表（与 findActiveGame 同源查询，避免错误 OR 条件误判）。
     */
    suspend fun getActiveOpponentIds(): Set<String>

    /** 主动从服务器拉取当前绑定对局（用于回到前台 / Realtime 不稳定时对齐状态）。 */
    suspend fun refreshCurrentGame()
}
