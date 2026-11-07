import java.io.*;

public class Snake {
    static GameState previousState;
    static int currentTurn;

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        // ---- initialisation: "<snakes> <width> <height> <mode>", once -------
        String[] init = in.readLine().trim().split("\\s+");
        int snakeCount = Integer.parseInt(init[0]);
        int width = Integer.parseInt(init[1]);
        int height = Integer.parseInt(init[2]);
        //int mode = Integer.parseInt(init[3]);

        while (true) {
            // ---- one game state ---------------------------------------------
            String first = in.readLine();
            if (first == null) return;                 // the game went away
            first = first.trim();
            if (first.equals("Game Over")) return;     // you have ~500ms here
            if (first.isEmpty()) continue;
            // Line 1: the apple, "x y".
            String[] apple = first.split("\\s+");
            int appleX = Integer.parseInt(apple[0]);
            int appleY = Integer.parseInt(apple[1]);
            // Line 2: which of the snakes is you.
            int me = Integer.parseInt(in.readLine().trim());
            // Then one line per snake, in index order.
            String[] snakes = new String[snakeCount];
            for (int i = 0; i < snakeCount; i++) snakes[i] = in.readLine();

            System.out.println(chooseMove(width, height, me, appleX, appleY, snakes));
            System.out.flush();
        }
    }
//----------------------------DECISION TIME----------------------------//

    static String chooseMove(int width, int height, int me, int appleX, int appleY, String[] snakes) {
        GameState state = GameState.fromObservation(
                width, height, me, appleX, appleY, snakes, currentTurn++, previousState);
        previousState = state;
        
        SnakeState mine = state.snakes[state.ourSnakeIndex];
        if (!mine.alive) return "0";
        return Integer.toString(RoutePlanner.chooseMove(state));
    }

    /** Anything you want to see later. It goes to my-snake.log, not to the game. */
    static void log(String message) {
        System.out.println("log " + message);
        System.out.flush();
    }
}
