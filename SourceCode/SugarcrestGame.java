import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SugarcrestGame extends Pane {

    // ── dimensions ────────────────────────────────────────────────────────────
    private static final int WIDTH     = 1310;
    private static final int HEIGHT    = 730;
    private static final int ROWS      = 8;
    private static final int COLS      = 8;
    private static final int CELL_SIZE = 60;

    private final int gridX = (WIDTH - COLS * CELL_SIZE) / 2;
    private final int gridY = 180;

    // ── references ────────────────────────────────────────────────────────────
    private final Stage       parentStage;
    private final WorldScreen previousScreen;
    private final Random      rand = new Random();
    private final Canvas      canvas;

    // ── assets ────────────────────────────────────────────────────────────────
    private Image   scImage;
    private Image   scWinImage;
    private Image   scLoseImage;
    private Image[] candyImages = new Image[6];   // 0-4 = colours, 5 = donut
    private Image   hurricaneImage;

    // ── sounds ────────────────────────────────────────────────────────────────
    private AudioClip instructionsClip, winClip, loseClip, splashClip, windClip, hurricaneClip;

    // ── game state ────────────────────────────────────────────────────────────
    private enum GameState { INSTRUCTIONS, PLAYING, WIN, LOSE }
    private GameState state = GameState.INSTRUCTIONS;

    private int[][]     grid     = new int[ROWS][COLS];
    private boolean[][] toRemove = new boolean[ROWS][COLS];

    private final int[] required  = {20, 20, 20, 20, 20};
    private int[]       collected = new int[5];
    private boolean     donutSpawned = false;
    private int         matchesMade  = 0;

    // ── timing ────────────────────────────────────────────────────────────────
    private long gameStartTime;
    private long pausedTime = 0;      // total ms spent in donut-effect pause
    private long donutPauseStart = 0;
    private int  timeLeft = 60;

    // ── disaster ──────────────────────────────────────────────────────────────
    private boolean disasterTriggered = false;
    private String  disasterType      = "";
    private int     disasterCountdown = 0;
    private long    countdownStart    = 0;

    private boolean floodActive   = false;
    private double  waterLevel    = 0;
    private long    lastFloodTick = 0;
    private static final double FLOOD_SPEED_PPS = 18.0;

    private boolean      hurricaneActive   = false;
    private double       hurricaneXd       = -300;
    private long         nextHurricaneTime = 0;
    private long         lastHurricaneTick = 0;
    private Set<Integer> hitByHurricane    = new HashSet<>();
    private static final int    HURR_W         = 320;
    private static final int    HURR_H         = 240;
    private static final double HURR_SPEED_PPS = 340.0;

    // ── Hurricane swept-candy animation ──────────────────────────────────────
    // Each entry: [candyType, startX, startY, velocityX, velocityY, alpha]
    private List<double[]> sweptCandies = new ArrayList<>();

    // ── swap animation ────────────────────────────────────────────────────────
    private enum SwapPhase { NONE, FORWARD, REVERSE }
    private SwapPhase swapPhase    = SwapPhase.NONE;
    private int       swapR1, swapC1, swapR2, swapC2;
    private float     swapProgress = 0f;
    private static final float SWAP_SPEED = 0.06f;  // slower = smoother
    private boolean swapWasValid = false;

    // ── DONUT sparkle-line effect ─────────────────────────────────────────────
    private boolean donutEffectActive = false;
    private int     donutFromR, donutFromC;
    private int     donutTargetColor = -1;
    private List<int[]> donutTargetCells = new ArrayList<>();
    private double  donutEffectTimer  = 0;
    private static final double DONUT_EFFECT_DURATION = 0.9;
    private boolean pendingDonutClear = false;
    private int     pendingDonutR1, pendingDonutC1, pendingDonutR2, pendingDonutC2;

    // ── instructions typing ───────────────────────────────────────────────────
    private final String[] instructionLines = {
        "You are stuck in a sugar loop...",
        "the only way to win is to collect candies",
        "but beware! there's a storm coming..."
    };
    private int[]   typedLengths      = new int[3];
    private int     currentTypingLine = 0;
    private boolean typingFinished    = false;
    private boolean outroStarted      = false;
    private float   textAlpha         = 1.0f;
    private long    lastTypingTick    = 0;

    // ── UI buttons ────────────────────────────────────────────────────────────
    private final double playRectX = (WIDTH - 220) / 2.0, playRectY = 480,
                         playRectW = 220, playRectH = 70;
    private final double backRectX = 30, backRectY = 30, backRectW = 140, backRectH = 40;
    private boolean hoveredPlay = false, hoveredBack = false;
    private int triOffsetPlay = 0, triOffsetBack = 0;

    // ── drag state ────────────────────────────────────────────────────────────
    private double  pressMouseX, pressMouseY;
    private int     pressRow = -1, pressCol = -1;
    private boolean pressing = false;

    // ── main loop ─────────────────────────────────────────────────────────────
    private AnimationTimer mainTimer;
    private long lastFrameNano = 0;
    private double frameDt = 0;

    private static final String[] COLOR_NAMES = {"YELLOW","RED","GREEN","ORANGE","PURPLE"};

    // ── fallback colours ──────────────────────────────────────────────────────
    private static final Color[] CANDY_COLORS = {
        Color.YELLOW, Color.RED, Color.LIMEGREEN,
        Color.ORANGE, Color.PURPLE, Color.HOTPINK
    };

    // ── Bubbly/funky font name (loaded or fallback) ───────────────────────────
    private static final String FUNKY_FONT = "Comic Sans MS";  // bubbly feel; widely available

    // ──────────────────────────────────────────────────────────────────────────
    public SugarcrestGame(Stage stage, WorldScreen prev) {
        this.parentStage    = stage;
        this.previousScreen = prev;

        canvas = new Canvas(WIDTH, HEIGHT);
        getChildren().add(canvas);

        loadAssets();
        initGrid();
        hookInput();
        startLoop();
        startInstructions();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ASSET LOADING
    // ══════════════════════════════════════════════════════════════════════════
    private void loadAssets() {
        scImage    = safeImg("Assets/sc.png");
        scWinImage = safeImg("Assets/scwin.png");
        if (scWinImage == null)  scWinImage  = safeImg("Assets/sc/scwin.png");
        scLoseImage = safeImg("Assets/sclose.png");
        if (scLoseImage == null) scLoseImage = safeImg("Assets/sc/sclose.png");

        hurricaneImage = safeImg("Assets/sc/hurricane.png");
        if (hurricaneImage == null) hurricaneImage = safeImg("Assets/hurricane.png");

        // Candy images — try multiple paths for each; orange is index 3
        String[] names = {"yellow", "red", "green", "orange", "purple", "donut"};
        String[] altPaths = {
            "Assets/",
            "Assets/sc/",
            "assets/",
            "assets/sc/"
        };
        for (int i = 0; i < 6; i++) {
            Image img = null;
            for (String base : altPaths) {
                img = safeImg(base + names[i] + ".png");
                if (img != null) break;
                img = safeImg(base + names[i].toUpperCase() + ".png");
                if (img != null) break;
                img = safeImg(base + Character.toUpperCase(names[i].charAt(0)) + names[i].substring(1) + ".png");
                if (img != null) break;
            }
            candyImages[i] = img;
            if (img == null) System.out.println("Could not load candy: " + names[i]);
            else System.out.println("Loaded candy: " + names[i]);
        }

        instructionsClip = safeClip("Assets/sc/instructions.wav");
        winClip          = safeClip("Assets/sc/win.wav");
        loseClip         = safeClip("Assets/sc/lose.wav");
        splashClip       = safeClip("Assets/sc/splash.wav");
        windClip         = safeClip("Assets/sc/wind.wav");
        hurricaneClip    = safeClip("Assets/sc/hurricane.wav");
    }

    private Image safeImg(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        try { return new Image(f.toURI().toString()); }
        catch (Exception e) { System.out.println("Failed to load image: " + path); return null; }
    }

    private AudioClip safeClip(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        try { return new AudioClip(f.toURI().toString()); }
        catch (Exception e) { return null; }
    }

    private void playClip(AudioClip c) { if (c != null) c.play(); }
    private void stopClip(AudioClip c) { if (c != null) c.stop(); }

    // ══════════════════════════════════════════════════════════════════════════
    // INSTRUCTIONS
    // ══════════════════════════════════════════════════════════════════════════
    private void startInstructions() {
        playClip(instructionsClip);
        currentTypingLine = 0;
        typedLengths      = new int[3];
        typingFinished    = false;
        outroStarted      = false;
        textAlpha         = 1.0f;
        lastTypingTick    = System.currentTimeMillis();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GRID HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void initGrid() {
        do {
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    grid[r][c] = rand.nextInt(5);
        } while (hasAnyMatch());
    }

    /** Strict: checks all horizontal and vertical runs of 3+ anywhere on the board */
    private boolean hasAnyMatch() {
        // Horizontal
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS - 2; c++) {
                int t = grid[r][c];
                if (t >= 0 && t < 5 && t == grid[r][c+1] && t == grid[r][c+2]) return true;
            }
        }
        // Vertical
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS - 2; r++) {
                int t = grid[r][c];
                if (t >= 0 && t < 5 && t == grid[r+1][c] && t == grid[r+2][c]) return true;
            }
        }
        return false;
    }

    private void swapCandies(int r1, int c1, int r2, int c2) {
        int tmp = grid[r1][c1]; grid[r1][c1] = grid[r2][c2]; grid[r2][c2] = tmp;
    }

    private void dropCandies() {
        for (int c = 0; c < COLS; c++) {
            int write = ROWS - 1;
            for (int r = ROWS - 1; r >= 0; r--)
                if (grid[r][c] != -1) {
                    grid[write][c] = grid[r][c];
                    if (write != r) grid[r][c] = -1;
                    write--;
                }
        }
    }

    private void fillTop() {
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++)
                if (grid[r][c] == -1) {
                    if (!donutSpawned && matchesMade >= 5 && rand.nextInt(8) == 0)
                        { grid[r][c] = 5; donutSpawned = true; }
                    else
                        grid[r][c] = rand.nextInt(5);
                }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MATCH PROCESSING — strict full-board cascade
    // ══════════════════════════════════════════════════════════════════════════
    private void collect(int colorIdx) {
        if (colorIdx >= 0 && colorIdx < 5)
            collected[colorIdx] = Math.min(collected[colorIdx] + 1, required[colorIdx]);
    }

    /**
     * Full strict board scan: mark ALL horizontal and vertical runs of 3+,
     * remove them, drop, fill, then repeat until no matches remain.
     */
    private boolean processMatches() {
        boolean anyMatched = false;
        boolean matched;
        do {
            matched = false;
            for (int i = 0; i < ROWS; i++) for (int j = 0; j < COLS; j++) toRemove[i][j] = false;

            // Horizontal runs
            for (int r = 0; r < ROWS; r++) {
                int start = 0;
                while (start < COLS) {
                    int t = grid[r][start];
                    if (t < 0 || t >= 5) { start++; continue; }
                    int end = start + 1;
                    while (end < COLS && grid[r][end] == t) end++;
                    if (end - start >= 3) {
                        for (int k = start; k < end; k++) toRemove[r][k] = true;
                        matched = true;
                    }
                    start = end;
                }
            }
            // Vertical runs
            for (int c = 0; c < COLS; c++) {
                int start = 0;
                while (start < ROWS) {
                    int t = grid[start][c];
                    if (t < 0 || t >= 5) { start++; continue; }
                    int end = start + 1;
                    while (end < ROWS && grid[end][c] == t) end++;
                    if (end - start >= 3) {
                        for (int k = start; k < end; k++) toRemove[k][c] = true;
                        matched = true;
                    }
                    start = end;
                }
            }

            if (matched) {
                anyMatched = true;
                for (int r = 0; r < ROWS; r++)
                    for (int c = 0; c < COLS; c++)
                        if (toRemove[r][c]) {
                            collect(grid[r][c]);
                            grid[r][c] = -1;
                            matchesMade++;
                        }
                dropCandies();
                fillTop();
                checkWin();
            }
        } while (matched);

        return anyMatched;
    }

    private boolean wouldMatchAfterSwap(int r1, int c1, int r2, int c2) {
        swapCandies(r1, c1, r2, c2);
        boolean result = hasAnyMatch();
        swapCandies(r1, c1, r2, c2);
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DONUT LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    private boolean tryHandleDonutSwap(int r1, int c1, int r2, int c2) {
        int t1 = grid[r1][c1], t2 = grid[r2][c2];
        boolean d1 = (t1 == 5), d2 = (t2 == 5);
        if (!d1 && !d2) return false;

        int targetColor;
        int donutR, donutC;
        if (d1) {
            targetColor = t2; donutR = r1; donutC = c1;
        } else {
            targetColor = t1; donutR = r2; donutC = c2;
        }
        if (targetColor < 0 || targetColor >= 5) return false;

        donutTargetCells.clear();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (grid[r][c] == targetColor)
                    donutTargetCells.add(new int[]{r, c});

        donutFromR       = donutR;
        donutFromC       = donutC;
        donutTargetColor = targetColor;

        donutEffectActive = true;
        donutEffectTimer  = 0;
        donutPauseStart   = System.currentTimeMillis();
        pendingDonutClear = true;
        pendingDonutR1    = r1; pendingDonutC1 = c1;
        pendingDonutR2    = r2; pendingDonutC2 = c2;

        swapCandies(r1, c1, r2, c2);
        return true;
    }

    private void resolveDonutClear() {
        if (!pendingDonutClear) return;
        pendingDonutClear = false;

        // Accumulate the time we paused the game clock
        pausedTime += System.currentTimeMillis() - donutPauseStart;

        for (int[] cell : donutTargetCells) {
            int r = cell[0], c = cell[1];
            if (grid[r][c] == donutTargetColor) {
                collect(donutTargetColor);
                grid[r][c] = -1;
                matchesMade++;
            }
        }
        grid[pendingDonutR1][pendingDonutC1] = -1;
        grid[pendingDonutR2][pendingDonutC2] = -1;
        donutSpawned = false;

        dropCandies();
        fillTop();
        processMatches();  // cascade after donut clear
        checkWin();

        donutEffectActive = false;
        donutTargetCells.clear();
    }

    private void checkWin() {
        for (int i = 0; i < 5; i++) if (collected[i] < required[i]) return;
        state = GameState.WIN;
        mainTimer.stop();
        playClip(winClip);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SWAP ANIMATION
    // ══════════════════════════════════════════════════════════════════════════
    private void beginSwapAnimation(int r1, int c1, int r2, int c2) {
        swapR1 = r1; swapC1 = c1; swapR2 = r2; swapC2 = c2;
        swapProgress = 0f;
        swapPhase    = SwapPhase.FORWARD;

        int t1 = grid[r1][c1], t2 = grid[r2][c2];
        boolean isDonut = (t1 == 5 || t2 == 5);

        if (isDonut) {
            swapWasValid = true;
            // Don't swap the grid yet — wait for forward animation to finish
        } else {
            // Test validity WITHOUT modifying the grid yet
            swapWasValid = wouldMatchAfterSwap(r1, c1, r2, c2);
            // Now physically swap for the animation to render
            swapCandies(r1, c1, r2, c2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DISASTER
    // ══════════════════════════════════════════════════════════════════════════
    private void triggerDisaster() {
        if (disasterType.equals("FLOOD")) {
            floodActive = true; waterLevel = 0;
            lastFloodTick = System.currentTimeMillis();
            playClip(splashClip);
        } else {
            spawnHurricane();
            nextHurricaneTime = System.currentTimeMillis() + 10000;
        }
    }

    private void spawnHurricane() {
        hurricaneActive = true; hurricaneXd = -HURR_W;
        lastHurricaneTick = System.currentTimeMillis();
        hitByHurricane.clear();
        playClip(hurricaneClip);
    }

    private boolean isCovered(int row) {
        if (!floodActive) return false;
        int cellBottom = gridY + (row + 1) * CELL_SIZE;
        int waterTop   = gridY + ROWS * CELL_SIZE - (int) waterLevel;
        return cellBottom > waterTop;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TICK — main game logic
    // ══════════════════════════════════════════════════════════════════════════
    private void tick(double dt) {
        long now = System.currentTimeMillis();

        // Typing animation
        if (!typingFinished && now - lastTypingTick >= 40) {
            lastTypingTick = now;
            if (currentTypingLine < 3) {
                if (typedLengths[currentTypingLine] < instructionLines[currentTypingLine].length())
                    typedLengths[currentTypingLine]++;
                else currentTypingLine++;
            } else {
                typingFinished = true;
                outroStarted   = true;
            }
        }

        if (state == GameState.INSTRUCTIONS && outroStarted)
            textAlpha = Math.max(0f, textAlpha - 0.02f);

        if (state != GameState.PLAYING) return;

        // Timer excludes time paused for donut animation
        int elapsed = (int)((now - gameStartTime - pausedTime) / 1000);
        timeLeft = Math.max(0, 60 - elapsed);

        // ── Donut sparkle animation tick ──────────────────────────────────
        if (donutEffectActive) {
            donutEffectTimer += dt;
            if (donutEffectTimer >= DONUT_EFFECT_DURATION) {
                resolveDonutClear();
            }
            // Still update swept candy particles
            updateSweptCandies(dt);
            return;
        }

        if (timeLeft <= 0) {
            boolean won = true;
            for (int i = 0; i < 5; i++) if (collected[i] < required[i]) { won = false; break; }
            state = won ? GameState.WIN : GameState.LOSE;
            mainTimer.stop();
            floodActive = false;
            playClip(state == GameState.WIN ? winClip : loseClip);
            return;
        }

        // Swap animation
        if (swapPhase != SwapPhase.NONE) {
            swapProgress += SWAP_SPEED;
            if (swapProgress >= 1f) {
                swapProgress = 1f;
                if (swapPhase == SwapPhase.FORWARD) {
                    if (swapWasValid) {
                        swapPhase = SwapPhase.NONE;
                        int t1 = grid[swapR1][swapC1], t2 = grid[swapR2][swapC2];
                        boolean isDonut = (t1 == 5 || t2 == 5);
                        if (isDonut) {
                            tryHandleDonutSwap(swapR1, swapC1, swapR2, swapC2);
                        } else {
                            processMatches();
                        }
                    } else {
                        // Invalid swap: show briefly then reverse
                        swapPhase = SwapPhase.REVERSE;
                        swapProgress = 0f;
                    }
                } else {
                    // Reverse complete: undo the grid swap
                    swapCandies(swapR1, swapC1, swapR2, swapC2);
                    swapPhase = SwapPhase.NONE;
                }
            }
            updateSweptCandies(dt);
            return;
        }

        // Disaster trigger at 20 s
        if (!disasterTriggered && elapsed >= 20) {
            disasterTriggered = true;
            disasterType      = rand.nextBoolean() ? "HURRICANE" : "FLOOD";
            disasterCountdown = 3;
            countdownStart    = now;
            playClip(windClip);
        }

        if (disasterCountdown > 0 && (now - countdownStart) >= 1000) {
            disasterCountdown--; countdownStart = now;
            if (disasterCountdown == 0) triggerDisaster();
        }

        if (floodActive) {
            long delta = now - lastFloodTick; lastFloodTick = now;
            waterLevel = Math.min(waterLevel + FLOOD_SPEED_PPS * (delta / 1000.0), ROWS * CELL_SIZE);
        }

        if (hurricaneActive) {
            long delta = now - lastHurricaneTick; lastHurricaneTick = now;
            hurricaneXd += HURR_SPEED_PPS * (delta / 1000.0);
            if (hurricaneXd > WIDTH + HURR_W) hurricaneActive = false;
            else checkHurricaneCollisions();
        }

        if (disasterType.equals("HURRICANE") && nextHurricaneTime > 0
                && now > nextHurricaneTime && !hurricaneActive) {
            spawnHurricane(); nextHurricaneTime = now + 10000;
        }

        updateSweptCandies(dt);
    }

    // ── Update swept candy particles ──────────────────────────────────────────
    private void updateSweptCandies(double dt) {
        sweptCandies.removeIf(p -> {
            p[1] += p[3] * dt;   // x += vx * dt
            p[2] += p[4] * dt;   // y += vy * dt
            p[4] += 300 * dt;    // gravity
            p[5] -= 1.5 * dt;    // fade alpha
            return p[5] <= 0 || p[1] > WIDTH + 100 || p[2] > HEIGHT + 100;
        });
    }

    private void checkHurricaneCollisions() {
        int hx = (int) hurricaneXd;
        int hy = gridY + ROWS * CELL_SIZE / 2 - HURR_H / 2;

        // Hurricane centre for velocity calculation
        double hcx = hx + HURR_W / 2.0;
        double hcy = hy + HURR_H / 2.0;

        boolean anyHit = false;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (grid[r][c] == -1) continue;
            int key = r * COLS + c;
            if (hitByHurricane.contains(key)) continue;
            int cx = gridX + c * CELL_SIZE, cy = gridY + r * CELL_SIZE;
            if (hx < cx+CELL_SIZE && hx+HURR_W > cx && hy < cy+CELL_SIZE && hy+HURR_H > cy) {
                int candyType = grid[r][c];
                collect(candyType);
                grid[r][c] = -1;
                matchesMade++;
                hitByHurricane.add(key);
                anyHit = true;

                // Launch swept candy particle away with hurricane spin
                double candyCx = cx + CELL_SIZE / 2.0;
                double candyCy = cy + CELL_SIZE / 2.0;
                double dx = candyCx - hcx;
                double dy = candyCy - hcy;
                double len = Math.max(1, Math.sqrt(dx*dx + dy*dy));
                // Velocity: hurricane direction (rightward) + outward spin
                double vx = HURR_SPEED_PPS * 0.6 + (dx / len) * 120 + (rand.nextDouble() - 0.5) * 80;
                double vy = (dy / len) * 120 - 80 + (rand.nextDouble() - 0.5) * 60;
                // [candyType, x, y, vx, vy, alpha]
                sweptCandies.add(new double[]{candyType, candyCx, candyCy, vx, vy, 1.0});
            }
        }
        if (anyHit) { dropCandies(); fillTop(); processMatches(); checkWin(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INPUT
    // ══════════════════════════════════════════════════════════════════════════
    private void hookInput() {
        canvas.setOnMouseMoved(e -> {
            hoveredPlay = (state == GameState.INSTRUCTIONS && typingFinished
                && inRect(e.getX(), e.getY(), (WIDTH - 220) / 2.0, 480, 220, 70));
            hoveredBack = ((state == GameState.WIN || state == GameState.LOSE)
                && inRect(e.getX(), e.getY(), backRectX, backRectY, backRectW, backRectH));
            canvas.setCursor(hoveredPlay || hoveredBack
                ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        });

        canvas.setOnMousePressed(e -> {
            if (state != GameState.PLAYING || swapPhase != SwapPhase.NONE
                    || donutEffectActive) return;
            int c = (int)((e.getX() - gridX) / CELL_SIZE);
            int r = (int)((e.getY() - gridY) / CELL_SIZE);
            if (c >= 0 && c < COLS && r >= 0 && r < ROWS
                    && grid[r][c] != -1 && !isCovered(r)) {
                pressMouseX = e.getX(); pressMouseY = e.getY();
                pressRow = r; pressCol = c; pressing = true;
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (!pressing || state != GameState.PLAYING
                    || swapPhase != SwapPhase.NONE || donutEffectActive) return;
            pressing = false;
            double dx = e.getX() - pressMouseX, dy = e.getY() - pressMouseY;
            int tr = pressRow, tc = pressCol;
            if (Math.abs(dx) > Math.abs(dy)) { if (dx > 20) tc++; else if (dx < -20) tc--; }
            else                             { if (dy > 20) tr++; else if (dy < -20) tr--; }
            if ((tr != pressRow || tc != pressCol)
                    && tr >= 0 && tr < ROWS && tc >= 0 && tc < COLS
                    && !isCovered(tr) && grid[pressRow][pressCol] != -1) {
                beginSwapAnimation(pressRow, pressCol, tr, tc);
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (state == GameState.INSTRUCTIONS && typingFinished
                    && inRect(e.getX(), e.getY(), (WIDTH - 220) / 2.0, 480, 220, 70)) {
                stopClip(instructionsClip);
                startPlaying();
            } else if ((state == GameState.WIN || state == GameState.LOSE)
                    && inRect(e.getX(), e.getY(), backRectX, backRectY, backRectW, backRectH)) {
                mainTimer.stop();
                previousScreen.returnToWorld();
            }
        });
    }

    private boolean inRect(double mx, double my, double bx, double by, double bw, double bh) {
        return mx >= bx && mx <= bx+bw && my >= by && my <= by+bh;
    }

    private void startPlaying() {
        state = GameState.PLAYING;
        gameStartTime = System.currentTimeMillis();
        pausedTime = 0;
        timeLeft = 60; disasterTriggered = false; disasterType = "";
        disasterCountdown = 0; floodActive = false; waterLevel = 0;
        hurricaneActive = false; hurricaneXd = -HURR_W; nextHurricaneTime = 0;
        collected = new int[5]; donutSpawned = false; matchesMade = 0;
        swapPhase = SwapPhase.NONE; hitByHurricane.clear();
        donutEffectActive = false; pendingDonutClear = false;
        donutTargetCells.clear(); sweptCandies.clear();
        initGrid();
        mainTimer.stop();
        mainTimer.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GAME LOOP
    // ══════════════════════════════════════════════════════════════════════════
    private void startLoop() {
        mainTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastFrameNano == 0) { lastFrameNano = now; }
                frameDt = Math.min((now - lastFrameNano) / 1_000_000_000.0, 0.05);
                lastFrameNano = now;
                tick(frameDt);
                render();
            }
        };
        mainTimer.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERING
    // ══════════════════════════════════════════════════════════════════════════
    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        switch (state) {
            case INSTRUCTIONS -> drawInstructions(gc);
            case PLAYING      -> drawPlaying(gc);
            default           -> drawEndScreen(gc);
        }
    }

    // ── Smooth swap interpolation (cubic ease-in-out) ─────────────────────────
    private double[] candyDrawPos(int r, int c) {
        double baseX = gridX + c * CELL_SIZE + 5;
        double baseY = gridY + r * CELL_SIZE + 5;
        if (swapPhase == SwapPhase.NONE) return new double[]{baseX, baseY};

        float t = swapProgress;
        // Cubic ease-in-out for much smoother feel
        t = t < 0.5f ? 4f*t*t*t : 1f - (float)Math.pow(-2f*t+2f, 3)/2f;

        double x1 = gridX + swapC1 * CELL_SIZE + 5, y1 = gridY + swapR1 * CELL_SIZE + 5;
        double x2 = gridX + swapC2 * CELL_SIZE + 5, y2 = gridY + swapR2 * CELL_SIZE + 5;

        if (r == swapR1 && c == swapC1) {
            if (swapPhase == SwapPhase.FORWARD)
                return new double[]{x1 + t*(x2-x1), y1 + t*(y2-y1)};
            else
                return new double[]{x2 + t*(x1-x2), y2 + t*(y1-y2)};
        } else if (r == swapR2 && c == swapC2) {
            if (swapPhase == SwapPhase.FORWARD)
                return new double[]{x2 + t*(x1-x2), y2 + t*(y1-y2)};
            else
                return new double[]{x1 + t*(x2-x1), y1 + t*(y2-y1)};
        }
        return new double[]{baseX, baseY};
    }

    // ── Instructions screen ───────────────────────────────────────────────────
    private void drawInstructions(GraphicsContext gc) {
        if (scImage != null) gc.drawImage(scImage, 0, 0, WIDTH, HEIGHT);
        else { gc.setFill(Color.rgb(255,220,180)); gc.fillRect(0,0,WIDTH,HEIGHT); }

        // ── Semi-transparent backdrop for the story text ──────────────────
        double boxX = (WIDTH - 680) / 2.0;
        double boxY = 210;
        double boxW = 680;
        double boxH = 190;
        double boxAlpha = outroStarted ? Math.max(0, textAlpha * 0.72) : 0.72;

        gc.save();
        gc.setFill(Color.rgb(20, 10, 40, boxAlpha));
        roundRect(gc, boxX, boxY, boxW, boxH, 22);
        gc.fill();
        // Soft pink border glow
        gc.setStroke(Color.rgb(255, 170, 220, Math.min(boxAlpha + 0.1, 1.0)));
        gc.setLineWidth(2.5);
        roundRect(gc, boxX, boxY, boxW, boxH, 22);
        gc.stroke();
        gc.restore();

        gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 28));
        for (int i = 0; i < 3; i++) {
            if (typedLengths[i] == 0) continue;
            String txt = instructionLines[i].substring(0, typedLengths[i]);
            double alpha = outroStarted ? textAlpha : 1.0;
            // Drop shadow
            gc.setFill(Color.rgb(0, 0, 0, alpha * 0.6));
            gc.fillText(txt, (WIDTH - textW(txt, 28)) / 2 + 2, 258 + i * 52 + 2);
            gc.setFill(Color.rgb(255, 240, 255, alpha));
            gc.fillText(txt, (WIDTH - textW(txt, 28)) / 2, 258 + i * 52);
        }

        if (typingFinished) {
            // ── PLAY button — fully centred, whole button bobs on hover ───
            // Smooth vertical bob: triOffsetPlay counts 0→10 on hover
            triOffsetPlay = hoveredPlay ? Math.min(triOffsetPlay + 2, 10) : Math.max(triOffsetPlay - 2, 0);
            double bobY = -triOffsetPlay;   // button rises when hovered

            // Button dimensions
            double btnW = 220;
            double btnH = 60;
            double btnX = (WIDTH - btnW) / 2.0;
            double btnY = 490 + bobY;       // centred horizontally, bobs vertically

            // Pill background
            gc.save();
            gc.setFill(hoveredPlay
                ? Color.rgb(255, 100, 180, 0.92)
                : Color.rgb(200, 60, 140, 0.80));
            roundRect(gc, btnX, btnY, btnW, btnH, 30);
            gc.fill();
            // Glow border
            gc.setStroke(Color.rgb(255, 230, 245, 0.95));
            gc.setLineWidth(2.5);
            roundRect(gc, btnX, btnY, btnW, btnH, 30);
            gc.stroke();
            // Subtle inner highlight stripe
            gc.setFill(Color.rgb(255, 255, 255, 0.18));
            roundRect(gc, btnX + 6, btnY + 6, btnW - 12, btnH / 2 - 6, 20);
            gc.fill();
            gc.restore();

            // ▶ triangle + "PLAY" text — measure total width and centre both together
            double triW = 22, triH = 28;
            String playLabel = "PLAY";
            gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 32));
            double labelW = textW(playLabel, 32);
            double gap    = 10;                              // space between triangle and text
            double totalW = triW + gap + labelW;
            double contentX = btnX + (btnW - totalW) / 2.0; // left edge of content block
            double midY     = btnY + btnH / 2.0;             // vertical centre of button

            // Triangle (pointing right)
            double tx = contentX;
            double ty = midY - triH / 2.0;
            gc.setFill(Color.WHITE);
            gc.fillPolygon(
                new double[]{tx,       tx,       tx + triW},
                new double[]{ty,       ty + triH, midY},
                3
            );

            // "PLAY" text — vertically centred on the same midY
            gc.setFill(Color.WHITE);
            gc.fillText(playLabel, contentX + triW + gap, midY + 11);
        }
    }

    /** Helper to draw a rounded rectangle path */
    private void roundRect(GraphicsContext gc, double x, double y, double w, double h, double r) {
        gc.beginPath();
        gc.moveTo(x + r, y);
        gc.lineTo(x + w - r, y);
        gc.arcTo(x + w, y, x + w, y + r, r);
        gc.lineTo(x + w, y + h - r);
        gc.arcTo(x + w, y + h, x + w - r, y + h, r);
        gc.lineTo(x + r, y + h);
        gc.arcTo(x, y + h, x, y + h - r, r);
        gc.lineTo(x, y + r);
        gc.arcTo(x, y, x + r, y, r);
        gc.closePath();
    }

    // ── Playing screen ────────────────────────────────────────────────────────
    private void drawPlaying(GraphicsContext gc) {
        if (scImage != null) gc.drawImage(scImage, 0, 0, WIDTH, HEIGHT);
        else { gc.setFill(Color.rgb(255,240,200)); gc.fillRect(0,0,WIDTH,HEIGHT); }

        // Grid cells
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            double x = gridX + c * CELL_SIZE, y = gridY + r * CELL_SIZE;
            gc.setFill(Color.color(1, 1, 1, 0.85));
            gc.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            gc.setStroke(Color.rgb(220, 200, 230, 180.0/255));
            gc.setLineWidth(1);
            gc.strokeRect(x, y, CELL_SIZE, CELL_SIZE);
        }

        int sz = CELL_SIZE - 10;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            int v = grid[r][c];
            if (v == -1) continue;
            double[] pos = candyDrawPos(r, c);
            double x = pos[0], y = pos[1];
            drawCandy(gc, v, x, y, sz);
            if (isCovered(r)) {
                gc.setFill(Color.rgb(0, 100, 255, 120.0/255));
                gc.fillRect(x - 5, y - 5, CELL_SIZE, CELL_SIZE);
            }
        }

        // Swept candy particles (hurricane effect)
        for (double[] p : sweptCandies) {
            gc.save();
            gc.setGlobalAlpha(Math.max(0, p[5]));
            drawCandy(gc, (int)p[0], p[1] - 20, p[2] - 20, 40);
            gc.restore();
        }

        // Flood
        if (floodActive && waterLevel > 0) {
            double waterY = gridY + ROWS * CELL_SIZE - waterLevel;
            gc.setFill(Color.rgb(0,150,255,150.0/255));
            gc.fillRect(gridX, waterY, COLS * CELL_SIZE, waterLevel);
            gc.setFill(Color.rgb(255,255,255,100.0/255));
            gc.fillRect(gridX, waterY - 6, COLS * CELL_SIZE, 12);
        }

        // Hurricane
        if (hurricaneActive) {
            double hx = hurricaneXd;
            double hy = gridY + ROWS * CELL_SIZE / 2.0 - HURR_H / 2.0;
            if (hurricaneImage != null) gc.drawImage(hurricaneImage, hx, hy, HURR_W, HURR_H);
            else { gc.setFill(Color.rgb(100,100,180,200.0/255)); gc.fillOval(hx, hy, HURR_W, HURR_H); }
        }

        // Donut sparkle-beam animation
        if (donutEffectActive) drawDonutEffect(gc);

        // HUD
        drawHUD(gc);
    }

    // ── Draw a single candy ───────────────────────────────────────────────────
    private void drawCandy(GraphicsContext gc, int v, double x, double y, int sz) {
        if (v < 0 || v >= candyImages.length) return;
        if (candyImages[v] != null) {
            gc.drawImage(candyImages[v], x, y, sz, sz);
        } else {
            Color col = (v < CANDY_COLORS.length) ? CANDY_COLORS[v] : Color.WHITE;
            gc.setFill(col);
            gc.fillOval(x, y, sz, sz);
            gc.setStroke(col.darker());
            gc.setLineWidth(2);
            gc.strokeOval(x, y, sz, sz);
            if (v == 5) {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 13));
                gc.fillText("★", x + sz/2.0 - 7, y + sz/2.0 + 5);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DONUT SPARKLE BEAM EFFECT
    // ══════════════════════════════════════════════════════════════════════════
    private void drawDonutEffect(GraphicsContext gc) {
        double progress = Math.min(donutEffectTimer / DONUT_EFFECT_DURATION, 1.0);
        long   ms       = System.currentTimeMillis();

        double fromX = gridX + donutFromC * CELL_SIZE + CELL_SIZE / 2.0;
        double fromY = gridY + donutFromR * CELL_SIZE + CELL_SIZE / 2.0;

        Color[] beamColors = {
            Color.web("#ffe066"),
            Color.web("#ff5555"),
            Color.web("#55ff88"),
            Color.web("#ffaa44"),
            Color.web("#cc66ff"),
        };
        Color beamCol = (donutTargetColor >= 0 && donutTargetColor < 5)
            ? beamColors[donutTargetColor] : Color.WHITE;

        for (int[] cell : donutTargetCells) {
            double toX = gridX + cell[1] * CELL_SIZE + CELL_SIZE / 2.0;
            double toY = gridY + cell[0] * CELL_SIZE + CELL_SIZE / 2.0;
            double tipX = fromX + (toX - fromX) * progress;
            double tipY = fromY + (toY - fromY) * progress;
            drawSparkleBeam(gc, fromX, fromY, tipX, tipY, beamCol, ms, progress);
        }

        double pulse = 0.5 + 0.5 * Math.sin(ms / 80.0);
        gc.setFill(beamCol.deriveColor(0, 1, 1, 0.35 * pulse));
        gc.fillOval(fromX - 28, fromY - 28, 56, 56);

        if (progress > 0.3) {
            String label = donutTargetColor >= 0 && donutTargetColor < COLOR_NAMES.length
                ? "★ " + COLOR_NAMES[donutTargetColor] + " ×" + donutTargetCells.size() + "!"
                : "";
            gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 22));
            gc.setFill(beamCol.deriveColor(0, 1, 1, Math.min((progress - 0.3) / 0.4, 1.0)));
            gc.fillText(label, fromX - textW(label, 22) / 2, fromY - 38);
        }
    }

    private void drawSparkleBeam(GraphicsContext gc,
                                  double x1, double y1, double x2, double y2,
                                  Color col, long ms, double progress) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx*dx + dy*dy);
        if (len < 1) return;

        double ux = dx / len, uy = dy / len;
        double px = -uy,       py =  ux;

        int segments = Math.max(4, (int)(len / 10));
        double phase = (ms / 120.0);

        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                gc.setStroke(col.deriveColor(0, 1, 1, 0.35));
                gc.setLineWidth(6);
            } else {
                gc.setStroke(col.deriveColor(0, 1, 1.2, 0.85));
                gc.setLineWidth(2.0);
            }

            gc.beginPath();
            for (int i = 0; i <= segments; i++) {
                double t  = (double)i / segments;
                double bx = x1 + t * dx;
                double by = y1 + t * dy;
                double amp    = 7.0 * Math.sin(progress * Math.PI);
                double wiggle = amp * Math.sin(t * Math.PI * 4 + phase);
                double qx = bx + wiggle * px;
                double qy = by + wiggle * py;
                if (i == 0) gc.moveTo(qx, qy);
                else        gc.lineTo(qx, qy);
            }
            gc.stroke();
        }

        Random sr = new Random((long)(x1 * 7 + y1 * 13 + ms / 200));
        int dotCount = Math.max(3, (int)(len / 20));
        for (int i = 0; i < dotCount; i++) {
            double t = ((double)i / dotCount + (ms % 400) / 400.0 * (1.0 / dotCount)) % 1.0;
            if (t > progress) continue;
            double bx    = x1 + t * dx;
            double by    = y1 + t * dy;
            double wiggle = 7.0 * Math.sin(progress * Math.PI) * Math.sin(t * Math.PI * 4 + (ms / 120.0));
            double qx = bx + wiggle * px;
            double qy = by + wiggle * py;
            double r  = 2.5 + sr.nextDouble() * 3.5;
            double alpha = 0.6 + 0.4 * Math.sin(ms / 80.0 + i);
            gc.setFill(col.deriveColor(0, 1, 1.3, alpha));
            gc.fillOval(qx - r, qy - r, r*2, r*2);
            gc.setFill(Color.color(1, 1, 1, alpha * 0.7));
            gc.fillOval(qx - r*0.35, qy - r*0.35, r*0.7, r*0.7);
        }

        double tipAlpha = 0.7 + 0.3 * Math.sin(ms / 60.0);
        gc.setFill(Color.color(1, 1, 1, tipAlpha));
        gc.fillOval(x2 - 5, y2 - 5, 10, 10);
        gc.setFill(col.deriveColor(0, 1, 1, tipAlpha * 0.8));
        gc.fillOval(x2 - 9, y2 - 9, 18, 18);
    }

    // ── HUD ───────────────────────────────────────────────────────────────────
    private void drawHUD(GraphicsContext gc) {
        // ── Goal panel — light pink bubbly box ────────────────────────────
        double goalBoxX = 18, goalBoxY = 26, goalBoxW = 230, goalBoxH = 210;
        gc.save();
        gc.setFill(Color.rgb(255, 210, 230, 0.82));
        roundRect(gc, goalBoxX, goalBoxY, goalBoxW, goalBoxH, 18);
        gc.fill();
        gc.setStroke(Color.rgb(230, 130, 180, 0.9));
        gc.setLineWidth(2);
        roundRect(gc, goalBoxX, goalBoxY, goalBoxW, goalBoxH, 18);
        gc.stroke();
        gc.restore();

        gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 17));
        gc.setFill(Color.rgb(150, 50, 100));
        gc.fillText("GOAL: Collect 20 each", goalBoxX + 10, goalBoxY + 24);

        for (int i = 0; i < 5; i++) {
            double gy = goalBoxY + 38 + i * 33;
            drawCandy(gc, i, goalBoxX + 8, gy, 26);
            int remaining = required[i] - collected[i];
            gc.setFill(remaining == 0 ? Color.rgb(0,160,0) : Color.rgb(100, 30, 70));
            gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 17));
            gc.fillText(COLOR_NAMES[i] + ": " + remaining, goalBoxX + 40, gy + 19);
        }

        // ── Timer box — centred bubbly box ────────────────────────────────
        String timeStr = "TIME: " + timeLeft;
        double twid = textW(timeStr, 28) + 32;
        double timerBoxX = WIDTH / 2.0 - twid / 2.0;
        double timerBoxY = 20;
        double timerBoxH = 52;

        gc.save();
        // Colour shifts red as time runs low
        double urgency = Math.max(0, 1.0 - timeLeft / 20.0);
        Color timerBg = Color.rgb(
            (int)(255),
            (int)(210 - urgency * 130),
            (int)(230 - urgency * 230),
            0.85
        );
        gc.setFill(timerBg);
        roundRect(gc, timerBoxX, timerBoxY, twid, timerBoxH, 16);
        gc.fill();
        gc.setStroke(urgency > 0.5 ? Color.rgb(220, 60, 60, 0.9) : Color.rgb(230, 130, 180, 0.9));
        gc.setLineWidth(2);
        roundRect(gc, timerBoxX, timerBoxY, twid, timerBoxH, 16);
        gc.stroke();
        gc.restore();

        gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 28));
        gc.setFill(urgency > 0.5 ? Color.rgb(180, 20, 20) : Color.rgb(120, 30, 80));
        gc.fillText(timeStr, timerBoxX + 16, timerBoxY + 37);

        // ── Disaster warning ──────────────────────────────────────────────
        if (disasterTriggered) {
            String txt = "⚡ " + disasterType +
                (disasterCountdown > 0 ? " in " + disasterCountdown + "s!" : " INCOMING!");
            double dw = textW(txt, 24) + 28;
            double dbx = (WIDTH - dw) / 2.0;
            double dby = 82;
            gc.save();
            gc.setFill(Color.rgb(255, 50, 50, 0.82));
            roundRect(gc, dbx, dby, dw, 42, 14);
            gc.fill();
            gc.restore();
            gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 22));
            gc.setFill(Color.WHITE);
            gc.fillText(txt, dbx + 14, dby + 29);
        }
    }

    // ── End screen ────────────────────────────────────────────────────────────
    private void drawEndScreen(GraphicsContext gc) {
        // Use win/lose background images if available
        if (state == GameState.WIN && scWinImage != null) {
            gc.drawImage(scWinImage, 0, 0, WIDTH, HEIGHT);
        } else if (state == GameState.LOSE && scLoseImage != null) {
            gc.drawImage(scLoseImage, 0, 0, WIDTH, HEIGHT);
        } else {
            gc.setFill(Color.rgb(30, 10, 40));
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        }

        // Semi-transparent overlay so text is readable over the image
        gc.setFill(Color.rgb(20, 5, 35, 0.55));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // ── Main message ──────────────────────────────────────────────────
        String msg = state == GameState.WIN ? "YOU WIN! 🍬" : "GAME OVER 💨";
        gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 72));
        // Shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.6));
        gc.fillText(msg, (WIDTH - textW(msg, 72)) / 2.0 + 4, 234);
        gc.setFill(state == GameState.WIN ? Color.rgb(100, 255, 120) : Color.rgb(255, 100, 100));
        gc.fillText(msg, (WIDTH - textW(msg, 72)) / 2.0, 230);

        // ── Collected summary box ─────────────────────────────────────────
        double sumBoxX = (WIDTH - 820) / 2.0;
        double sumBoxY = 270;
        double sumBoxW = 820;
        double sumBoxH = 130;
        gc.save();
        gc.setFill(Color.rgb(255, 210, 240, 0.78));
        roundRect(gc, sumBoxX, sumBoxY, sumBoxW, sumBoxH, 20);
        gc.fill();
        gc.setStroke(Color.rgb(220, 140, 190, 0.9));
        gc.setLineWidth(2);
        roundRect(gc, sumBoxX, sumBoxY, sumBoxW, sumBoxH, 20);
        gc.stroke();
        gc.restore();

        gc.setFill(Color.rgb(100, 30, 70));
        gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 22));
        gc.fillText("Candies Collected:", sumBoxX + 18, sumBoxY + 34);

        for (int i = 0; i < 5; i++) {
            double ex = sumBoxX + 14 + i * 164;
            double ey = sumBoxY + 52;
            drawCandy(gc, i, ex, ey, 32);
            boolean done = collected[i] >= required[i];
            gc.setFill(done ? Color.rgb(0, 160, 0) : Color.rgb(180, 50, 50));
            gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 18));
            gc.fillText(collected[i] + "/20", ex + 36, ey + 22);
            if (done) { gc.setFill(Color.rgb(0,200,0)); gc.fillText("✓", ex + 36, ey + 42); }
        }

        // ── BACK button ───────────────────────────────────────────────────
        triOffsetBack = hoveredBack ? Math.min(triOffsetBack+2,10) : Math.max(triOffsetBack-2,0);

        gc.save();
        gc.setFill(hoveredBack
            ? Color.rgb(255, 100, 180, 0.88)
            : Color.rgb(180, 50, 130, 0.78));
        roundRect(gc, backRectX - 6, backRectY - 6, backRectW + 12, backRectH + 12, 14);
        gc.fill();
        gc.setStroke(Color.rgb(255, 220, 240, 0.9));
        gc.setLineWidth(2);
        roundRect(gc, backRectX - 6, backRectY - 6, backRectW + 12, backRectH + 12, 14);
        gc.stroke();
        gc.restore();

        double bx = backRectX + triOffsetBack;
        gc.setFill(Color.WHITE);
        gc.fillPolygon(new double[]{bx, bx+14, bx}, new double[]{38,45,52}, 3);
        gc.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, 18));
        gc.fillText("BACK", bx + 20, 51);
    }

    // ── Text width helper ─────────────────────────────────────────────────────
    private double textW(String s, double size) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(Font.font(FUNKY_FONT, FontWeight.BOLD, size));
        t.applyCss();
        return t.getLayoutBounds().getWidth();
    }
}