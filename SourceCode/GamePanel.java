import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.io.File;

public class GamePanel extends Pane {

    static final int WIDTH  = 1310;
    static final int HEIGHT = 730;

    private final Stage  parentStage;
    private final Canvas canvas;

    private Image      backgroundImage;
    private AudioClip  hoverSound, clickSound;

    // the four playable worlds shown in the right column
    private final String[] worlds    = {"SUGARCREST", "MUSHROOMVILE", "MINE-WORLD", "PACMAN"};
    private final int[]    triOffset = {0, 0, 0, 0};
    private final double   startX    = 825;
    private final double[] worldY    = {390, 416, 442, 469};
    private int hoveredIndex     = -1;
    private int lastHoveredIndex = -1;

    // the seven disaster types shown in the left column
    private final String[] stages = {
        "FLOOD", "EARTHQUAKE", "VOLCANIC ACTIVITY",
        "HURRICANES", "WILDFIRE", "METEOROIDS", "MAMA'S CHAPPAL"
    };
    private final String[] stageInfo = {
        "Flooding occurs when water overflows onto normally dry land.",
        "Sudden shaking of the ground caused by tectonic plate movement.",
        "Eruption of magma, ash and gases from a volcano.",
        "Powerful tropical storms with strong winds and heavy rain.",
        "Uncontrolled fire that spreads rapidly through vegetation.",
        "Rocky debris from space that enters Earth's atmosphere.",
        "The most feared natural disaster. No survival tips exist lol."
    };
    private final int[]    triOffsetStage = {0, 0, 0, 0, 0, 0, 0};
    private final double   startXStage    = 163;
    private final double[] stageY         = {390, 416, 442, 469, 495, 521, 547};
    private int hoveredStage     = -1;
    private int lastHoveredStage = -1;

    // controls whether the info popup is visible and which disaster it shows
    private boolean showPopup     = false;
    private int     popupStageIdx = -1;

    // the simulations button sits below the world list with its own amber styling
    private static final double SIM_X = 825;
    private static final double SIM_Y = 530;
    private static final double SIM_W = 350;
    private static final double SIM_H = 32;
    private int     triOffsetSim   = 0;
    private boolean hoveredSim     = false;
    private boolean lastHoveredSim = false;

    // precomputed rectangular hit areas so mouse checks are just a bounds test
    private final double[] worldBoundsX = new double[4];
    private final double[] worldBoundsY = new double[4];
    private final double   BOUNDS_W     = 350;
    private final double   BOUNDS_H     = 28;

    private final double[] stageBoundsX = new double[7];
    private final double[] stageBoundsY = new double[7];

    private AnimationTimer animTimer;

    public GamePanel(Stage stage) {
        this.parentStage = stage;

        canvas = new Canvas(WIDTH, HEIGHT);
        getChildren().add(canvas);

        // fill in the hit-box arrays once so we're not recalculating every mouse move
        for (int i = 0; i < 4; i++) {
            worldBoundsX[i] = startX;
            worldBoundsY[i] = worldY[i] - 20;
        }
        for (int i = 0; i < 7; i++) {
            stageBoundsX[i] = startXStage;
            stageBoundsY[i] = stageY[i] - 20;
        }

        loadAssets();
        hookInput();
        startLoop();
    }

    private void loadAssets() {
        backgroundImage = loadImage("Assets/background.png");
        hoverSound      = loadClip("Assets/hover.wav");
        clickSound      = loadClip("Assets/click.wav");
    }

    // returns null gracefully if the file doesn't exist instead of crashing
    static Image loadImage(String path) {
        File f = new File(path);
        if (!f.exists()) { System.out.println("Missing image: " + path); return null; }
        return new Image(f.toURI().toString());
    }

    // same null-safe pattern for audio so missing sounds just print a warning
    static AudioClip loadClip(String path) {
        File f = new File(path);
        if (!f.exists()) { System.out.println("Missing sound: " + path); return null; }
        return new AudioClip(f.toURI().toString());
    }

    private void hookInput() {
        canvas.setOnMouseMoved(e -> {
            // reset all hover states at the top, then re-detect below
            hoveredStage = -1;
            hoveredIndex = -1;
            hoveredSim   = false;

            for (int i = 0; i < 4; i++) {
                if (inBounds(e, worldBoundsX[i], worldBoundsY[i], BOUNDS_W, BOUNDS_H)) {
                    hoveredIndex = i;
                    canvas.setCursor(javafx.scene.Cursor.HAND);
                    // only fire the hover sound when we first enter an item, not every frame
                    if (hoveredIndex != lastHoveredIndex) playClip(hoverSound);
                    lastHoveredIndex = hoveredIndex;
                    return;
                }
            }
            lastHoveredIndex = -1;

            if (inBounds(e, SIM_X, SIM_Y - 22, SIM_W, SIM_H)) {
                hoveredSim = true;
                canvas.setCursor(javafx.scene.Cursor.HAND);
                if (!lastHoveredSim) playClip(hoverSound);
                lastHoveredSim = true;
                return;
            }
            lastHoveredSim = false;

            for (int i = 0; i < 7; i++) {
                if (inBounds(e, stageBoundsX[i], stageBoundsY[i], BOUNDS_W, BOUNDS_H)) {
                    hoveredStage = i;
                    canvas.setCursor(javafx.scene.Cursor.HAND);
                    if (hoveredStage != lastHoveredStage) playClip(hoverSound);
                    lastHoveredStage = hoveredStage;
                    return;
                }
            }
            lastHoveredStage = -1;
            canvas.setCursor(javafx.scene.Cursor.DEFAULT);
        });

        canvas.setOnMouseClicked(e -> {
            for (int i = 0; i < 4; i++) {
                if (inBounds(e, worldBoundsX[i], worldBoundsY[i], BOUNDS_W, BOUNDS_H)) {
                    playClip(clickSound);
                    openWorld(worlds[i]);
                    return;
                }
            }

            if (inBounds(e, SIM_X, SIM_Y - 22, SIM_W, SIM_H)) {
                playClip(clickSound);
                openSimulations();
                return;
            }

            for (int i = 0; i < 7; i++) {
                if (inBounds(e, stageBoundsX[i], stageBoundsY[i], BOUNDS_W, BOUNDS_H)) {
                    playClip(clickSound);
                    popupStageIdx = i;
                    showPopup     = true;
                    return;
                }
            }

            // clicking anywhere that isn't a button dismisses the popup
            showPopup = false;
        });
    }

