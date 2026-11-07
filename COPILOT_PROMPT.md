# MISSION

You are my autonomous engineer. Build, test and tune a Java snake that wins the snake league described in `docs/student-guide.md` and `docs/protocol.md`. Read both, plus `configs/league-arena.toml`, completely before writing code. You can run every command (`javac`, `python -m snake.cli ...`), so work as a loop: **implement, compile, test, measure, improve**. Do not stop at a first working version. Keep going until the acceptance criteria in section 12 are met, then report.

Build from scratch in a new folder `snake/` (not `my-snake/`). Do not copy the reference classes. You may read `my-snake/` for hints about the protocol only.

Hard rules about integrity: do not decompile, disassemble or otherwise inspect the compiled engine or the `gauntlet_*` opponents. Use only the public CLI and the docs. Explain your design in code comments so I can understand and defend it.

---

# 1. WHAT WINS (the objective)

- Ranking = **the longest length I ever reach in a game** (a high-water mark). Kills only break ties. Food, deaths and survival time are not scored directly.
- Respawns are on. Dying costs tempo (back to length 5 after 5 turns), not the game. The banked peak is never lost.
- The game runs the full 2000 turns on a 35x35 torus with 4 snakes and exactly 1 apple. Each apple gives +5 length.
- So the optimal policy is a **greedy grower**: eat as many apples as possible, as fast as possible, while avoiding the deaths that reset growth. Fighting is only worth it when it is a safe kill, or when it removes a competitor for the apple at no risk.
- The first life matters most (climbing from 5 to a high length takes most of a game). Late in the game, caution has less value (see 8.5).

---

# 2. HARD CONSTRAINTS (violating any of these loses games)

- Java 17, standard library only, plain `javac`, no flags. `Snake.java` is the entry point, with **no package declaration**, a public class `Snake` and `main(String[] args)`. Submit as `snake.zip` of the `.java` files (zip the files themselves, not a folder).
- **50 ms per move.** A late move repeats the old move and counts a failure. Three failures in a row means forfeit. Budget **at most ~30 ms of my own thinking** per turn, measured with `System.nanoTime()` from the moment the first line of the state arrives. Make the budget adaptive (see 9).
- stdout carries only the move (one integer 0-3 per turn, absolute directions only: 0 up, 1 down, 2 left, 3 right). **Flush after every line.** Debug output must be printed as `log <text>` and used sparingly (printing costs time). `System.err` is fine for diagnostics.
- Process is started once per game. Keep state in fields. Exit immediately on `Game Over` or EOF.
- Never crash, never loop forever: wrap `chooseMove` in `try/catch (Throwable)` and fall back to the best safe move computed by the cheap heuristic. A forfeit is permanent for that game.
- Do not use randomness unless it is seeded deterministically from the opening state (replays must be reproducible). Prefer fully deterministic tie-breaking.

---

# 3. INPUT PARSING AND BODY EXPANSION (get this exactly right, unit-test it)

Init line: `snakes width height mode` (always `4 35 35 1`). Each turn: apple `x y`, my index, then 4 snake lines `alive|dead length kills x,y x,y ...`. Ignore dead snakes. `(0,0)` is top-left, y grows downward.

**Expanding a chain into cells** (the head is the first point):

- Walk from point to point in a straight, **non-wrapping** line, adding every cell, without duplicating the corner cells.
- **If the same point appears twice in a row, that is a teleport**: the run ends there and the snake continues from the next listed point (no interpolation between the duplicate and the next point).
- A single-point chain is a one-cell snake. A full row is `0,10 34,10` (35 cells).

Algorithm sketch: `prev=null`; for each point `q`: if `prev==null` add `q`; else if `q==prev` set `prev=null` and continue (teleport); else add all cells from `prev` toward `q` excluding `prev`. Set `prev=q` after adding.

Required unit tests (write a `Tests` class run with `java`, not submitted in the zip, or keep it in the zip but not wired to `Snake`):

- `1,10 0,10 0,10 34,10 33,10` expands to `(1,10) (0,10) (34,10) (33,10)`: 4 cells.
- `34,10 34,10 0,10 1,10` expands to `(34,10) (0,10) (1,10)`: 3 cells.
- `0,10 34,10` expands to 35 cells.
- `12,13 12,14` expands to 2 cells with the head at `(12,13)`.
- A chain that turns corners, and one that teleports on both axes.

