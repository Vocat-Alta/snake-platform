final class TurnResult {
    final GameState state;
    final int[] destinationCells;
    final boolean[] ateFood;
    final boolean[] died;
    final int[] killers;

    TurnResult(
            GameState state, int[] destinationCells, boolean[] ateFood,
            boolean[] died, int[] killers) {
        this.state = state;
        this.destinationCells = destinationCells;
        this.ateFood = ateFood;
        this.died = died;
        this.killers = killers;
    }
}
