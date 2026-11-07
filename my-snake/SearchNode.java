final class SearchNode {
    final GameState state;
    final int firstMove;
    final int depth;
    final double score;

    SearchNode(GameState state, int firstMove, int depth, double score) {
        if (state == null) throw new IllegalArgumentException("Search state is required");
        if (depth < 0) throw new IllegalArgumentException("Search depth cannot be negative");
        this.state = state;
        this.firstMove = firstMove;
        this.depth = depth;
        this.score = score;
    }
}
