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
        val isRepeatedPosition = hashCount >= 3
        val sameSideHistory = history.filter { it.side == annotation.side }
        val recentThreeChecks = (sameSideHistory.takeLast(2).all { it.isCheck } && annotation.isCheck)
        if (recentThreeChecks) {
            return JudgeDecision(illegalTag = "illegal_long_check")
        }

        val recentThreeChases = (sameSideHistory.takeLast(2).all { it.isChase } && annotation.isChase)
        // 长捉需要建立在重复局面上，避免普通持续施压被误判为禁着。
        if (recentThreeChases && isRepeatedPosition) {
            return JudgeDecision(illegalTag = "illegal_long_chase")
        }

        if (isRepeatedPosition) {
            return JudgeDecision(drawReason = "threefold_repetition")
        }

        return JudgeDecision()
    }

    fun positionHash(board: BoardState, turnSide: Side): String =
        "${BoardCodec.encode(board)}|${if (turnSide == Side.Red) "r" else "b"}"
}
