import java.util.Arrays;

final class GameState {
    static final int EMPTY = -1;
    static final int NO_CELL = -1;
    // Rules from configs/league-arena.toml.
    static final int START_LENGTH = 5;
    static final int GROWTH_PER_FOOD = 5;
    static final int RESPAWN_DELAY_TURNS = 5;

    final int width;
    final int height;
    final int appleX;
    final int appleY;
    final SnakeState[] snakes;
    final int turn;
    final int ourSnakeIndex;
    final int[] board;

    GameState(
            int width, int height, int appleX, int appleY, SnakeState[] snakes,
            int turn, int ourSnakeIndex) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Board dimensions must be positive");
        }
        boolean noApple = appleX == NO_CELL && appleY == NO_CELL;
        if (!noApple && (appleX < 0 || appleX >= width || appleY < 0 || appleY >= height)) {
            throw new IllegalArgumentException("Apple must be on the board");
        }
        if (snakes == null || snakes.length == 0
                || ourSnakeIndex < 0 || ourSnakeIndex >= snakes.length) {
            throw new IllegalArgumentException("Our snake index must identify a snake");
        }

        this.width = width;
        this.height = height;
        this.appleX = appleX;
        this.appleY = appleY;
        this.snakes = snakes.clone();
        this.turn = turn;
        this.ourSnakeIndex = ourSnakeIndex;
        this.board = new int[width * height];
        Arrays.fill(board, EMPTY);
        for (int snakeIndex = 0; snakeIndex < this.snakes.length; snakeIndex++) {
            SnakeState snake = this.snakes[snakeIndex];
            if (!snake.alive) continue;
            for (int cell : snake.body) board[cell] = snakeIndex;
        }
    }

    static GameState fromObservation(
            int width, int height, int ourSnakeIndex, int appleX, int appleY,
            String[] observations, int turn, GameState previous) {
        if (observations == null) {
            throw new IllegalArgumentException("Snake observations are required");
        }

        SnakeState[] snakes = new SnakeState[observations.length];
        for (int i = 0; i < observations.length; i++) {
            SnakeState previousSnake = previous != null && previous.snakes.length == observations.length
                    ? previous.snakes[i] : null;
            int respawnIn = -1;
            if (!isAlive(observations[i]) && previousSnake != null) {
                if (previousSnake.alive) {
                    respawnIn = RESPAWN_DELAY_TURNS - 1;
                } else if (previousSnake.respawnIn > 0) {
                    respawnIn = previousSnake.respawnIn - 1;
                } else {
                    respawnIn = previousSnake.respawnIn;
                }
            }
            snakes[i] = SnakeState.fromObservation(
                    observations[i], width, height, previousSnake, respawnIn);
        }
        return new GameState(width, height, appleX, appleY, snakes, turn, ourSnakeIndex);
    }

    TurnResult simulateTurn(TurnInputs inputs) {
        return TurnSimulator.simulate(this, inputs);
    }

    int[] legalMoves(int snakeIndex) {
        SnakeState snake = snakes[snakeIndex];
        if (!snake.alive) return new int[0];
        int facing = snake.previousDirection < 0 ? 0 : snake.previousDirection;
        int clockwiseIndex = indexOfClockwise(facing);
        int[] clockwise = {0, 3, 1, 2};
        return new int[] {
            facing,
            clockwise[Math.floorMod(clockwiseIndex - 1, clockwise.length)],
            clockwise[(clockwiseIndex + 1) % clockwise.length]
        };
    }

    int cellAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException("Cell must be on the board");
        }
        return board[y * width + x];
    }

    private static boolean isAlive(String observation) {
        return observation != null && observation.trim().startsWith("alive");
    }

    private static int indexOfClockwise(int direction) {
        int[] clockwise = {0, 3, 1, 2};
        for (int i = 0; i < clockwise.length; i++) {
            if (clockwise[i] == direction) return i;
        }
        throw new IllegalArgumentException("Direction must be between 0 and 3");
    }
}
