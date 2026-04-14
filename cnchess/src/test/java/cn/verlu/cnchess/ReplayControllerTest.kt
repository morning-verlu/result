package cn.verlu.cnchess

import cn.verlu.cnchess.domain.chess.BoardCodec
import cn.verlu.cnchess.domain.chess.ReplayNavigator
import cn.verlu.cnchess.domain.chess.Side
import cn.verlu.cnchess.domain.model.MoveRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReplayControllerTest {

    @Test
    fun replayNavigator_shouldReturnBoardByIndex() {
        val initial = BoardCodec.initialBoard()
        val fen0 = BoardCodec.encode(initial)
        val board1 = initial
            .with(cn.verlu.cnchess.domain.chess.Position(6, 0), null)
            .with(cn.verlu.cnchess.domain.chess.Position(5, 0), initial.at(cn.verlu.cnchess.domain.chess.Position(6, 0)))
        val fen1 = BoardCodec.encode(board1)

        val history = listOf(
            MoveRecord(
                moveNo = 1,
                side = Side.Red,
                fromRow = 6,
                fromCol = 0,
                toRow = 5,
                toCol = 0,
                fenBefore = fen0,
                fenAfter = fen1,
            ),
        )

        val b0 = ReplayNavigator.boardAt(history, 0)
        val b1 = ReplayNavigator.boardAt(history, 1)
        assertNotNull(b0)
        assertNotNull(b1)
        assertEquals(fen0, BoardCodec.encode(b0))
        assertEquals(fen1, BoardCodec.encode(b1))
    }
}
