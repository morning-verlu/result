package cn.verlu.cnchess.domain.chess

object RuleEngine {

    fun legalMovesFrom(board: BoardState, side: Side, from: Position): List<Move> {
        val piece = board.at(from) ?: return emptyList()
        if (piece.side != side) return emptyList()

        return pseudoMoves(board, from, piece).filter { mv ->
            val next = applyUnchecked(board, mv)
            !isInCheck(next, side)
        }
    }

    fun tryApplyMove(board: BoardState, side: Side, from: Position, to: Position): Pair<BoardState, Move>? {
        val legal = legalMovesFrom(board, side, from).firstOrNull { it.to == to } ?: return null
        return applyUnchecked(board, legal) to legal
    }

    fun isInCheck(board: BoardState, side: Side): Boolean {
        val kingPos = findKing(board, side) ?: return true
        val enemy = side.opposite()
        return allPiecePositions(board, enemy).any { (from, piece) ->
            pseudoMoves(board, from, piece).any { it.to == kingPos }
        }
    }

    fun hasAnyLegalMove(board: BoardState, side: Side): Boolean {
        return allPiecePositions(board, side).any { (from, _) ->
            legalMovesFrom(board, side, from).isNotEmpty()
        }
    }

    fun evaluateResult(board: BoardState, turnSide: Side): GameResult {
        if (hasAnyLegalMove(board, turnSide)) return GameResult.Ongoing
        return if (turnSide == Side.Red) GameResult.BlackWin else GameResult.RedWin
    }

    fun buildMoveAnnotation(
        nextBoard: BoardState,
        move: Move,
        moveNo: Long,
        side: Side,
        nextTurnSide: Side,
    ): MoveAnnotation {
        val isCheck = isInCheck(nextBoard, side.opposite())
        val isChase = isChaseMove(nextBoard, move)
        return MoveAnnotation(
            side = side,
            moveNo = moveNo,
            positionHash = RepetitionJudge.positionHash(nextBoard, nextTurnSide),
            isCheck = isCheck,
            isChase = isChase,
            judgeTag = when {
                isCheck -> "check"
                isChase -> "chase"
                else -> null
            },
        )
    }

    private fun applyUnchecked(board: BoardState, move: Move): BoardState {
        return board.with(move.from, null).with(move.to, move.piece)
    }

    private fun isChaseMove(board: BoardState, move: Move): Boolean {
        if (move.captured != null) return false
        val attacker = board.at(move.to) ?: return false
        val threats = pseudoMoves(board, move.to, attacker)
        return threats.any { threat ->
            val target = board.at(threat.to) ?: return@any false
            target.side != attacker.side && target.type in setOf(
                PieceType.King,
                PieceType.Rook,
                PieceType.Cannon,
                PieceType.Horse,
            )
        }
    }

    private fun allPiecePositions(board: BoardState, side: Side): List<Pair<Position, Piece>> {
        val result = mutableListOf<Pair<Position, Piece>>()
        for (row in 0..9) {
            for (col in 0..8) {
                val pos = Position(row, col)
                val piece = board.at(pos) ?: continue
                if (piece.side == side) result += pos to piece
            }
        }
        return result
    }

    private fun findKing(board: BoardState, side: Side): Position? {
        for (row in 0..9) {
            for (col in 0..8) {
                val pos = Position(row, col)
                val piece = board.at(pos) ?: continue
                if (piece.side == side && piece.type == PieceType.King) return pos
            }
        }
        return null
    }

    private fun pseudoMoves(board: BoardState, from: Position, piece: Piece): List<Move> = when (piece.type) {
        PieceType.King -> kingMoves(board, from, piece)
        PieceType.Advisor -> advisorMoves(board, from, piece)
        PieceType.Elephant -> elephantMoves(board, from, piece)
        PieceType.Horse -> horseMoves(board, from, piece)
        PieceType.Rook -> rookMoves(board, from, piece)
        PieceType.Cannon -> cannonMoves(board, from, piece)
        PieceType.Pawn -> pawnMoves(board, from, piece)
    }

    private fun kingMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        val candidates = listOf(
            Position(from.row - 1, from.col),
            Position(from.row + 1, from.col),
            Position(from.row, from.col - 1),
            Position(from.row, from.col + 1),
        ).filter { it.insideBoard() && inPalace(it, piece.side) }
        val result = candidates.mapNotNull { toMove(board, from, it, piece) }.toMutableList()

