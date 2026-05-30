import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class PacmanGame extends Pane {

    // ── Constants ──────────────────────────────────────────────────────────────
    static final int W      = 1310, H    = 730;
    static final int TILE   = 32;
    static final int COLS   = 28,   ROWS = 18;
    static final int MAZE_X = (W - COLS * TILE) / 2;
    static final int MAZE_Y = 80;

    // ── Maze  (1=wall, 0=dot, 2=empty) ────────────────────────────────────────
    static final int[][] MAZE = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,1,1,0,1,1,1,0,1,1,1,0,1,1,0,1,1,1,0,1,1,1,0,1,1,0,1},
        {1,0,1,1,0,1,1,1,0,1,1,1,0,1,1,0,1,1,1,0,1,1,1,0,1,1,0,1},
        {1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,1},
        {1,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,0,0,1},
        {1,1,1,1,0,1,1,0,0,1,1,1,1,2,2,1,1,1,1,0,0,1,1,0,1,1,1,1},
        {1,1,1,1,0,1,1,0,0,1,2,2,2,2,2,2,2,2,1,0,0,1,1,0,1,1,1,1},
        {1,1,1,1,0,0,0,0,0,2,2,1,1,2,2,1,1,2,2,0,0,0,0,0,1,1,1,1},
        {1,1,1,1,0,1,1,0,0,2,2,1,2,2,2,2,2,1,2,0,0,1,1,0,1,1,1,1},
        {1,1,1,1,0,1,1,0,0,1,2,2,2,2,2,2,2,2,1,0,0,1,1,0,1,1,1,1},
        {1,1,1,1,0,1,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,1,0,1,1,1,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,1,1,0,1,1,1,0,1,1,1,0,1,1,0,1,1,1,0,1,1,1,0,1,1,0,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,1,1,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };

    // ── Enums ──────────────────────────────────────────────────────────────────
    enum GameState    { INTRO, COUNTDOWN, PLAYING, WIN, LOSE }
    enum DisasterType { FIRE, CHAPPAL }

    // ── References ────────────────────────────────────────────────────────────
    private final Stage       parentStage;
    private final WorldScreen prevScreen;
    private final Canvas      canvas;
    private       AnimationTimer gameTimer;

    // ── Images ────────────────────────────────────────────────────────────────
    private Image imgRight, imgLeft, imgUp, imgDown, imgDead;
    private Image fruitImg, fireImg, chappalImg;

    // ── Sounds ────────────────────────────────────────────────────────────────
    private AudioClip   fruitSound, winSound, loseSound;
    private AudioClip   hoverSound, clickSound, startSound, tickSound;
    private MediaPlayer obstacleLoop;

    // ── Game objects ──────────────────────────────────────────────────────────
    private final Random        rand      = new Random();
    private Player              player;
    private ArrayList<Fruit>    fruits    = new ArrayList<>();
    private ArrayList<Obstacle> obstacles = new ArrayList<>();
    private DisasterType        disaster;
    private int[][]             dotGrid;

    // ── State ─────────────────────────────────────────────────────────────────
    private GameState state           = GameState.INTRO;
    private int       score           = 0;
    private int       fruitsCollected = 0;
    private int       animTick        = 0;

    // Timer (30 seconds — matches original)
    private long gameStartTime = 0;
    private int  timeLeft      = 30;

    // Countdown
    private long countdownStart     = 0;
    private int  countdownValue     = 3;
    private int  lastCountdownFired = -1;

    // Intro hover
    private boolean playHovered   = false;
    private int     playTriOffset = 0;

    // ── Constructor ───────────────────────────────────────────────────────────
    public PacmanGame(Stage stage, WorldScreen prev) {
        this.parentStage = stage;
        this.prevScreen  = prev;

        canvas = new Canvas(W, H);
        getChildren().add(canvas);

        // IMPORTANT: make this Pane focusable so key events reach it
        setFocusTraversable(true);

        disaster = rand.nextBoolean() ? DisasterType.FIRE : DisasterType.CHAPPAL;
        loadAssets();
        initGame();
        hookMouseInput();
        hookKeyInput();
        startLoop();

        // Request focus when added to scene
        javafx.application.Platform.runLater(this::requestFocus);
    }

    // ── Asset loading ─────────────────────────────────────────────────────────
    private void loadAssets() {
        imgRight   = img("Assets/pmSprite/right.png");
        imgLeft    = img("Assets/pmSprite/left.png");
        imgUp      = img("Assets/pmSprite/up.png");
        imgDown    = img("Assets/pmSprite/down.png");
        imgDead    = img("Assets/pmSprite/dead.png");
        fruitImg   = img("Assets/fruit.png");
        fireImg    = img("Assets/fire.png");
        chappalImg = img("Assets/chappal.png");

        fruitSound  = clip("Assets/pmSound/fruit.wav");
        winSound    = clip("Assets/pmSound/win.wav");
        loseSound   = clip("Assets/pmSound/lose.wav");
        hoverSound  = clip("Assets/hover.wav");
        clickSound  = clip("Assets/click.wav");
        startSound  = clip("Assets/pmSound/start.wav");
        tickSound   = clip("Assets/pmSound/tick.wav");

        File f = new File("Assets/pmSound/obstacle.wav");
        if (f.exists()) {
            obstacleLoop = new MediaPlayer(new Media(f.toURI().toString()));
            obstacleLoop.setCycleCount(MediaPlayer.INDEFINITE);
        }
    }

    private Image img(String path) {
        File f = new File(path);
        if (!f.exists()) { System.out.println("Missing image: " + path); return null; }
        return new Image(f.toURI().toString());
    }

    private AudioClip clip(String path) {
        File f = new File(path);
        if (!f.exists()) { System.out.println("Missing sound: " + path); return null; }
        return new AudioClip(f.toURI().toString());
    }

    private void play(AudioClip c) { if (c != null) c.play(); }

    // ── Game init — identical to original initGame() ─────────────────────────
    private void initGame() {
        dotGrid = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                dotGrid[r][c] = MAZE[r][c];

        int startRow = 15, startCol = 13;
        if (MAZE[startRow][startCol] == 1) {
            outer:
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    if (MAZE[r][c] != 1) { startRow = r; startCol = c; break outer; }
        }
        player = new Player(pixelX(startCol), pixelY(startRow));

        int[][] spots = {{1,1},{1,26},{16,1},{16,26}};
        for (int[] sp : spots) {
            fruits.add(new Fruit(pixelX(sp[1]), pixelY(sp[0])));
            dotGrid[sp[0]][sp[1]] = 2;
        }

        int count = disaster == DisasterType.FIRE ? 2 : 3;
        for (int i = 0; i < count; i++) spawnObstacle();
    }

    // ── Key input — wired directly on the Pane (no scene dependency) ─────────
    private void hookKeyInput() {
        setOnKeyPressed(this::handleKey);
    }

    // Also callable externally if WorldScreen wires scene.setOnKeyPressed(pg::handleKey)
    public void handleKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.ESCAPE) { exitGame(); return; }
        if (state == GameState.PLAYING) player.queueDir(k);
        if (state == GameState.INTRO && (k == KeyCode.ENTER || k == KeyCode.SPACE)) {
            play(clickSound);
            startCountdown();
        }
    }

    // ── Mouse — intro PLAY button ─────────────────────────────────────────────
    private void hookMouseInput() {
        canvas.setOnMouseMoved(e -> {
            boolean on = inPlayBtn(e.getX(), e.getY());
            if (on && !playHovered) { playHovered = true; play(hoverSound); }
            if (!on) playHovered = false;
            if (state == GameState.INTRO)
                canvas.setCursor(on ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        });
        canvas.setOnMouseClicked(e -> {
            if (state == GameState.INTRO && inPlayBtn(e.getX(), e.getY())) {
                play(clickSound);
                startCountdown();
            }
        });
    }

    // Matches original Rectangle(W/2-100, 565, 200, 60)
    private boolean inPlayBtn(double mx, double my) {
        return mx >= W / 2.0 - 100 && mx <= W / 2.0 + 100 && my >= 565 && my <= 625;
    }

    // ── Game loop — AnimationTimer at ~60fps, mirrors original 16ms Thread loop
    private void startLoop() {
        gameTimer = new AnimationTimer() {
            private long last = 0;
            @Override public void handle(long now) {
                if (now - last >= 16_000_000L) {
                    update();
                    render();
                    last = now;
                }
            }
        };
        gameTimer.start();
    }

    // ── Update — faithful copy of original update() ───────────────────────────
    private void update() {

        // COUNTDOWN ────────────────────────────────────────────────────────────
        if (state == GameState.COUNTDOWN) {
            long elapsed = System.currentTimeMillis() - countdownStart;

            if (elapsed >= 3800) {
                state         = GameState.PLAYING;
                gameStartTime = System.currentTimeMillis();
                if (obstacleLoop != null) obstacleLoop.play();
                play(startSound);
                return;
            }

            int newVal = 3 - (int)(elapsed / 1000);
            if (newVal < 0) newVal = 0;
            countdownValue = newVal;

            // Fire tick sound exactly once when digit changes — not every frame
            if (newVal != lastCountdownFired) {
                lastCountdownFired = newVal;
                play(tickSound);
            }
            return;
        }

        if (state != GameState.PLAYING) return;

        // PLAYING ──────────────────────────────────────────────────────────────
        animTick++;
        player.update();
        for (Obstacle o : obstacles) o.update();

        // Timer — 30 seconds, same as original
        long elapsed = System.currentTimeMillis() - gameStartTime;
        timeLeft = Math.max(0, 30 - (int)(elapsed / 1000));
        if (timeLeft <= 0) {
            state = GameState.LOSE;
            play(loseSound);
            stopObstacleLoop();
            return;
        }

        // Eat dot under player centre
        eatDotAt(tileRow(player.y + TILE / 2), tileCol(player.x + TILE / 2));

        // Fruit collection
        for (int i = fruits.size() - 1; i >= 0; i--) {
            Fruit f = fruits.get(i);
            if (overlaps(player.bx(), player.by(), player.bw(), player.bh(),
                         f.bx(), f.by(), f.bw(), f.bh())) {
                fruits.remove(i);
                fruitsCollected++;
                score += 100;
                play(fruitSound);
                if (fruitsCollected >= 4) {
                    state = GameState.WIN;
                    play(winSound);
                    stopObstacleLoop();
                    return;
                }
            }
        }

        // Obstacle collision
        for (Obstacle o : obstacles) {
            if (overlaps(player.bx(), player.by(), player.bw(), player.bh(),
                         o.hbx(), o.hby(), o.hbw(), o.hbh())) {
                state = GameState.LOSE;
                play(loseSound);
                stopObstacleLoop();
                return;
            }
        }

        // Spawn extra obstacles over time
        if (animTick % 420 == 0 && obstacles.size() < 6) spawnObstacle();
    }

    // Stop obstacle loop audio without killing the AnimationTimer
    // (AnimationTimer must stay alive so ESC key / render still works on WIN/LOSE)
    private void stopObstacleLoop() {
        if (obstacleLoop != null) obstacleLoop.stop();
    }

    private void startCountdown() {
        state              = GameState.COUNTDOWN;
        countdownStart     = System.currentTimeMillis();
        countdownValue     = 3;
        lastCountdownFired = -1;
    }

    private void exitGame() {
    gameTimer.stop();
    stopObstacleLoop();
    prevScreen.returnToWorld();
}
    // ── Rendering — faithful copy of original paintComponent() ───────────────
    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Clear canvas every frame (JavaFX Canvas doesn't auto-clear unlike Swing JPanel)
        gc.clearRect(0, 0, W, H);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        playTriOffset = playHovered ? Math.min(playTriOffset + 2, 10) : Math.max(playTriOffset - 2, 0);

        if (state == GameState.INTRO) {
            drawGameBackground(gc);
            drawIntroOverlay(gc);
            return;
        }
        if (state == GameState.COUNTDOWN) {
            drawGameBackground(gc);
            drawCountdownOverlay(gc);
            return;
        }

        drawMaze(gc);
        drawDots(gc);
        for (Fruit    f : fruits)    f.draw(gc);
        for (Obstacle o : obstacles) o.draw(gc);
        player.draw(gc);
        drawHUD(gc);

        // WIN / LOSE overlay — same dark semi-transparent style for BOTH
        // (original Swing version used same overlay() method for both)
        if (state == GameState.WIN)  drawOverlay(gc, "YOU WIN!",  Color.rgb(0, 200, 80));
        if (state == GameState.LOSE) drawOverlay(gc, "GAME OVER", Color.rgb(220, 40, 40));
    }

    // Draw the full game scene used as background behind intro/countdown overlays
    private void drawGameBackground(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);
        drawMaze(gc);
        drawDots(gc);
        for (Fruit    f : fruits)    f.draw(gc);
        for (Obstacle o : obstacles) o.draw(gc);
        player.draw(gc);
    }

    // ── Intro overlay — matches original drawIntroOverlay() exactly ───────────
    private void drawIntroOverlay(GraphicsContext gc) {
        // Semi-transparent dark overlay
        gc.setFill(Color.rgb(0, 0, 0, 195.0 / 255));
        gc.fillRect(0, 0, W, H);

        // Title
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 42));
        gc.setFill(Color.WHITE);
        String title = "HOW TO PLAY";
        gc.fillText(title, cx(title, 42), 160);

        // Instructions
        gc.setFont(Font.font("Courier New", 20));
        gc.setFill(Color.rgb(220, 220, 220));
        String[] lines = {
            "Use ARROW KEYS or W A S D to move your character.",
            "Collect all 4 FRUITS to win!",
            "Avoid the natural disaster obstacles at all costs.",
            "You have 30 seconds — don't run out of time!"
        };
        double ly = 220;
        for (String line : lines) {
            gc.fillText(line, cx(line, 20), ly);
            ly += 34;
        }

        // Divider
        gc.setFill(Color.rgb(255, 255, 255, 60.0 / 255));
        gc.fillRect(W / 2.0 - 300, ly + 10, 600, 2);
        ly += 30;

        // Flavour line
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 13));
        gc.setFill(Color.rgb(255, 230, 80));
        String fl = "Capture the fruits while surviving the natural disaster. Yellow Dots give you points, but the fruits are what you really want!";
        gc.fillText(fl, cx(fl, 13), ly + 20);

        // Disaster warning
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 20));
        String dline;
        if (disaster == DisasterType.FIRE) {
            gc.setFill(Color.rgb(255, 120, 30));
            dline = "Your Natural Disaster: WILDFIRE";
        } else {
            gc.setFill(Color.rgb(200, 140, 255));
            dline = "Your Natural Disaster: MAMA'S CHAPPAL";
        }
        gc.fillText(dline, cx(dline, 20), ly + 60);

        gc.setFont(Font.font("Courier New", 17));
        gc.setFill(Color.rgb(200, 200, 200));
        String desc = disaster == DisasterType.FIRE
            ? "Wildfire spreads quickly! So be careful and evacuate fast!!"
            : "You annoyed your mom, get ready for flying chappals!";
        gc.fillText(desc, cx(desc, 17), ly + 88);

        if (disaster == DisasterType.CHAPPAL) {
            gc.setFont(Font.font("Courier New", FontPosture.ITALIC, 15));
            gc.setFill(Color.rgb(200, 160, 255));
            String ex = "( Beware! The more you avoid them, the more they will come )";
            gc.fillText(ex, cx(ex, 15), ly + 112);
        }

        // PLAY button — triangle + text, matches original exactly
        gc.setFill(playHovered ? Color.rgb(255, 255, 80) : Color.WHITE);
        double tx = W / 2.0 - 100 + 14 + playTriOffset;
        double ty = 595;
        gc.fillPolygon(
            new double[]{tx, tx + 14, tx},
            new double[]{ty - 10, ty, ty + 10},
            3
        );
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 38));
        gc.fillText("PLAY", tx + 20, 609);
    }

    // ── Countdown overlay — matches original drawCountdownOverlay() exactly ───
    private void drawCountdownOverlay(GraphicsContext gc) {
        gc.setFill(Color.rgb(0, 0, 0, 185.0 / 255));
        gc.fillRect(0, 0, W, H);

        long elapsed = System.currentTimeMillis() - countdownStart;

        String text;
        Color  col;
        int    fontSize;

        if      (elapsed < 1000) { text = "3";     col = Color.rgb(255, 80, 80);  fontSize = 160; }
        else if (elapsed < 2000) { text = "2";     col = Color.rgb(255, 200, 50); fontSize = 160; }
        else if (elapsed < 3000) { text = "1";     col = Color.rgb(80, 255, 120); fontSize = 160; }
        else                     { text = "PLAY!"; col = Color.rgb(255, 255, 80); fontSize = 100; }

        // Pulse animation — identical to original
        float phase = (float)(elapsed % 1000) / 1000f;
        float scale = 1.0f + 0.08f * (float)Math.sin(phase * Math.PI);
        int fs = (int)(fontSize * scale);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, fs));
        gc.setFill(col);
        gc.fillText(text, cx(text, fs), H / 2.0 + fs / 2.0 - 30);

        // Disaster label below countdown number
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        gc.setFill(Color.rgb(220, 220, 220));
        String dl = "Your Natural Disaster:  " +
            (disaster == DisasterType.FIRE ? "WILDFIRE" : "MAMA'S CHAPPAL");
        gc.fillText(dl, cx(dl, 22), H / 2.0 + fs / 2.0 + 50);
    }

    // ── Maze ──────────────────────────────────────────────────────────────────
    private void drawMaze(GraphicsContext gc) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (MAZE[r][c] == 1) {
                    double px = pixelX(c), py = pixelY(r);
                    gc.setFill(Color.rgb(0, 0, 180));
                    gc.fillRect(px + 2, py + 2, TILE - 4, TILE - 4);
                    gc.setStroke(Color.rgb(0, 80, 255));
                    gc.setLineWidth(2);
                    gc.strokeRect(px + 2, py + 2, TILE - 4, TILE - 4);
                }
            }
        }
    }

    // ── Dots ──────────────────────────────────────────────────────────────────
    private void drawDots(GraphicsContext gc) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (dotGrid[r][c] == 0) {
                    gc.setFill(Color.WHITE);
                    gc.fillOval(pixelX(c) + TILE / 2.0 - 3, pixelY(r) + TILE / 2.0 - 3, 6, 6);
                }
            }
        }
    }

    // ── HUD — identical to original drawHUD() ─────────────────────────────────
    private void drawHUD(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, MAZE_Y - 4);

        // Disaster label — left
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 20));
        gc.setFill(Color.WHITE);
        gc.fillText("NATURAL DISASTER:  " +
            (disaster == DisasterType.FIRE ? "WILDFIRE" : "MAMA'S CHAPPAL"),
            MAZE_X - 200, 50);

        // Timer — centred over maze
        String ts = String.format("TIME: %02d", timeLeft);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 28));
        gc.setFill(timeLeft <= 10 ? Color.rgb(255, 60, 60) : Color.CYAN);
        gc.fillText(ts, MAZE_X + COLS * TILE / 2.0 - tw(ts, 28) / 2.0, 52);

        // Score + fruits — right
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 20));
        gc.setFill(Color.YELLOW);
        gc.fillText("SCORE: " + score, W - 230, 40);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 16));
        gc.setFill(Color.WHITE);
        gc.fillText("FRUITS: " + fruitsCollected + " / 4", W - 230, 65);
    }

    // ── Win/Lose overlay — matches original overlay() method exactly ──────────
    // Original used the SAME dark overlay + coloured text for both WIN and LOSE.
    // The white-flash was a bug introduced in the failed port — removed here.
    private void drawOverlay(GraphicsContext gc, String msg, Color msgColor) {
        gc.setFill(Color.rgb(0, 0, 0, 190.0 / 255));
        gc.fillRect(0, 0, W, H);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 72));
        gc.setFill(msgColor);
        gc.fillText(msg, cx(msg, 72), H / 2.0);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 24));
        gc.setFill(Color.WHITE);
        String sub = "SCORE: " + score + "   |   Press ESC to return to menu";
        gc.fillText(sub, cx(sub, 24), H / 2.0 + 60);
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    private int pixelX(int col) { return MAZE_X + col * TILE; }
    private int pixelY(int row) { return MAZE_Y + row * TILE; }
    private int tileCol(int px) { return (px - MAZE_X) / TILE; }
    private int tileRow(int py) { return (py - MAZE_Y) / TILE; }
    private boolean inBounds(int r, int c) { return r >= 0 && r < ROWS && c >= 0 && c < COLS; }
    private boolean isWall(int r, int c)   { return !inBounds(r, c) || MAZE[r][c] == 1; }

    private void eatDotAt(int r, int c) {
        if (inBounds(r, c) && dotGrid[r][c] == 0) {
            dotGrid[r][c] = 2;
            score += 10;
        }
    }

    // AABB overlap — replaces AWT Rectangle.intersects()
    private boolean overlaps(int ax, int ay, int aw, int ah,
                             int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    // Accurate text width measurement for centring
    private double tw(String s, double size) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(Font.font("Courier New", FontWeight.BOLD, size));
        t.applyCss();
        return t.getLayoutBounds().getWidth();
    }

    // Horizontal centre x for a string
    private double cx(String s, double size) {
        return (W - tw(s, size)) / 2.0;
    }

    private void spawnObstacle() {
        int r, c;
        do {
            r = 1 + rand.nextInt(ROWS - 2);
            c = 1 + rand.nextInt(COLS - 2);
        } while (MAZE[r][c] == 1);
        obstacles.add(disaster == DisasterType.FIRE
            ? new FireObstacle(pixelX(c), pixelY(r))
            : new ChappalObstacle(pixelX(c), pixelY(r)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Player
    // ══════════════════════════════════════════════════════════════════════════
    class Player {
        int x, y, dx, dy, ndx, ndy, speed = 4, dir = 0, ndir = 0;
        Image[] sprites;

        Player(int x, int y) {
            this.x = x; this.y = y;
            sprites = new Image[]{ imgRight, imgLeft, imgUp, imgDown, imgDead };
            dx = speed;
        }

        void queueDir(KeyCode k) {
            switch (k) {
                case RIGHT, D -> { ndx =  speed; ndy = 0;      ndir = 0; }
                case LEFT,  A -> { ndx = -speed; ndy = 0;      ndir = 1; }
                case UP,    W -> { ndx = 0;      ndy = -speed; ndir = 2; }
                case DOWN,  S -> { ndx = 0;      ndy =  speed; ndir = 3; }
                default -> {}
            }
        }

        void update() {
            if (canMove(x + ndx, y + ndy)) { dx = ndx; dy = ndy; dir = ndir; }
            if (canMove(x + dx, y + dy))            { x += dx; y += dy; }
            if (x < MAZE_X - TILE)      x = MAZE_X + COLS * TILE;
            if (x > MAZE_X + COLS * TILE) x = MAZE_X - TILE;
        }

        boolean canMove(int nx, int ny) {
            int sz = TILE - 6;
            return !isWall(tileRow(ny + 3), tileCol(nx + 3))  &&
                   !isWall(tileRow(ny + sz), tileCol(nx + 3)) &&
                   !isWall(tileRow(ny + 3), tileCol(nx + sz)) &&
                   !isWall(tileRow(ny + sz), tileCol(nx + sz));
        }

        int bx() { return x + 5; }  int by() { return y + 5; }
        int bw() { return TILE - 10; } int bh() { return TILE - 10; }

        void draw(GraphicsContext gc) {
            int idx = (state == GameState.LOSE && sprites[4] != null) ? 4 : dir;
            Image im = sprites[idx];
            if (im != null) {
                gc.drawImage(im, x, y, TILE, TILE);
            } else {
                gc.setFill(Color.RED);     gc.fillRect(x + 4, y + 4, TILE - 8, TILE - 8);
                gc.setStroke(Color.WHITE); gc.strokeRect(x + 4, y + 4, TILE - 8, TILE - 8);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Fruit
    // ══════════════════════════════════════════════════════════════════════════
    class Fruit {
        int x, y;
        Fruit(int x, int y) { this.x = x; this.y = y; }
        int bx() { return x + 4; } int by() { return y + 4; }
        int bw() { return TILE - 8; } int bh() { return TILE - 8; }
        void draw(GraphicsContext gc) {
            if (fruitImg != null) gc.drawImage(fruitImg, x + 2, y + 2, TILE - 4, TILE - 4);
            else { gc.setFill(Color.RED); gc.fillOval(x + 6, y + 6, TILE - 12, TILE - 12); }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Obstacle base
    // ══════════════════════════════════════════════════════════════════════════
    abstract class Obstacle {
        int x, y, dx, dy, speed, frame = 0, ftick = 0;
        Image[] frames;

        Obstacle(int x, int y, Image[] frames, int speed) {
            this.x = x; this.y = y; this.frames = frames; this.speed = speed;
            randomDir();
        }

        void randomDir() {
            int[] o = { -speed, speed };
            if (rand.nextBoolean()) { dx = o[rand.nextInt(2)]; dy = 0; }
            else                    { dx = 0; dy = o[rand.nextInt(2)]; }
        }

        void update() {
            if (++ftick > 6) { ftick = 0; frame = (frame + 1) % frames.length; }
            int nx = x + dx, ny = y + dy;
            if (!wallCheck(nx, y)) { dx = -dx; nx = x; }
            if (!wallCheck(x, ny)) { dy = -dy; ny = y; }
            x = nx; y = ny;
            if (rand.nextInt(120) < 2) randomDir();
        }

        boolean wallCheck(int nx, int ny) {
            int sz = TILE - 6;
            return !isWall(tileRow(ny + 3), tileCol(nx + 3))  &&
                   !isWall(tileRow(ny + sz), tileCol(nx + 3)) &&
                   !isWall(tileRow(ny + 3), tileCol(nx + sz)) &&
                   !isWall(tileRow(ny + sz), tileCol(nx + sz));
        }

        abstract int hbx(); abstract int hby();
        abstract int hbw(); abstract int hbh();
        abstract void draw(GraphicsContext gc);
    }

    // ── Fire obstacle ─────────────────────────────────────────────────────────
    class FireObstacle extends Obstacle {
        float scale = 1.0f;
        int   growTick = 0;

        FireObstacle(int x, int y) {
            super(x, y, new Image[]{ fireImg, fireImg, fireImg, fireImg }, 1);
        }

        @Override void update() {
            super.update();
            if (++growTick > 150 && scale < 2.2f) {
                growTick = 0;
                scale = Math.min(scale + 0.12f, 2.2f);
            }
        }

        private int s()  { return (int)(TILE * scale); }
        private int ox() { return x - (s() - TILE) / 2; }
        private int oy() { return y - (s() - TILE) / 2; }

        @Override int hbx() { return ox() + s() / 4; } @Override int hby() { return oy() + s() / 4; }
        @Override int hbw() { return s() / 2; }         @Override int hbh() { return s() / 2; }

        @Override void draw(GraphicsContext gc) {
            Image f = frames[frame];
            if (f != null) gc.drawImage(f, ox(), oy(), s(), s());
            else {
                gc.setFill(Color.rgb(255, 80, 0, 200.0 / 255));
                gc.fillOval(ox(), oy(), s(), s());
            }
        }
    }

    // ── Chappal obstacle ──────────────────────────────────────────────────────
    class ChappalObstacle extends Obstacle {
        ChappalObstacle(int x, int y) {
            super(x, y, new Image[]{ chappalImg, chappalImg, chappalImg, chappalImg }, 2);
        }

        @Override int hbx() { return x + 5; }    @Override int hby() { return y + 5; }
        @Override int hbw() { return TILE - 10; } @Override int hbh() { return TILE - 10; }

        @Override void draw(GraphicsContext gc) {
            Image f = frames[frame];
            if (f != null) gc.drawImage(f, x, y, TILE, TILE);
            else {
                gc.setFill(Color.rgb(180, 100, 40));
                gc.fillRoundRect(x + 4, y + 8, TILE - 8, TILE - 16, 8, 8);
            }
        }
    }
}