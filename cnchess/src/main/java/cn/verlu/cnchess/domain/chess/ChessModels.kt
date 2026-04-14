package cn.verlu.cnchess.domain.chess

enum class Side {
    Red,
    Black;

    fun opposite(): Side = if (this == Red) Black else Red
}

enum class PieceType {
    King,
    Advisor,
    Elephant,
    Horse,
    Rook,
    Cannon,
    Pawn,
}

data class Piece(
    val side: Side,
    val type: PieceType,
)

data class Position(
    val row: Int,
    val col: Int,
) {
    fun insideBoard(): Boolean = row in 0..9 && col in 0..8
}

data class Move(
    val from: Position,
    val to: Position,
    val piece: Piece,
    val captured: Piece? = null,
)

enum class GameResult {
    Ongoing,
    RedWin,
    BlackWin,
    Draw,
}

data class BoardState(
    val cells: List<Piece?>,
) {
    init {
        require(cells.size == 90) { "board must have 90 cells" }
    }

    fun at(pos: Position): Piece? = cells[pos.row * 9 + pos.col]

    fun with(pos: Position, piece: Piece?): BoardState {
        val idx = pos.row * 9 + pos.col
        val mutable = cells.toMutableList()
        mutable[idx] = piece
        return copy(cells = mutable)
    }
}