At runtime, compare `cells.size()` with the reported `length` for every snake and `log` mismatches once. This tells us how the engine reports length during growth, and we need to find out empirically (see section 4).

---

# 4. VERIFY THE RULES EMPIRICALLY BEFORE TRUSTING ASSUMPTIONS

The docs leave some details open. Write a small probe snake or log-analysis script and settle each one with real games (`python -m snake.cli play ...`, `watch`, and the log files), then record the findings in a `NOTES.md`:

1. After a snake eats, does the reported `length` jump by 5 immediately, or grow turn by turn as the tail stays put? (Compare `length` with expanded cell count over the next 6 turns.)
2. Is a cell occupied by an enemy **tail** that moves away this turn safe to enter? Is my own tail cell safe to enter when I am not growing? What if the snake just ate?
3. Is there an `events` or turn counter anywhere we can use? (Protocol says no, so **count turns myself** from the first state.)
4. Where does the apple respawn (uniform random on free cells?) and is it ever adjacent to a snake?
5. Exact head-to-head resolution when a head moves into a cell that another snake's tail leaves on the same turn.
6. How long respawn invulnerability or placement takes (I come back at length 5 at a safe cell after 5 turns).

Until verified, use these **conservative** assumptions:
- A snake's tail cell becomes free next turn only if that snake did not eat in the last 5 turns (track `lastAteTurn` per snake by detecting length increase or apple movement onto its head).
- Enemy tail cells are treated as blocked unless the owning snake provably is not growing; my own tail is considered free only if I am not growing.

---

# 5. CORE DATA STRUCTURES (speed matters: zero allocation in the search hot path)

- Cells are ints `id = y*35 + x`. Precompute `NEIGH[id][4]` (up, down, left, right, all wrapped) and a wrapped-distance table `DIST[a][b]` (or compute it with `min(dx,35-dx)+min(dy,35-dy)`).
- Per snake: circular int-buffer body (head to tail), length, alive, kills, `growPending`.
- `freeAt[cell]` ("time until free grid"): for a body cell at index `i` from the head (0 = head) of a snake of length `L` (not growing), the cell becomes free after `L - i` turns. Flood fills and BFS use it: a cell is enterable at step `t` if `freeAt[cell] <= t`. This is what makes tail-aware pathing correct on a crowded board.
- Reusable `int[]` queues and stamp-based visited arrays (no clearing, no `new` per call). Avoid streams, boxing, `String.split` in the hot path (parse by hand with a char scanner; `split` is acceptable for the 6 lines per turn if profiling shows it is cheap).
- Warm the JIT: in a daemon thread started at the top of `main`, run the search on synthetic random states for ~200-300 ms so the first real turns are not slow. Make sure the warm-up thread can never throw or touch stdout.

---

# 6. THE FORWARD SIMULATOR (the search depends on it being exact)

Implement `step(state, myMove, enemyMoves[])` with an undo stack (or cheap copies of the small arrays), following the engine rules exactly:

1. All snakes move one cell simultaneously (head advances; tail advances unless `growPending > 0`, in which case `growPending--`).
2. Apple: a head on the apple cell eats it (`growPending += 5`, apple removed; in search do **not** model respawn, just treat the apple as gone and give the eating a reward in evaluation).
3. Deaths: a head entering any body cell (including its own) dies; two heads in one cell: the strictly longer lives, equal length means both die. Reversing onto the neck is a collision.
4. A dead snake is removed from the board for the rest of the simulation (ignore respawn within search depth).

Test the simulator against the real engine: log some real consecutive observations and check that your predicted next state matches the actual one (heads, lengths, deaths).

---

# 7. DECISION PIPELINE (layered, always keeps a safe fallback)

**Layer 0: hard safety filter** (O(1), always runs first, always yields a move)
For each of the 4 moves compute the destination cell `d`. A move is **illegal** if `d` is occupied by a body cell that will not have vacated, or reverses onto my neck. A move is **deadly** if an enemy head is adjacent to `d` with `enemyLength >= myLength` (equal length kills both, and I count that as a death), or if every continuation from `d` is trapped. A move is a **kill opportunity** if an adjacent enemy head could only be in `d` and is strictly shorter than me.

**Layer 1: space check** (flood fill with wrap and `freeAt`)
For each non-illegal move, compute my reachable area after the move. Reject moves where reachable area < `myLength + margin` **unless** my tail is reachable (a path to my own tail cell, accounting for it moving, means I can always circle). This is the classic anti-trap rule and the thing that stops self-collision deaths, the most common death here. If every move fails, take the one with the largest area, then the most reachable-tail steps.

