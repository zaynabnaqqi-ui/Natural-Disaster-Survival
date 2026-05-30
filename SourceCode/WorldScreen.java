import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.io.File;

public class WorldScreen extends Pane {

    static final int WIDTH  = 1310;
    static final int HEIGHT = 730;

    private final Stage     parentStage;
    private final String    worldName;
    private final Color     bgColor;
    private final GamePanel mainMenu;

    private Image       backgroundImage;
    protected MediaPlayer bgMusic;
    private final Canvas canvas;

    // per-button triangle animation offsets, same sliding mechanic as gamepanel
    private int     triOffsetStart = 0, triOffsetBack = 0, triOffsetSim = 0;
    private boolean hoveredStart   = false, hoveredBack  = false, hoveredSim = false;
    // launched flag stops the animation loop from drawing over the active game screen
    private boolean launched       = false;
    protected AnimationTimer animTimer;

    private static final double START_X = 490, START_Y = 560, START_W = 310, START_H = 65;
    private static final double BACK_X  = 30,  BACK_Y  = 30,  BACK_W  = 140, BACK_H  = 40;

    // simulations lives in the bottom-right corner of this screen
    private static final double SIM_X = WIDTH - 310, SIM_Y = HEIGHT - 60;
    private static final double SIM_W = 290,          SIM_H = 50;

    private static final String[] WORLD_NAMES  = {"SUGARCREST", "MUSHROOMVILE", "MINE-WORLD", "PACMAN"};
    private static final String[] WORLD_IMAGES = {
        "Assets/sugarcrest.png", "Assets/mushroomvile.png",
        "Assets/mineworld.png",  "Assets/pacman.png"
    };

    public WorldScreen(Stage stage, String name, Color color, GamePanel menu) {
        this.parentStage = stage;
        this.worldName   = name;
        this.bgColor     = color;
        this.mainMenu    = menu;

        canvas = new Canvas(WIDTH, HEIGHT);
        getChildren().add(canvas);

        loadBackground();
        playMusic();
        hookInput();
        startLoop();
    }

    private void loadBackground() {
        // match the world name to its image asset by scanning the parallel arrays
        for (int i = 0; i < WORLD_NAMES.length; i++) {
            if (WORLD_NAMES[i].equals(worldName)) {
                backgroundImage = GamePanel.loadImage(WORLD_IMAGES[i]);
                break;
            }
        }
    }

    protected void playMusic() {
        // mushroomvile gets its own track, every other world shares the default one
        String path = worldName.equals("MUSHROOMVILE")
            ? "Assets/mushroomvile.wav"
            : "Assets/worldscreen.wav";
        File f = new File(path);
        if (!f.exists()) { System.out.println("Music missing: " + path); return; }
        bgMusic = new MediaPlayer(new Media(f.toURI().toString()));
        bgMusic.setCycleCount(MediaPlayer.INDEFINITE);
        bgMusic.play();
    }

    protected void stopMusic() {
        // dispose releases the native media resources, not just pausing
        if (bgMusic != null) { bgMusic.stop(); bgMusic.dispose(); bgMusic = null; }
    }

    private void hookInput() {
        canvas.setOnMouseMoved(e -> {
            boolean onStart = hit(e.getX(), e.getY(), START_X, START_Y, START_W, START_H);
            boolean onBack  = hit(e.getX(), e.getY(), BACK_X,  BACK_Y,  BACK_W,  BACK_H);
            boolean onSim   = hit(e.getX(), e.getY(), SIM_X,   SIM_Y,   SIM_W,   SIM_H);

            // only play hover sound on initial entry, not on every mouse-move frame inside the button
            if (onStart && !hoveredStart) { hoveredStart = true; mainMenu.playHover(); }
            if (onBack  && !hoveredBack)  { hoveredBack  = true; mainMenu.playHover(); }
            if (onSim   && !hoveredSim)   { hoveredSim   = true; mainMenu.playHover(); }
            if (!onStart) hoveredStart = false;
            if (!onBack)  hoveredBack  = false;
            if (!onSim)   hoveredSim   = false;

            canvas.setCursor(onStart || onBack || onSim
                ? javafx.scene.Cursor.HAND
                : javafx.scene.Cursor.DEFAULT);
        });

        canvas.setOnMouseClicked(e -> {
            if (hit(e.getX(), e.getY(), START_X, START_Y, START_W, START_H)) {
                mainMenu.playClick();
                launchGame();
            } else if (hit(e.getX(), e.getY(), BACK_X, BACK_Y, BACK_W, BACK_H)) {
                mainMenu.playClick();
                goBack();
            } else if (hit(e.getX(), e.getY(), SIM_X, SIM_Y, SIM_W, SIM_H)) {
                mainMenu.playClick();
                openSimulations();
            }
        });
    }

