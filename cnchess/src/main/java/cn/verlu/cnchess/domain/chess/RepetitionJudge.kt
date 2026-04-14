package cn.verlu.cnchess.domain.chess

import cn.verlu.cnchess.domain.model.MoveRecord

data class JudgeDecision(
    val illegalTag: String? = null,
    val drawReason: String? = null,
)

object RepetitionJudge {

    fun decide(
        history: List<MoveRecord>,
        annotation: MoveAnnotation,
    ): JudgeDecision {
        val hashCount = history.count { it.positionHash == annotation.positionHash } + 1
        if (hashCount >= 3) {
            return JudgeDecision(drawReason = "threefold_repetition")
        }

        val sameSideHistory = history.filter { it.side == annotation.side }
        val recentThreeChecks = (sameSideHistory.takeLast(2).all { it.isCheck } && annotation.isCheck)
        if (recentThreeChecks) {
            return JudgeDecision(illegalTag = "illegal_long_check")
        }

        val recentThreeChases = (sameSideHistory.takeLast(2).all { it.isChase } && annotation.isChase)
        if (recentThreeChases) {
            return JudgeDecision(illegalTag = "illegal_long_chase")
        }

        return JudgeDecision()
    }

    fun positionHash(board: BoardState, turnSide: Side): String =
        "${BoardCodec.encode(board)}|${if (turnSide == Side.Red) "r" else "b"}"
}
