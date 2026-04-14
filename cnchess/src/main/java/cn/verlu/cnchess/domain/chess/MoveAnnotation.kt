package cn.verlu.cnchess.domain.chess

data class MoveAnnotation(
    val side: Side,
    val moveNo: Long,
    val positionHash: String,
    val isCheck: Boolean,
    val isChase: Boolean,
    val judgeTag: String? = null,
)
