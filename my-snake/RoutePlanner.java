import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

final class RoutePlanner {
    private static final int MAX_EXPANSIONS = 6000;
    private static final int MAX_ROUTE_DEPTH = 80;
    private static final int MAX_SPACE_DETOUR = 2;
    private static final int MIN_SPACE_GAIN = 8;

    private RoutePlanner() {}

    static int chooseMove(GameState state) {
        SnakeState mine = state.snakes[state.ourSnakeIndex];
        int[] legalMoves = state.legalMoves(state.ourSnakeIndex);
        int[] opponentReleaseTime = opponentReleaseTimes(state);
        boolean[] blockedNextTurn = occupiedAtDepth(opponentReleaseTime, 1);
        PriorityQueue<RouteNode> frontier = new PriorityQueue<>(
                Comparator.comparingInt((RouteNode node) -> node.depth + node.heuristic)
                        .thenComparingInt(node -> node.heuristic));
        Map<BodyKey, Integer> bestDepth = new HashMap<>();
        RouteOption[] routes = new RouteOption[4];
        int fallbackMove = -1;
        int fallbackArea = -1;
        int threatenedFallback = -1;
        int shortestFoodDistance = Integer.MAX_VALUE;

        for (int move : legalMoves) {
            int destination = nextCell(state, mine.body[0], move);
            if (blockedNextTurn[destination]) continue;

            int effectiveLength = mine.length
                    + (destination == appleCell(state) ? GameState.GROWTH_PER_FOOD : 0);
            if (hasHeadToHeadThreat(state, destination, effectiveLength)) {
                threatenedFallback = move;
                continue;
            }

            SnakeState nextSnake = mine.move(
                    move, state.height, destination == appleCell(state),
                    GameState.GROWTH_PER_FOOD);
            if (overlapsOwnBody(nextSnake)) continue;
            int area = reachableArea(state, opponentReleaseTime, 1, nextSnake);
            int forcedKills = countForcedKills(
                    state, blockedNextTurn, nextSnake, destination);
            int opponentEscapes = countOpponentEscapeMoves(
                    state, blockedNextTurn, nextSnake, destination);
            if (area > fallbackArea) {
                fallbackArea = area;
                fallbackMove = move;
            }

            if (destination == appleCell(state)) {
                RouteOption route = routeOption(
                        state, opponentReleaseTime, nextSnake, move, 1,
                        forcedKills, opponentEscapes);
                routes[move] = betterRoute(routes[move], route);
                shortestFoodDistance = 1;
            } else {
                addNode(frontier, bestDepth,
                        new RouteNode(nextSnake, 1, move,
                                distanceToApple(state, destination), forcedKills,
                                opponentEscapes));
            }
        }

        int expansions = 0;
        while (!frontier.isEmpty() && expansions < MAX_EXPANSIONS) {
            RouteNode node = frontier.poll();
            if (shortestFoodDistance != Integer.MAX_VALUE
                    && node.depth + node.heuristic
                    > shortestFoodDistance + MAX_SPACE_DETOUR) {
                break;
            }
            BodyKey key = new BodyKey(node.snake.body, node.direction, node.firstMove);
            Integer knownDepth = bestDepth.get(key);
            if (knownDepth != null && knownDepth < node.depth) continue;
            expansions++;

            for (int direction : legalMoves(node.direction)) {
                int head = nextCell(state, node.snake.body[0], direction);
                int depth = node.depth + 1;
                if (isOccupied(opponentReleaseTime, head, depth)) continue;
                boolean eatsApple = head == appleCell(state);
                SnakeState snake = node.snake.move(
                        direction, state.height, eatsApple, GameState.GROWTH_PER_FOOD);
                if (overlapsOwnBody(snake)) continue;

                if (eatsApple) {
                    RouteOption route = routeOption(
                            state, opponentReleaseTime, snake, node.firstMove, depth,
                            node.firstMoveForcedKills, node.firstMoveOpponentEscapes);
                    routes[node.firstMove] = betterRoute(routes[node.firstMove], route);
                    shortestFoodDistance = Math.min(shortestFoodDistance, depth);
                    continue;
                }

                if (depth >= MAX_ROUTE_DEPTH) continue;
                addNode(frontier, bestDepth,
                        new RouteNode(snake, depth, node.firstMove,
                                distanceToApple(state, head), node.firstMoveForcedKills,
                                node.firstMoveOpponentEscapes));
            }
        }

        RouteOption shortest = null;
        for (RouteOption route : routes) shortest = betterRoute(shortest, route);
        if (shortest != null) {
            RouteOption roomier = null;
            int requiredGain = Math.max(MIN_SPACE_GAIN, shortest.area / 2);
            for (RouteOption route : routes) {
                if (route == null || route.distance > shortest.distance + MAX_SPACE_DETOUR
                        || route.area < shortest.area + requiredGain) {
                    continue;
                }
                if (roomier == null || betterRoomierRoute(route, roomier)) roomier = route;
            }
            if (roomier != null) return roomier.move;
            return shortest.move;
        }
        if (fallbackMove >= 0) return fallbackMove;
        if (threatenedFallback >= 0) return threatenedFallback;
        return legalMoves.length == 0 ? 0 : legalMoves[0];
    }

