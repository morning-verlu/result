package cn.verlu.cnchess

import cn.verlu.cnchess.domain.chess.BoardCodec
import cn.verlu.cnchess.domain.chess.Piece
import cn.verlu.cnchess.domain.chess.PieceType
import cn.verlu.cnchess.domain.chess.Position
import cn.verlu.cnchess.domain.chess.RuleEngine
import cn.verlu.cnchess.domain.chess.Side
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    @Test
    fun initialBoard_redCanMovePawnForward() {
        val board = BoardCodec.initialBoard()
        val moves = RuleEngine.legalMovesFrom(board, Side.Red, Position(6, 0))
        assertTrue(moves.any { it.to == Position(5, 0) })
    }

    @Test
    fun elephantCannotCrossRiver() {
        val board = BoardCodec.initialBoard()
        val moves = RuleEngine.legalMovesFrom(board, Side.Red, Position(9, 2))
        assertFalse(moves.any { it.to.row <= 4 })
    }

    @Test
    fun illegalMoveLeavingKingInCheckIsRejected() {
        val cells = MutableList<Piece?>(90) { null }
        cells[9 * 9 + 4] = Piece(Side.Red, PieceType.King)
        cells[0 * 9 + 4] = Piece(Side.Black, PieceType.Rook)
        cells[8 * 9 + 4] = Piece(Side.Red, PieceType.Rook)
        val board = cn.verlu.cnchess.domain.chess.BoardState(cells)

        // Red rook moves away would expose king to rook check.
        val result = RuleEngine.tryApplyMove(board, Side.Red, Position(8, 4), Position(8, 3))
        assertTrue(result == null)
    }

    @Test
    fun codecRoundTrip() {
        val board = BoardCodec.initialBoard()
        val encoded = BoardCodec.encode(board)
        val decoded = BoardCodec.decode(encoded)
        assertNotNull(decoded)
        assertTrue(encoded == BoardCodec.encode(decoded))
    }
}