**Layer 2: Voronoi territory** (multi-source BFS from all snake heads, wrapped)
Compute for each cell who reaches it first (ties go to the longer snake, and equal length cancels). Count `myTerritory`, and the territory of each enemy. This is the main positional evaluation, and it also tells us who wins the race for the apple.

**Layer 3: time-boxed search** (iterative deepening)

- Plies are simultaneous rounds. At each ply I pick the max over my safe moves and the adversary picks the minimum (paranoid) over the joint moves of the **K nearest enemies** (start with K=2; each enemy limited to its legal non-suicidal moves, at most 3). The remaining enemies play a single predicted move from a cheap policy (head toward the apple if they are the closest, otherwise max-area move).
- Alpha-beta with move ordering (Layer 0/1 scores; killer moves; the previous iteration's best move first). Check the deadline every ~256 nodes. On timeout, discard the partial iteration and use the last completed depth's best move.
- Start at depth 1 (always completes), then 2, 3, ... until time runs out. Typical result: depth 4-8 in the early game, less in crowded fights.
- Paranoid search can be too timid. Parameterise a blend `value = a*min + (1-a)*mean` over enemy responses and tune `a` with the gauntlet (try 1.0, 0.8, 0.6).
- The final move is the search's best move **only if** it also passes Layer 0 and Layer 1. Otherwise use the best Layer 1 move. Always store a valid fallback move before starting any search.

**Leaf evaluation** (all weights live in one `Params` class so they can be tuned from a file or with environment variables during testing):

```
score = + W_TERR   * (myTerritory - maxEnemyTerritory)        // positional control
        + W_AREA   * min(myReachableArea, cap)                 // never trapped
        + W_APPLE  * proximity to apple, boosted when I win the Voronoi race for it
        + W_EAT    * (apples eaten along the line)              // big: +5 length each
        + W_LEN    * myLength
        - W_DANGER * (adjacent-to-longer-or-equal-enemy-head cells)
        - W_TAIL   * (no path to my own tail)
        + W_KILL   * (kills in the line)                        // small, safe kills only
        dead  => -1e6 + depth bonus (later death is less bad)
```

Starting guesses: `W_EAT=1000, W_TERR=4, W_AREA=2, W_APPLE=30, W_LEN=5, W_DANGER=200, W_TAIL=300, W_KILL=150`. **These are guesses; tuning them against the gauntlet is part of the job.**

---

# 8. STRATEGY RULES (the policy the search must respect)

1. **Apple race:** go for the apple when my wrapped path distance (using `freeAt`) is strictly smaller than every enemy's, or equal while I am strictly longer. If an enemy is closer or equal and longer, do not head-race it. Instead take the territory position that makes the contest safe, or let it eat and cut it off. Never enter a dead end for an apple: after eating, the area and tail checks must still pass with the +5 growth included (simulate the growth).
2. **Space discipline:** length is the hazard. As my length grows, keep the reachable area well above my length. When there is no safe apple, follow my own tail loop (it is always safe if the tail is reachable) while staying close to the apple's region.
3. **Avoid head-to-head with equal or longer enemies:** never step onto a cell adjacent to a longer-or-equal enemy head if I can avoid it; equal length is a mutual kill, which for me is a reset.
4. **Safe kills only:** take a kill if it is forced (the enemy has no escape from the cell I enter) and I am strictly longer, and my escape area afterwards is fine. Cutting an enemy off with my body is welcome when it also wins me territory.
5. **Endgame all-in:** the peak is banked. When fewer than ~150 turns remain (count my own turn counter) and my current length is my peak, caution is worth less, but a death still costs apples I could have eaten. Increase `W_EAT` and lower `W_DANGER` gradually, not abruptly. If I have just respawned and few turns remain, simply maximise apples with safe moves.
6. **After respawn:** I come back at length 5 at a safe cell. Play the same policy, but being short means enemy heads are mostly more dangerous (I lose head-to-heads), so give extra weight to `W_DANGER` while I am shorter than nearby enemies.
7. **Crowded board:** when territory is shrinking, prefer moves that keep two exits open and avoid splitting the free space into regions smaller than my length (the Voronoi and area terms handle this; make sure `cap` is not so low that it hides the difference).

---

# 9. TIME MANAGEMENT AND ROBUSTNESS

- Soft budget: 28 ms from receipt of the state line; hard stop 35 ms. Measure how long output and I/O take and subtract it.
- Adaptive: keep a rolling max of my recent decision times. If any turn exceeds 40 ms, reduce the budget by 10% (and restore slowly when it is stable). The first 3 turns use a smaller budget (JIT is cold).
- Garbage collection: no allocations per node. If `-Xlog:gc` (run it manually when testing) shows pauses above 5 ms, remove the allocation sources.
- Wrap everything in `try/catch Throwable`. On any exception print the cheapest safe move, log the exception to stderr once, and continue.
- If `readLine()` returns null or the line `Game Over`, return right away.
- Handle edge cases: length 1-2 snakes, a snake occupying a whole row/column, apple under an enemy (apple on a body cell can't happen, but do not crash), all four moves illegal (take the one with the highest `freeAt` pessimism, never output an invalid integer).

---

# 10. CODE STRUCTURE (zip these files; `Snake.java` is the entry point)

- `Snake.java`: main loop, I/O, time keeping, exception fallback.
- `Board.java`: constants, neighbour tables, wrapped distance.
- `GameState.java`: parsed observation, expanded bodies, `freeAt`, turn counter, growth tracking.
- `Sim.java`: forward simulator with undo.
- `Voronoi.java` / `Flood.java`: territory, reachable area, tail-reachability.
- `Search.java`: iterative deepening paranoid alpha-beta, move ordering.
- `Eval.java` + `Params.java`: evaluation and all tunable weights.
- `Tests.java`: expansion, simulator and flood-fill unit tests.

Use clear comments. Keep each class small and focused.

---

# 11. TEST, MEASURE, TUNE (the loop you must run)

Build and run exactly as the marker does:

```bash
rm -rf build && javac -d build snake/*.java
python -m snake.cli gauntlet "cmd:java -cp build Snake" --seeds 1..60
python -m snake.cli gauntlet "cmd:java -cp build Snake" --only 1 --seeds 1..60   # gauntlet_1 is the hardest
python -m snake.cli evaluate "cmd:java -cp build Snake" benchmark survivor hungry --seeds 1..50
```

Procedure:

1. **Milestone A (safe baseline):** parsing, expansion, Layer 0 + 1 + apple BFS. It must beat `benchmark` and the baselines, with **zero timeouts**.
2. **Milestone B:** add Voronoi evaluation and tail-aware flood fill. Re-measure.
3. **Milestone C:** add the iterative-deepening search. Re-measure, and verify the timing distribution (p99 decision time below 40 ms).
4. **Milestone D:** tune `Params` (coordinate descent or random search over seeds 1..100) and the paranoid blend. Keep a `RESULTS.md` table: version, change, mean peak length, deaths per game, timeouts, gauntlet placements.
5. **Regression discipline:** accept a change only if mean peak length over >= 100 seeds improves with no new timeouts. Keep the previous best build in `build_prev/` and play new against old (`cmd:` agents) as an extra check.
6. Add a **stress test**: run 30+ full games and confirm 0 crashes, 0 forfeits, no `log`/stdout pollution, and decision times logged (max, p99, mean).
7. Check the long-game behaviour (turns 1000-2000): look at a `watch` replay and fix any pattern where I trap myself or spiral on a crowded board.

Metrics to log per game in a test harness (parse the logs): peak length, final length, deaths, kills, apples eaten, max/p99 decision time, number of timeouts.

---

# 12. ACCEPTANCE CRITERIA (do not stop before all hold)

- Compiles with plain `javac` from clean, runs from `Snake` with no package, packages as `snake.zip`.
- 0 timeouts and 0 crashes across 60 gauntlet games; p99 decision time under 40 ms.
- Holds its own against **all three** gauntlet opponents (finishes above the middle of the table over seeds 1..60), and beats `benchmark` clearly (higher mean peak length).
- Mean peak length clearly higher than the reference-style heuristic (straight at the apple, avoid walls) and than my earlier milestones.
- Deaths per game are low and self-collisions are close to zero.
- `NOTES.md` records the empirically verified rules (section 4), `RESULTS.md` records the tuning history, and `snake.zip` is built and checked by unzipping and compiling into a temp folder.

When finished, give me: a short summary of the algorithm, the final numbers, the known weaknesses, and the exact zip command used.