    private static void addNode(
            PriorityQueue<RouteNode> frontier, Map<BodyKey, Integer> bestDepth, RouteNode node) {
        BodyKey key = new BodyKey(node.snake.body, node.direction, node.firstMove);
        Integer priorDepth = bestDepth.get(key);
        if (priorDepth != null && priorDepth <= node.depth) return;
        bestDepth.put(key, node.depth);
        frontier.add(node);
    }

    private static RouteOption routeOption(
            GameState state, int[] opponentReleaseTime, SnakeState snake,
            int firstMove, int distance, int forcedKills, int opponentEscapes) {
        RouteOption option = new RouteOption(firstMove, distance,
                reachableArea(state, opponentReleaseTime, distance, snake));
        option.forcedKills = forcedKills;
        option.opponentEscapes = opponentEscapes;
        return option;
    }

    private static int reachableArea(
            GameState state, int[] opponentReleaseTime, int depth, SnakeState snake) {
        int[] board = new int[opponentReleaseTime.length];
        Arrays.fill(board, GameState.EMPTY);
        for (int cell = 0; cell < opponentReleaseTime.length; cell++) {
            if (isOccupied(opponentReleaseTime, cell, depth)) board[cell] = 0;
        }
        for (int cell : snake.body) board[cell] = state.ourSnakeIndex;
        int head = snake.body[0];
        return Search.reachableArea(
                board, state.width, state.height, head % state.width, head / state.width);
    }

    private static int[] opponentReleaseTimes(GameState state) {
        int[] releaseTime = new int[state.width * state.height];
        for (int i = 0; i < state.snakes.length; i++) {
            SnakeState snake = state.snakes[i];
            if (!snake.alive || i == state.ourSnakeIndex) continue;
            int foodGrowthBuffer = canEatAppleNextTurn(state, i)
                    ? GameState.GROWTH_PER_FOOD : 0;
            for (int segment = 0; segment < snake.body.length; segment++) {
                int cell = snake.body[segment];
                int release = snake.body.length - segment + foodGrowthBuffer;
                releaseTime[cell] = Math.max(releaseTime[cell], release);
            }
        }
        return releaseTime;
    }

    private static boolean[] occupiedAtDepth(int[] releaseTime, int depth) {
        boolean[] occupied = new boolean[releaseTime.length];
        for (int cell = 0; cell < releaseTime.length; cell++) {
            occupied[cell] = isOccupied(releaseTime, cell, depth);
        }
        return occupied;
    }

    private static boolean isOccupied(int[] releaseTime, int cell, int depth) {
        return releaseTime[cell] > depth;
    }

    private static boolean canEatAppleNextTurn(GameState state, int snakeIndex) {
        int apple = appleCell(state);
        if (apple == GameState.NO_CELL) return false;
        SnakeState snake = state.snakes[snakeIndex];
        for (int move : state.legalMoves(snakeIndex)) {
            if (nextCell(state, snake.body[0], move) == apple) return true;
        }
        return false;
    }

    private static boolean hasHeadToHeadThreat(GameState state, int destination, int ourLength) {
        int apple = appleCell(state);
        for (int i = 0; i < state.snakes.length; i++) {
            if (i == state.ourSnakeIndex || !state.snakes[i].alive) continue;
            SnakeState opponent = state.snakes[i];
            for (int move : state.legalMoves(i)) {
                int opponentDestination = nextCell(state, opponent.body[0], move);
                if (opponentDestination != destination) continue;
                int opponentLength = opponent.length
                        + (opponentDestination == apple ? GameState.GROWTH_PER_FOOD : 0);
                if (opponentLength >= ourLength) return true;
            }
        }
        return false;
    }

    private static int countForcedKills(
            GameState state, boolean[] opponentBlocked, SnakeState ourNextSnake, int ourHead) {
        boolean[] blocked = opponentBlocked.clone();
        for (int cell : ourNextSnake.body) blocked[cell] = true;
        blocked[ourHead] = true;
        int forcedKills = 0;

        for (int i = 0; i < state.snakes.length; i++) {
            SnakeState opponent = state.snakes[i];
            if (i == state.ourSnakeIndex || !opponent.alive) {
                continue;
            }

            boolean canHitOurHead = false;
            boolean hasOtherMove = false;
            for (int move : state.legalMoves(i)) {
                int destination = nextCell(state, opponent.body[0], move);
                if (destination == ourHead) {
                    int opponentLength = opponent.length
                            + (destination == appleCell(state)
                            ? GameState.GROWTH_PER_FOOD : 0);
                    if (opponentLength >= ourNextSnake.length) continue;
                    canHitOurHead = true;
                } else if (!blocked[destination]) {
                    hasOtherMove = true;
                }
            }
            if (canHitOurHead && !hasOtherMove) forcedKills++;
        }
        return forcedKills;
    }

