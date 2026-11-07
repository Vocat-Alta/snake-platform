import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class TurnSimulator {
    private TurnSimulator() {}

    static TurnResult simulate(GameState state, TurnInputs inputs) {
        int snakeCount = state.snakes.length;
        if (inputs.moves.length != snakeCount) {
            throw new IllegalArgumentException("One move is required for every snake");
        }
        validateRespawnInputs(state, inputs);

        SnakeState[] moved = new SnakeState[snakeCount];
        int[] destinations = new int[snakeCount];
        boolean[] ateFood = new boolean[snakeCount];
        boolean[] died = new boolean[snakeCount];
        int[] killers = new int[snakeCount];
        Arrays.fill(destinations, GameState.NO_CELL);
        Arrays.fill(killers, -1);

        int appleCell = state.appleX == GameState.NO_CELL
                ? GameState.NO_CELL : state.appleY * state.width + state.appleX;
        boolean foodConsumed = false;

        for (int i = 0; i < snakeCount; i++) {
            SnakeState snake = state.snakes[i];
            if (!snake.alive) {
                moved[i] = respawnOrWait(state, snake, inputs, i);
                if (moved[i].alive) {
                    if (!isDirection(inputs.moves[i])) {
                        throw new IllegalArgumentException("A respawning snake needs a move");
                    }
                    int direction = moved[i].resolvedDirection(inputs.moves[i]);
                    int nextHead = nextCell(state, moved[i].body[0], direction);
                    ateFood[i] = nextHead == appleCell;
                    foodConsumed |= ateFood[i];
                    moved[i] = moved[i].move(
                            direction, state.height, ateFood[i], GameState.GROWTH_PER_FOOD);
                    destinations[i] = nextHead;
                }
                continue;
            }

            if (!isDirection(inputs.moves[i])) {
                throw new IllegalArgumentException("An alive snake needs a direction from 0 to 3");
            }
            int direction = snake.resolvedDirection(inputs.moves[i]);
            int nextHead = nextCell(state, snake.body[0], direction);
            ateFood[i] = nextHead == appleCell;
            foodConsumed |= ateFood[i];
            moved[i] = snake.move(
                    direction, state.height, ateFood[i], GameState.GROWTH_PER_FOOD);
            destinations[i] = nextHead;
        }

        resolveHeadToHead(moved, died, killers);
        resolveBodyCollisions(moved, died, killers);
        awardKills(moved, died, killers);
        for (int i = 0; i < snakeCount; i++) {
            if (died[i]) moved[i] = moved[i].deadAfterTurn(GameState.RESPAWN_DELAY_TURNS - 1);
        }

        int nextAppleCell = foodConsumed ? inputs.nextAppleCell : appleCell;
        validateSpawnCell(state, moved, nextAppleCell, foodConsumed);
        int nextAppleX = nextAppleCell == GameState.NO_CELL
                ? GameState.NO_CELL : nextAppleCell % state.width;
        int nextAppleY = nextAppleCell == GameState.NO_CELL
                ? GameState.NO_CELL : nextAppleCell / state.width;
        GameState nextState = new GameState(
                state.width, state.height, nextAppleX, nextAppleY,
                moved, state.turn + 1, state.ourSnakeIndex);
        return new TurnResult(nextState, destinations, ateFood, died, killers);
    }

    private static SnakeState respawnOrWait(
            GameState state, SnakeState snake, TurnInputs inputs, int snakeIndex) {
        if (snake.respawnIn < 0) return snake;
        if (snake.respawnIn > 0) {
            return snake.deadAfterTurn(snake.respawnIn - 1);
        }

        int cell = inputs.respawnCells[snakeIndex];
        if (cell == GameState.NO_CELL) return snake;
        if (cell < 0 || cell >= state.width * state.height) {
            throw new IllegalArgumentException("Respawn cell must be on the board");
        }
        if (state.board[cell] != GameState.EMPTY) {
            throw new IllegalArgumentException("Respawn cell must be unoccupied");
        }
        return SnakeState.atRespawn(
                cell, GameState.START_LENGTH, snake.kills, state.width);
    }

    private static void validateSpawnCell(
            GameState state, SnakeState[] snakes, int cell, boolean foodConsumed) {
        if (!foodConsumed || cell == GameState.NO_CELL) return;
        if (cell < 0 || cell >= state.width * state.height) {
            throw new IllegalArgumentException("New food cell must be on the board");
        }
        for (SnakeState snake : snakes) {
            if (!snake.alive) continue;
            for (int bodyCell : snake.body) {
                if (bodyCell == cell) {
                    throw new IllegalArgumentException("New food cell must be unoccupied");
                }
            }
        }
    }

    private static void validateRespawnInputs(GameState state, TurnInputs inputs) {
        boolean[] usedCells = new boolean[state.width * state.height];
        for (int i = 0; i < state.snakes.length; i++) {
            if (state.snakes[i].alive || state.snakes[i].respawnIn != 0) continue;
            int cell = inputs.respawnCells[i];
            if (cell == GameState.NO_CELL) continue;
            if (cell < 0 || cell >= usedCells.length || state.board[cell] != GameState.EMPTY) {
                throw new IllegalArgumentException("Respawn cell must be an unoccupied board cell");
            }
            if (usedCells[cell]) {
                throw new IllegalArgumentException("Respawn cells must be distinct");
            }
            usedCells[cell] = true;
        }
    }

    private static void resolveHeadToHead(
            SnakeState[] snakes, boolean[] died, int[] killers) {
        Map<Integer, Integer> largestAtCell = new HashMap<>();
        Map<Integer, Integer> contendersAtCell = new HashMap<>();
        Map<Integer, Integer> topContendersAtCell = new HashMap<>();

        for (int i = 0; i < snakes.length; i++) {
            if (!snakes[i].alive) continue;
            int cell = snakes[i].body[0];
            int length = snakes[i].length;
            int largest = largestAtCell.getOrDefault(cell, -1);
            if (length > largest) {
                largestAtCell.put(cell, length);
                topContendersAtCell.put(cell, 1 << i);
            } else if (length == largest) {
                topContendersAtCell.put(cell, topContendersAtCell.get(cell) | (1 << i));
            }
            contendersAtCell.merge(cell, 1, Integer::sum);
        }

        for (Map.Entry<Integer, Integer> entry : contendersAtCell.entrySet()) {
            if (entry.getValue() < 2) continue;
            int cell = entry.getKey();
            int top = topContendersAtCell.get(cell);
            int winner = Integer.bitCount(top) == 1 ? Integer.numberOfTrailingZeros(top) : -1;
            for (int i = 0; i < snakes.length; i++) {
                if (!snakes[i].alive || snakes[i].body[0] != cell || i == winner) continue;
                died[i] = true;
                if (winner >= 0) killers[i] = winner;
            }
        }
    }

    private static void resolveBodyCollisions(
            SnakeState[] snakes, boolean[] died, int[] killers) {
        for (int i = 0; i < snakes.length; i++) {
            if (!snakes[i].alive || died[i]) continue;
            int head = snakes[i].body[0];
            for (int owner = 0; owner < snakes.length; owner++) {
                if (!snakes[owner].alive) continue;
                for (int segment = 1; segment < snakes[owner].body.length; segment++) {
                    if (snakes[owner].body[segment] != head) continue;
                    died[i] = true;
                    if (owner != i) killers[i] = owner;
                    break;
                }
                if (died[i]) break;
            }
        }
    }

    private static void awardKills(SnakeState[] snakes, boolean[] died, int[] killers) {
        int[] killCounts = new int[snakes.length];
        for (int i = 0; i < snakes.length; i++) {
            if (died[i] && killers[i] >= 0 && killers[i] != i && !died[killers[i]]) {
                killCounts[killers[i]]++;
            }
        }
        for (int i = 0; i < snakes.length; i++) {
            if (killCounts[i] > 0) snakes[i] = snakes[i].withKills(snakes[i].kills + killCounts[i]);
        }
    }

    private static int nextCell(GameState state, int headCell, int direction) {
        int x = headCell % state.width;
        int y = headCell / state.width;
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        int nextX = Math.floorMod(x + dx[direction], state.width);
        int nextY = Math.floorMod(y + dy[direction], state.height);
        return nextY * state.width + nextX;
    }

    private static boolean isDirection(int direction) {
        return direction >= 0 && direction <= 3;
    }
}
