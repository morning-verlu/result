package cn.verlu.cnchess.data.repository

import cn.verlu.cnchess.domain.chess.Position
import cn.verlu.cnchess.domain.model.ChessGame
import cn.verlu.cnchess.domain.model.GameHistoryItem
import kotlinx.coroutines.flow.StateFlow

interface GameRepository {
    val gameState: StateFlow<ChessGame?>

    suspend fun bindGame(gameId: String)
    suspend fun unbindGame()
    suspend fun makeMove(from: Position, to: Position)
    suspend fun resign()
    suspend fun offerDraw()
    suspend fun respondDraw(accept: Boolean)
    suspend fun listRecentGames(limit: Int = 50): List<GameHistoryItem>
}