        // flying general capture
        val enemyKing = findKing(board, piece.side.opposite())
        if (enemyKing != null && enemyKing.col == from.col && clearBetweenRows(board, from.col, from.row, enemyKing.row)) {
            toMove(board, from, enemyKing, piece)?.let { result += it }
        }
        return result
    }

    private fun advisorMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        val deltas = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        return deltas.mapNotNull { (dr, dc) ->
            val to = Position(from.row + dr, from.col + dc)
            if (!to.insideBoard() || !inPalace(to, piece.side)) return@mapNotNull null
            toMove(board, from, to, piece)
        }
    }

    private fun elephantMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        val deltas = listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2)
        return deltas.mapNotNull { (dr, dc) ->
            val eye = Position(from.row + dr / 2, from.col + dc / 2)
            val to = Position(from.row + dr, from.col + dc)
            if (!to.insideBoard()) return@mapNotNull null
            if (!onOwnSide(to, piece.side)) return@mapNotNull null
            if (board.at(eye) != null) return@mapNotNull null
            toMove(board, from, to, piece)
        }
    }

    private fun horseMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        val hops = listOf(
            Triple(-2, -1, Position(from.row - 1, from.col)),
            Triple(-2, 1, Position(from.row - 1, from.col)),
            Triple(2, -1, Position(from.row + 1, from.col)),
            Triple(2, 1, Position(from.row + 1, from.col)),
            Triple(-1, -2, Position(from.row, from.col - 1)),
            Triple(1, -2, Position(from.row, from.col - 1)),
            Triple(-1, 2, Position(from.row, from.col + 1)),
            Triple(1, 2, Position(from.row, from.col + 1)),
        )
        return hops.mapNotNull { (dr, dc, leg) ->
            val to = Position(from.row + dr, from.col + dc)
            if (!to.insideBoard()) return@mapNotNull null
            if (board.at(leg) != null) return@mapNotNull null
            toMove(board, from, to, piece)
        }
    }

    private fun rookMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        return lineMoves(board, from, piece, cannonMode = false)
    }

    private fun cannonMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        return lineMoves(board, from, piece, cannonMode = true)
    }

    private fun pawnMoves(board: BoardState, from: Position, piece: Piece): List<Move> {
        val result = mutableListOf<Move>()
        val forward = if (piece.side == Side.Red) -1 else 1
        listOf(Position(from.row + forward, from.col)).forEach { to ->
            toMove(board, from, to, piece)?.let { result += it }
        }
        if (crossedRiver(from, piece.side)) {
            listOf(Position(from.row, from.col - 1), Position(from.row, from.col + 1)).forEach { to ->
                toMove(board, from, to, piece)?.let { result += it }
            }
        }
        return result
    }

    private fun lineMoves(board: BoardState, from: Position, piece: Piece, cannonMode: Boolean): List<Move> {
        val result = mutableListOf<Move>()
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in directions) {
            var r = from.row + dr
            var c = from.col + dc
            var jumped = false
            while (Position(r, c).insideBoard()) {
                val pos = Position(r, c)
                val target = board.at(pos)
                if (!cannonMode) {
                    if (target == null) {
                        result += Move(from, pos, piece, null)
                    } else {
                        if (target.side != piece.side) result += Move(from, pos, piece, target)
                        break
                    }
                } else {
                    if (!jumped) {
                        if (target == null) {
                            result += Move(from, pos, piece, null)
                        } else {
                            jumped = true
                        }
                    } else {
                        if (target != null) {
                            if (target.side != piece.side) result += Move(from, pos, piece, target)
                            break
                        }
                    }
                }
                r += dr
                c += dc
            }
        }
        return result
    }

    private fun toMove(board: BoardState, from: Position, to: Position, piece: Piece): Move? {
        if (!to.insideBoard()) return null
        val target = board.at(to)
        if (target?.side == piece.side) return null
        return Move(from = from, to = to, piece = piece, captured = target)
    }

    private fun inPalace(pos: Position, side: Side): Boolean {
        val palaceRows = if (side == Side.Red) 7..9 else 0..2
        return pos.row in palaceRows && pos.col in 3..5
    }

    private fun onOwnSide(pos: Position, side: Side): Boolean {
        return if (side == Side.Red) pos.row >= 5 else pos.row <= 4
    }

    private fun crossedRiver(pos: Position, side: Side): Boolean {
        return if (side == Side.Red) pos.row <= 4 else pos.row >= 5
    }

    private fun clearBetweenRows(board: BoardState, col: Int, fromRow: Int, toRow: Int): Boolean {
        val min = minOf(fromRow, toRow) + 1
        val max = maxOf(fromRow, toRow) - 1
        for (row in min..max) {
            if (board.at(Position(row, col)) != null) return false
        }
        return true
    }
}
