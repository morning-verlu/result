package cn.verlu.cnchess

import cn.verlu.cnchess.domain.chess.MoveAnnotation
import cn.verlu.cnchess.domain.chess.RepetitionJudge
import cn.verlu.cnchess.domain.chess.Side
import cn.verlu.cnchess.domain.model.MoveRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepetitionJudgeTest {

    @Test
    fun threefoldRepetition_shouldDraw() {
        val history = listOf(
            MoveRecord(1, Side.Red, 9, 0, 8, 0, positionHash = "h1"),
            MoveRecord(2, Side.Black, 0, 0, 1, 0, positionHash = "h2"),
            MoveRecord(3, Side.Red, 8, 0, 9, 0, positionHash = "h1"),
            MoveRecord(4, Side.Black, 1, 0, 0, 0, positionHash = "h2"),
        )
        val annotation = MoveAnnotation(
            side = Side.Red,
            moveNo = 5,
            positionHash = "h1",
            isCheck = false,
            isChase = false,
        )
        val decision = RepetitionJudge.decide(history, annotation)
        assertEquals("threefold_repetition", decision.drawReason)
    }

    @Test
    fun longCheck_shouldBeIllegal() {
        val history = listOf(
            MoveRecord(1, Side.Red, 9, 4, 8, 4, isCheck = true),
            MoveRecord(2, Side.Black, 0, 4, 1, 4, isCheck = false),
            MoveRecord(3, Side.Red, 8, 4, 9, 4, isCheck = true),
            MoveRecord(4, Side.Black, 1, 4, 0, 4, isCheck = false),
        )
        val annotation = MoveAnnotation(
            side = Side.Red,
            moveNo = 5,
            positionHash = "x",
            isCheck = true,
            isChase = false,
        )
        val decision = RepetitionJudge.decide(history, annotation)
        assertEquals("illegal_long_check", decision.illegalTag)
        assertNull(decision.drawReason)
    }

    @Test
    fun longCheck_shouldTakePrecedenceOverThreefoldDraw() {
        val history = listOf(
            MoveRecord(1, Side.Red, 9, 4, 8, 4, positionHash = "h1", isCheck = true),
            MoveRecord(2, Side.Black, 0, 4, 1, 4, positionHash = "h2"),
            MoveRecord(3, Side.Red, 8, 4, 9, 4, positionHash = "h1", isCheck = true),
            MoveRecord(4, Side.Black, 1, 4, 0, 4, positionHash = "h2"),
        )
        val annotation = MoveAnnotation(
            side = Side.Red,
            moveNo = 5,
            positionHash = "h1",
            isCheck = true,
            isChase = false,
        )
        val decision = RepetitionJudge.decide(history, annotation)
        assertEquals("illegal_long_check", decision.illegalTag)
        assertNull(decision.drawReason)
    }

    @Test
    fun longChase_shouldTakePrecedenceOverThreefoldDraw() {
        val history = listOf(
            MoveRecord(1, Side.Red, 9, 0, 8, 0, positionHash = "h1", isChase = true),
            MoveRecord(2, Side.Black, 0, 0, 1, 0, positionHash = "h2"),
            MoveRecord(3, Side.Red, 8, 0, 9, 0, positionHash = "h1", isChase = true),
            MoveRecord(4, Side.Black, 1, 0, 0, 0, positionHash = "h2"),
        )
        val annotation = MoveAnnotation(
            side = Side.Red,
            moveNo = 5,
            positionHash = "h1",
            isCheck = false,
            isChase = true,
        )
        val decision = RepetitionJudge.decide(history, annotation)
        assertEquals("illegal_long_chase", decision.illegalTag)
        assertNull(decision.drawReason)
    }

    @Test
    fun continuousChase_withoutRepeatedPosition_shouldNotBeIllegalLongChase() {
        val history = listOf(
            MoveRecord(1, Side.Red, 9, 0, 8, 0, positionHash = "h1", isChase = true),
            MoveRecord(2, Side.Black, 0, 0, 1, 0, positionHash = "h2"),
            MoveRecord(3, Side.Red, 8, 0, 7, 0, positionHash = "h3", isChase = true),
            MoveRecord(4, Side.Black, 1, 0, 2, 0, positionHash = "h4"),
        )
        val annotation = MoveAnnotation(
            side = Side.Red,
            moveNo = 5,
            positionHash = "h5",
            isCheck = false,
            isChase = true,
        )
        val decision = RepetitionJudge.decide(history, annotation)
        assertNull(decision.illegalTag)
        assertNull(decision.drawReason)
    }

    @Test
    fun neutralMove_shouldPass() {
        val decision = RepetitionJudge.decide(
            history = emptyList(),
            annotation = MoveAnnotation(
                side = Side.Red,
                moveNo = 1,
                positionHash = "a",
                isCheck = false,
                isChase = false,
            ),
        )
        assertTrue(decision.illegalTag == null && decision.drawReason == null)
    }
}
