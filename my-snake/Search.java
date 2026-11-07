import java.util.Arrays;

class Search {
    static int reachableArea(int[] board, int width, int height, int startX, int startY) {
        validateBoard(board, width, height);
        if (startX < 0 || startX >= width || startY < 0 || startY >= height) {
            throw new IllegalArgumentException("Start must be on the board");
        }

        int start = startY * width + startX;
        int[] queue = new int[width * height];
        boolean[] visited = new boolean[width * height];
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        visited[start] = true;

        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        while (head < tail) {
            int current = queue[head++];
            int x = current % width;
            int y = current / width;

            for (int direction = 0; direction < 4; direction++) {
                int nextX = Math.floorMod(x + dx[direction], width);
                int nextY = Math.floorMod(y + dy[direction], height);
                int next = nextY * width + nextX;
                if (visited[next] || board[next] != GameState.EMPTY) continue;

                visited[next] = true;
                queue[tail++] = next;
            }
        }
        return tail;
    }

    static SearchResult breadthFirstSearch(
            int[] board, int width, int height, int startX, int startY, int targetX, int targetY) {
        validateBoard(board, width, height);
        if (startX < 0 || startX >= width || targetX < 0 || targetX >= width
                || startY < 0 || startY >= height || targetY < 0 || targetY >= height) {
            throw new IllegalArgumentException("Start and target must be on the board");
        }

        int[][] distances = new int[height][width];
        for (int[] row : distances) Arrays.fill(row, -1);

        int start = startY * width + startX;
        int target = targetY * width + targetX;

        int cellCount = width * height;
        int[] queue = new int[cellCount];
        int[] parent = new int[cellCount];
        int[] directionFromParent = new int[cellCount];
        Arrays.fill(parent, -1);
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        distances[startY][startX] = 0;

        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        while (head < tail) {
            int current = queue[head++];
            int x = current % width;
            int y = current / width;

            for (int direction = 0; direction < 4; direction++) {
                int nextX = Math.floorMod(x + dx[direction], width);
                int nextY = Math.floorMod(y + dy[direction], height);
                int next = nextY * width + nextX;
                if (distances[nextY][nextX] != -1 || board[next] != GameState.EMPTY) {
                    continue;
                }

                distances[nextY][nextX] = distances[y][x] + 1;
                parent[next] = current;
                directionFromParent[next] = direction;
                queue[tail++] = next;
            }
        }

        int distance = distances[targetY][targetX];
        if (distance == -1) {
            return new SearchResult(-1, new int[0], distances);
        }

        int[] path = new int[distance];
        int current = target;
        for (int i = distance - 1; i >= 0; i--) {
            path[i] = directionFromParent[current];
            current = parent[current];
        }
        return new SearchResult(distance, path, distances);
    }

    private static void validateBoard(int[] board, int width, int height) {
        if (width <= 0 || height <= 0 || board == null || board.length != width * height) {
            throw new IllegalArgumentException("Board dimensions must match its cells");
        }
    }

    static final class SearchResult {
        final int distance;
        final int[] path;
        final int[][] distances;

        SearchResult(int distance, int[] path, int[][] distances) {
            this.distance = distance;
            this.path = path;
            this.distances = distances;
        }
    }
}