package cn.verlu.lulu.feature.cnchess.domain.chess

import cn.verlu.lulu.feature.cnchess.domain.model.MoveRecord

object ReplayNavigator {
    fun boardAt(history: List<MoveRecord>, index: Int): BoardState {
        if (history.isEmpty()) return BoardCodec.initialBoard()
        if (index <= 0) {
            return history.first().fenBefore?.let { runCatching { BoardCodec.decode(it) }.getOrNull() }
                ?: BoardCodec.initialBoard()
        }
        val clamped = index.coerceAtMost(history.size)
        val item = history[clamped - 1]
        return item.fenAfter?.let { runCatching { BoardCodec.decode(it) }.getOrNull() }
            ?: BoardCodec.initialBoard()
    }
}