    private static int countOpponentEscapeMoves(
            GameState state, boolean[] opponentBlocked, SnakeState ourNextSnake, int ourHead) {
        boolean[] blocked = opponentBlocked.clone();
        for (int cell : ourNextSnake.body) blocked[cell] = true;
        blocked[ourHead] = true;
        int escapeMoves = 0;
        int apple = appleCell(state);

        for (int i = 0; i < state.snakes.length; i++) {
            SnakeState opponent = state.snakes[i];
            if (i == state.ourSnakeIndex || !opponent.alive) continue;

            for (int move : state.legalMoves(i)) {
                int destination = nextCell(state, opponent.body[0], move);
                int effectiveLength = opponent.length
                        + (destination == apple ? GameState.GROWTH_PER_FOOD : 0);
                if (effectiveLength >= ourNextSnake.length || blocked[destination]) continue;
                escapeMoves++;
            }
        }
        return escapeMoves;
    }

    private static int[] legalMoves(int facing) {
        int clockwiseIndex = indexOfClockwise(facing);
        int[] clockwise = {0, 3, 1, 2};
        return new int[] {
            facing,
            clockwise[Math.floorMod(clockwiseIndex - 1, clockwise.length)],
            clockwise[(clockwiseIndex + 1) % clockwise.length]
        };
    }

    private static int indexOfClockwise(int direction) {
        int[] clockwise = {0, 3, 1, 2};
        for (int i = 0; i < clockwise.length; i++) {
            if (clockwise[i] == direction) return i;
        }
        throw new IllegalArgumentException("Direction must be between 0 and 3");
    }

    private static boolean overlapsOwnBody(SnakeState snake) {
        for (int i = 1; i < snake.body.length; i++) {
            if (snake.body[0] == snake.body[i]) return true;
        }
        return false;
    }

    private static int distanceToApple(GameState state, int cell) {
        int apple = appleCell(state);
        if (apple == GameState.NO_CELL) return 0;
        int dx = Math.abs(cell % state.width - apple % state.width);
        int dy = Math.abs(cell / state.width - apple / state.width);
        return Math.min(dx, state.width - dx) + Math.min(dy, state.height - dy);
    }

    private static int appleCell(GameState state) {
        return state.appleX == GameState.NO_CELL
                ? GameState.NO_CELL : state.appleY * state.width + state.appleX;
    }

    private static int nextCell(GameState state, int cell, int direction) {
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        int x = Math.floorMod(cell % state.width + dx[direction], state.width);
        int y = Math.floorMod(cell / state.width + dy[direction], state.height);
        return y * state.width + x;
    }

    private static RouteOption betterRoute(RouteOption first, RouteOption second) {
        if (first == null) return second;
        if (second == null) return first;
        if (second.distance != first.distance) {
            return second.distance < first.distance ? second : first;
        }
        if (second.area != first.area) return second.area > first.area ? second : first;
        if (second.forcedKills != first.forcedKills) {
            return second.forcedKills > first.forcedKills ? second : first;
        }
        if (second.opponentEscapes != first.opponentEscapes) {
            return second.opponentEscapes < first.opponentEscapes ? second : first;
        }
        return first;
    }

    private static boolean betterRoomierRoute(RouteOption candidate, RouteOption current) {
        return candidate.area > current.area
                || (candidate.area == current.area
                && (candidate.forcedKills > current.forcedKills
                || (candidate.forcedKills == current.forcedKills
                && (candidate.opponentEscapes < current.opponentEscapes
                || (candidate.opponentEscapes == current.opponentEscapes
                && candidate.distance < current.distance)))));
    }

    private static final class RouteNode {
        final SnakeState snake;
        final int direction;
        final int depth;
        final int firstMove;
        final int heuristic;
        final int firstMoveForcedKills;
        final int firstMoveOpponentEscapes;

        RouteNode(
                SnakeState snake, int depth, int firstMove,
                int heuristic, int firstMoveForcedKills, int firstMoveOpponentEscapes) {
            this.snake = snake;
            this.direction = snake.previousDirection;
            this.depth = depth;
            this.firstMove = firstMove;
            this.heuristic = heuristic;
            this.firstMoveForcedKills = firstMoveForcedKills;
            this.firstMoveOpponentEscapes = firstMoveOpponentEscapes;
        }
    }

    private static final class BodyKey {
        private final int[] body;
        private final int direction;
        private final int firstMove;
        private final int hash;

        BodyKey(int[] body, int direction, int firstMove) {
            this.body = body;
            this.direction = direction;
            this.firstMove = firstMove;
            this.hash = 31 * (31 * Arrays.hashCode(body) + direction) + firstMove;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BodyKey)) return false;
            BodyKey key = (BodyKey) other;
            return direction == key.direction && firstMove == key.firstMove
                    && Arrays.equals(body, key.body);
        }
    }

    private static final class RouteOption {
        final int move;
        final int distance;
        final int area;
        int forcedKills;
        int opponentEscapes;

        RouteOption(int move, int distance, int area) {
            this.move = move;
            this.distance = distance;
            this.area = area;
        }
    }
}