    private boolean hit(double mx, double my, double bx, double by, double bw, double bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    private void launchGame() {
        animTimer.stop();
        stopMusic();
        launched = true;

        // pick which game class to instantiate based on which world was selected
        Pane nextScreen;
        switch (worldName) {
            case "SUGARCREST"   -> nextScreen = new SugarcrestGame(parentStage, this);
            case "MUSHROOMVILE" -> nextScreen = new Mushroomvile(parentStage, this);
            case "PACMAN"       -> nextScreen = new PacmanGame(parentStage, this);
            case "MINE-WORLD"   -> nextScreen = new Mineworld(parentStage, this);
            default             -> nextScreen = new PacmanGame(parentStage, this);
        }

        // swap the scene root instead of making a new scene so the window doesn't flicker
        javafx.scene.Scene scene = parentStage.getScene();
        scene.setRoot(nextScreen);

        // wire keyboard events directly to the game that needs them
        if (nextScreen instanceof PacmanGame pg) {
            scene.setOnKeyPressed(pg::handleKey);
        } else if (nextScreen instanceof Mushroomvile mv) {
            scene.setOnKeyPressed(mv::handleKey);
            scene.setOnKeyReleased(mv::handleKeyReleased);
        } else if (nextScreen instanceof Mineworld mw) {
            scene.setOnKeyPressed(mw::onKeyDown);
            scene.setOnKeyReleased(mw::onKeyUp);
        }

        nextScreen.requestFocus();
        javafx.application.Platform.runLater(nextScreen::requestFocus);
    }

    public void returnToWorld() {
        javafx.application.Platform.runLater(() -> {
            launched = false;
            if (animTimer != null) {
                animTimer.stop();
                animTimer.start();
            }

            // restart music fresh when we come back from a game
            stopMusic();
            playMusic();

            javafx.scene.Scene existing = parentStage.getScene();
            if (existing != null) {
                existing.setRoot(this);
            } else {
                parentStage.setScene(new javafx.scene.Scene(this, WIDTH, HEIGHT));
            }

            this.requestFocus();
        });
    }

    private void openSimulations() {
        stopMusic();
        animTimer.stop();
        SimulationMenu sm = new SimulationMenu(parentStage, () -> returnToWorld());
        javafx.scene.Scene simScene = new javafx.scene.Scene(sm, WIDTH, HEIGHT);
        parentStage.setScene(simScene);
        sm.requestFocus();
    }

    private void goBack() {
        stopMusic();
        animTimer.stop();
        mainMenu.returnToThis();
    }

    protected void startLoop() {
        animTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                // bail out immediately if a game is already running so we don't overdraw
                if (launched) {
                    animTimer.stop();
                    return;
                }
                triOffsetStart = hoveredStart ? Math.min(triOffsetStart + 2, 10) : Math.max(triOffsetStart - 2, 0);
                triOffsetBack  = hoveredBack  ? Math.min(triOffsetBack  + 2, 10) : Math.max(triOffsetBack  - 2, 0);
                triOffsetSim   = hoveredSim   ? Math.min(triOffsetSim   + 2, 10) : Math.max(triOffsetSim   - 2, 0);
                render();
            }
        };
        animTimer.start();
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        if (launched) {
            gc.clearRect(0, 0, WIDTH, HEIGHT);
            return;
        }

        if (backgroundImage != null) {
            gc.drawImage(backgroundImage, 0, 0, WIDTH, HEIGHT);
        } else {
            // fallback color if the world image didn't load
            gc.setFill(bgColor.darker().darker());
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        }

        gc.setFill(Color.WHITE);

        // start button — large triangle + text, centred at the bottom of the screen
        {
            double sx = 540 + triOffsetStart;
            gc.fillPolygon(new double[]{sx, sx + 14, sx}, new double[]{578, 585, 592}, 3);
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 48));
            gc.fillText("START", sx + 20, 600);
        }

        // back button — small, tucked in the top-left corner
        {
            double bx = 30 + triOffsetBack;
            gc.fillPolygon(new double[]{bx, bx + 14, bx}, new double[]{38, 45, 52}, 3);
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 15));
            gc.fillText("BACK", bx + 20, 51);
        }

        // simulations button shown on every world screen in the bottom-right corner
        if (worldName.equals("SUGARCREST") || worldName.equals("MUSHROOMVILE")
                || worldName.equals("MINE-WORLD") || worldName.equals("PACMAN")) {
            double sx = SIM_X + triOffsetSim;
            double sy = SIM_Y + 10;

            if (hoveredSim) {
                gc.setFill(Color.color(1.0, 0.78, 0.2, 0.18));
                gc.fillRoundRect(SIM_X - 6, SIM_Y - 4, SIM_W + 12, SIM_H + 4, 10, 10);
            }

            gc.setStroke(Color.color(1, 1, 1, 0.22));
            gc.setLineWidth(1);
            gc.strokeLine(SIM_X, SIM_Y + SIM_H / 2.0, SIM_X - 18, SIM_Y + SIM_H / 2.0);

            Color simCol = hoveredSim ? Color.web("#ffd060") : Color.web("#c8a040");
            gc.setFill(simCol);
            gc.fillPolygon(
                new double[]{sx,      sx + 14, sx},
                new double[]{sy - 12, sy - 6,  sy}, 3);

            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
            gc.fillText("SIMULATIONS", sx + 20, sy - 1);

            gc.setFont(Font.font("Courier New", 11));
            gc.setFill(Color.color(1, 0.85, 0.4, 0.55));
            gc.fillText("disaster simulation hub  ▶", sx + 20, sy + 14);
        }
    }
}