    private boolean inBounds(MouseEvent e, double bx, double by, double bw, double bh) {
        return e.getX() >= bx && e.getX() <= bx + bw
            && e.getY() >= by && e.getY() <= by + bh;
    }

    private void startLoop() {
        animTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                // each triangle nudges toward offset 10 when hovered and snaps back to 0 when not
                for (int i = 0; i < 4; i++) {
                    if (i == hoveredIndex) triOffset[i]      = Math.min(triOffset[i]      + 2, 10);
                    else                   triOffset[i]      = Math.max(triOffset[i]      - 2,  0);
                }
                for (int i = 0; i < 7; i++) {
                    if (i == hoveredStage) triOffsetStage[i] = Math.min(triOffsetStage[i] + 2, 10);
                    else                   triOffsetStage[i] = Math.max(triOffsetStage[i] - 2,  0);
                }
                triOffsetSim = hoveredSim
                    ? Math.min(triOffsetSim + 2, 10)
                    : Math.max(triOffsetSim - 2, 0);
                render();
            }
        };
        animTimer.start();
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        if (backgroundImage != null) {
            gc.drawImage(backgroundImage, 0, 0, WIDTH, HEIGHT);
        } else {
            // fallback so the screen isn't just transparent if the asset is missing
            gc.setFill(Color.BLACK);
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        }

        // draw each world name with its animated triangle bullet on the right side
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 29));
        for (int i = 0; i < worlds.length; i++) {
            double tx = startX + triOffset[i];
            double ty = worldY[i];
            gc.setFill(Color.WHITE);
            gc.fillPolygon(new double[]{tx, tx + 14, tx}, new double[]{ty - 14, ty - 7, ty}, 3);
            gc.fillText(worlds[i], tx + 20, ty - 2);
        }

        // simulations button uses amber/gold instead of white to stand out visually
        {
            double tx = startX + triOffsetSim;
            double ty = SIM_Y;

            if (hoveredSim) {
                gc.setFill(Color.color(1.0, 0.75, 0.2, 0.18));
                gc.fillRoundRect(tx - 4, ty - 26, SIM_W + 8, 32, 8, 8);
            }

            gc.setStroke(Color.color(1, 1, 1, 0.20));
            gc.setLineWidth(1);
            gc.strokeLine(startX, SIM_Y - 36, startX + 280, SIM_Y - 36);

            Color simColor = hoveredSim ? Color.web("#ffd060") : Color.web("#c8a040");
            gc.setFill(simColor);
            gc.fillPolygon(
                new double[]{tx, tx + 14, tx},
                new double[]{ty - 14, ty - 7, ty}, 3);

            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 24));
            gc.setFill(simColor);
            gc.fillText("SIMULATIONS", tx + 20, ty - 2);

            gc.setFont(Font.font("Courier New", 11));
            gc.setFill(Color.color(1, 0.85, 0.4, 0.6));
            gc.fillText("disaster simulation hub", tx + 20, ty + 16);
        }

        // draw each disaster name with its animated triangle bullet on the left side
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 29));
        for (int i = 0; i < stages.length; i++) {
            double tx = startXStage + triOffsetStage[i];
            double ty = stageY[i];
            gc.setFill(Color.WHITE);
            gc.fillPolygon(new double[]{tx, tx + 14, tx}, new double[]{ty - 14, ty - 7, ty}, 3);
            gc.fillText(stages[i], tx + 20, ty - 2);
        }

        // popup box appears over the bottom of the screen when a disaster is clicked
        if (showPopup && popupStageIdx >= 0) {
            gc.setFill(Color.rgb(0, 0, 0, 0.78));
            gc.fillRoundRect(360, 560, 650, 110, 20, 20);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 17));
            gc.fillText(stages[popupStageIdx], 380, 590);

            gc.setFont(Font.font("Courier New", 16));
            gc.fillText(stageInfo[popupStageIdx], 380, 618);

            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Courier New", 13));
            gc.fillText("click anywhere to close", 380, 648);
        }
    }

    private void openWorld(String worldName) {
        animTimer.stop();
        WorldScreen ws = new WorldScreen(parentStage, worldName, Color.WHITE, this);
        parentStage.getScene().setRoot(ws);
    }

    private void openSimulations() {
        animTimer.stop();
        SimulationMenu sm = new SimulationMenu(parentStage, () -> returnToThis());
        parentStage.getScene().setRoot(sm);
        sm.requestFocus();
    }

    public void playHover() { playClip(hoverSound); }
    public void playClick() { playClip(clickSound); }

    public void returnToThis() {
        // restart the loop since we stopped it when we left this screen
        animTimer.stop();
        animTimer.start();
        javafx.scene.Scene existing = parentStage.getScene();
        if (existing != null) {
            existing.setRoot(this);
        } else {
            parentStage.setScene(new javafx.scene.Scene(this, WIDTH, HEIGHT));
        }
        this.requestFocus();
    }

    static void playClip(AudioClip clip) {
        if (clip != null) clip.play();
    }
}