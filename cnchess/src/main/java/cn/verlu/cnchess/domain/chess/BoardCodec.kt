package cn.verlu.cnchess.domain.chess

object BoardCodec {

    fun initialBoard(): BoardState {
        val cells = MutableList<Piece?>(90) { null }
        fun put(row: Int, col: Int, side: Side, type: PieceType) {
            cells[row * 9 + col] = Piece(side = side, type = type)
        }

        // Black
        put(0, 0, Side.Black, PieceType.Rook)
        put(0, 1, Side.Black, PieceType.Horse)
        put(0, 2, Side.Black, PieceType.Elephant)
        put(0, 3, Side.Black, PieceType.Advisor)
        put(0, 4, Side.Black, PieceType.King)
        put(0, 5, Side.Black, PieceType.Advisor)
        put(0, 6, Side.Black, PieceType.Elephant)
        put(0, 7, Side.Black, PieceType.Horse)
        put(0, 8, Side.Black, PieceType.Rook)
        put(2, 1, Side.Black, PieceType.Cannon)
        put(2, 7, Side.Black, PieceType.Cannon)
        listOf(0, 2, 4, 6, 8).forEach { put(3, it, Side.Black, PieceType.Pawn) }

        // Red
        put(9, 0, Side.Red, PieceType.Rook)
        put(9, 1, Side.Red, PieceType.Horse)
        put(9, 2, Side.Red, PieceType.Elephant)
        put(9, 3, Side.Red, PieceType.Advisor)
        put(9, 4, Side.Red, PieceType.King)
        put(9, 5, Side.Red, PieceType.Advisor)
        put(9, 6, Side.Red, PieceType.Elephant)
        put(9, 7, Side.Red, PieceType.Horse)
        put(9, 8, Side.Red, PieceType.Rook)
        put(7, 1, Side.Red, PieceType.Cannon)
        put(7, 7, Side.Red, PieceType.Cannon)
        listOf(0, 2, 4, 6, 8).forEach { put(6, it, Side.Red, PieceType.Pawn) }

        return BoardState(cells)
    }

    fun encode(board: BoardState): String {
        val sb = StringBuilder()
        for (row in 0..9) {
            var empty = 0
            for (col in 0..8) {
                val piece = board.at(Position(row, col))
                if (piece == null) {
                    empty++
                    continue
                }
                if (empty > 0) {
                    sb.append(empty)
                    empty = 0
                }
                sb.append(piece.toFenChar())
            }
            if (empty > 0) sb.append(empty)
            if (row != 9) sb.append('/')
        }
        return sb.toString()
    }

    fun decode(fenBoard: String): BoardState {
        val rows = fenBoard.split('/')
        require(rows.size == 10) { "invalid board rows" }
        val cells = mutableListOf<Piece?>()
        rows.forEach { line ->
            line.forEach { ch ->
                if (ch.isDigit()) {
                    repeat(ch.digitToInt()) { cells.add(null) }
                } else {
                    cells.add(ch.toPiece())
                }
            }
        }
        require(cells.size == 90) { "invalid board cells" }
        return BoardState(cells)
    }

    private fun Piece.toFenChar(): Char = when (type) {
        PieceType.King -> if (side == Side.Red) 'K' else 'k'
        PieceType.Advisor -> if (side == Side.Red) 'A' else 'a'
        PieceType.Elephant -> if (side == Side.Red) 'B' else 'b'
        PieceType.Horse -> if (side == Side.Red) 'N' else 'n'
        PieceType.Rook -> if (side == Side.Red) 'R' else 'r'
        PieceType.Cannon -> if (side == Side.Red) 'C' else 'c'
        PieceType.Pawn -> if (side == Side.Red) 'P' else 'p'
    }

    private fun Char.toPiece(): Piece = when (this) {
        'K' -> Piece(Side.Red, PieceType.King)
        'A' -> Piece(Side.Red, PieceType.Advisor)
        'B' -> Piece(Side.Red, PieceType.Elephant)
        'N' -> Piece(Side.Red, PieceType.Horse)
        'R' -> Piece(Side.Red, PieceType.Rook)
        'C' -> Piece(Side.Red, PieceType.Cannon)
        'P' -> Piece(Side.Red, PieceType.Pawn)
        'k' -> Piece(Side.Black, PieceType.King)
        'a' -> Piece(Side.Black, PieceType.Advisor)
        'b' -> Piece(Side.Black, PieceType.Elephant)
        'n' -> Piece(Side.Black, PieceType.Horse)
        'r' -> Piece(Side.Black, PieceType.Rook)
        'c' -> Piece(Side.Black, PieceType.Cannon)
        'p' -> Piece(Side.Black, PieceType.Pawn)
        else -> error("invalid piece char $this")
    }
}
