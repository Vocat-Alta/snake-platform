/** Per-turn decisions and injected engine-random outcomes for the simulator. */
final class TurnInputs {
    // Absolute directions: up=0, down=1, left=2, right=3; -1 for a dead snake.
    final int[] moves;
    // Cell index after food is eaten, or GameState.NO_CELL if there is no spawn.
    final int nextAppleCell;
    // Respawn cell by snake index, or GameState.NO_CELL when not respawning.
    final int[] respawnCells;

    TurnInputs(int[] moves, int nextAppleCell, int[] respawnCells) {
        if (moves == null || respawnCells == null || moves.length != respawnCells.length) {
            throw new IllegalArgumentException("Moves and respawn cells must cover every snake");
        }
        this.moves = moves.clone();
        this.nextAppleCell = nextAppleCell;
        this.respawnCells = respawnCells.clone();
    }
}
