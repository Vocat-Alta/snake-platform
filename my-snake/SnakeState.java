import java.util.ArrayList;
import java.util.List;

class SnakeState {
    final int[] body;
    // Extra copies of the tail cell that must shift off before the tail advances.
    final int pendingGrowth;
    final int length;
    final int kills;
    final boolean alive;
    final int previousDirection;
    final int respawnIn;
    private final int width;

    private SnakeState(
            int[] body, int pendingGrowth, int kills, boolean alive,
            int previousDirection, int respawnIn, int width) {
        this.body = body;
        this.pendingGrowth = pendingGrowth;
        this.length = body.length;
        this.kills = kills;
        this.alive = alive;
        this.previousDirection = previousDirection;
        this.respawnIn = respawnIn;
        this.width = width;
    }

    static SnakeState fromObservation(
            String line, int width, int height, SnakeState previous, int respawnIn) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Board dimensions must be positive");
        }
        if (line == null || line.trim().isEmpty()) {
            return new SnakeState(new int[0], 0, 0, false, -1, respawnIn, width);
        }
        String[] fields = line.trim().split("\\s+");
        boolean alive = fields[0].equals("alive");
        int reportedLength = Integer.parseInt(fields[1]);
        int kills = Integer.parseInt(fields[2]);
        if (!alive) return new SnakeState(new int[0], 0, kills, false, -1, respawnIn, width);

        List<Integer> cells = new ArrayList<>();
        List<int[]> points = new ArrayList<>();
        for (int i = 3; i < fields.length; i++) {
            int comma = fields[i].indexOf(',');
            if (comma < 1 || comma == fields[i].length() - 1) {
                throw new IllegalArgumentException("Invalid coordinate: " + fields[i]);
            }
            int x = Integer.parseInt(fields[i].substring(0, comma));
            int y = Integer.parseInt(fields[i].substring(comma + 1));
            if (x < 0 || x >= width || y < 0 || y >= height) {
                throw new IllegalArgumentException("Coordinate outside board: " + fields[i]);
            }
            points.add(new int[] {x, y});
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Alive snake must have a head coordinate");
        }

        addCell(cells, points.get(0)[0], points.get(0)[1], width);
        boolean skipTeleportSegment = false;
        for (int i = 0; i + 1 < points.size(); i++) {
            int[] from = points.get(i);
            int[] to = points.get(i + 1);
            if (from[0] == to[0] && from[1] == to[1]) {
                skipTeleportSegment = true;
                continue;
            }
            if (skipTeleportSegment) {
                addCell(cells, to[0], to[1], width);
                skipTeleportSegment = false;
                continue;
            }
            if (from[0] != to[0] && from[1] != to[1]) {
                throw new IllegalArgumentException("Snake segment is not straight");
            }
            int dx = Integer.compare(to[0], from[0]);
            int dy = Integer.compare(to[1], from[1]);
            int x = from[0];
            int y = from[1];
            while (x != to[0] || y != to[1]) {
                x += dx;
                y += dy;
                addCell(cells, x, y, width);
            }
        }

        if (cells.size() > reportedLength) {
            throw new IllegalArgumentException("Expanded body exceeds reported snake length");
        }
        int[] body = new int[reportedLength];
        for (int i = 0; i < cells.size(); i++) body[i] = cells.get(i);
        int tail = cells.get(cells.size() - 1);
        for (int i = cells.size(); i < reportedLength; i++) body[i] = tail;
        int pendingGrowth = trailingTailCopies(body) - 1;
        int previousDirection = inferDirection(body, width, height);
        if (previousDirection == -1 && previous != null && previous.alive) {
            previousDirection = previous.previousDirection;
        }
        return new SnakeState(body, pendingGrowth, kills, true, previousDirection, -1, width);
    }

    static SnakeState atRespawn(int cell, int startLength, int kills, int width) {
        int[] body = new int[startLength];
        java.util.Arrays.fill(body, cell);
        return new SnakeState(body, startLength - 1, kills, true, 0, -1, width);
    }

    SnakeState move(int direction, int height, boolean ateApple, int growthPerFood) {
        if (!alive) throw new IllegalStateException("Cannot move a dead snake");
        if (direction < 0 || direction > 3) {
            throw new IllegalArgumentException("Direction must be between 0 and 3");
        }

        direction = resolvedDirection(direction);
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        int headX = xOf(body[0]);
        int headY = yOf(body[0]);
        int nextHead = Math.floorMod(headY + dy[direction], height) * width
                + Math.floorMod(headX + dx[direction], width);

        int extraLength = ateApple ? growthPerFood : 0;
        int nextLength = body.length + extraLength;
        int[] nextBody = new int[nextLength];
        nextBody[0] = nextHead;
        int retainedBodyLength = ateApple ? body.length : body.length - 1;
        System.arraycopy(body, 0, nextBody, 1, retainedBodyLength);
        if (ateApple) {
            int tail = body[body.length - 1];
            java.util.Arrays.fill(nextBody, body.length + 1, nextLength, tail);
        }
        return new SnakeState(
                nextBody, trailingTailCopies(nextBody) - 1, kills, true,
                direction, -1, width);
    }

    SnakeState withKills(int nextKills) {
        return new SnakeState(
                body, pendingGrowth, nextKills, alive, previousDirection, respawnIn, width);
    }

    SnakeState deadAfterTurn(int nextRespawnIn) {
        return new SnakeState(new int[0], 0, kills, false, -1, nextRespawnIn, width);
    }

    int resolvedDirection(int requestedDirection) {
        int facing = previousDirection < 0 ? 0 : previousDirection;
        int opposite = oppositeDirection(facing);
        return requestedDirection == opposite ? facing : requestedDirection;
    }

    int xOf(int cell) {
        return cell % width;
    }

    int yOf(int cell) {
        return cell / width;
    }

    private static int inferDirection(int[] body, int width, int height) {
        if (body.length < 2) return -1;
        int headX = body[0] % width;
        int headY = body[0] / width;
        int neckX = body[1] % width;
        int neckY = body[1] / width;
        int dx = Math.floorMod(headX - neckX, width);
        int dy = Math.floorMod(headY - neckY, height);
        if (dx == 0 && dy == height - 1) return 0;
        if (dx == 0 && dy == 1) return 1;
        if (dy == 0 && dx == width - 1) return 2;
        if (dy == 0 && dx == 1) return 3;
        return -1;
    }

    static int oppositeDirection(int direction) {
        return direction < 2 ? 1 - direction : 5 - direction;
    }

    static int trailingTailCopies(int[] body) {
        if (body.length == 0) return 0;
        int tail = body[body.length - 1];
        int copies = 1;
        for (int i = body.length - 2; i >= 0 && body[i] == tail; i--) copies++;
        return copies;
    }

    private static void addCell(List<Integer> cells, int x, int y, int width) {
        cells.add(y * width + x);
    }
}
