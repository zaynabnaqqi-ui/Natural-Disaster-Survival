import javafx.animation.*;
import javafx.scene.canvas.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SimulationMenu — pure Canvas rendering, no JavaFX layout engine involvement.
 * Constructor takes a Runnable for back-navigation so the caller controls
 * how to restore their own screen (avoids the double-scene-root problem).
 */
public class SimulationMenu extends Pane {

    public static final int WIDTH  = 1310;
    public static final int HEIGHT = 730;

    // ── Colours ───────────────────────────────────────────────────────────
    private static final Color BG_TOP       = Color.web("#0d0a08");
    private static final Color BG_MID       = Color.web("#1a1208");
    private static final Color ACCENT_EMBER = Color.web("#ff6a00");
    private static final Color ACCENT_GOLD  = Color.web("#ffd060");
    private static final Color TEXT_PRIMARY = Color.web("#f0e8d8");
    private static final Color TEXT_DIM     = Color.web("#8a7a60");
    private static final Color BTN_BASE     = Color.web("#1e1610");
    private static final Color BTN_HOVER    = Color.web("#2e1e0a");
    private static final Color BTN_BORDER   = Color.web("#3a2a14");

    // ── Disaster descriptors ──────────────────────────────────────────────
    private static class DisasterInfo {
        final String label, emoji, tagline;
        final Color  accent;
        final int    id;
        DisasterInfo(int id, String label, String emoji, String tagline, Color accent) {
            this.id = id; this.label = label; this.emoji = emoji;
            this.tagline = tagline; this.accent = accent;
        }
    }

    private static final DisasterInfo[] DISASTERS = {
        new DisasterInfo(0, "FLOOD SIMULATION",      "🌊",
            "Rising waters — survival under pressure",   Color.web("#1a8cff")),
        new DisasterInfo(1, "METEOR SIMULATION",     "☄",
            "Impact from above — the sky is falling",    Color.web("#ff4400")),
        new DisasterInfo(2, "EARTHQUAKE SIMULATION", "⚡",
            "The ground splits — nowhere to stand",      Color.web("#c8a040")),
        new DisasterInfo(3, "WILDFIRE SIMULATION",   "🔥",
            "Walls of fire — the forest ignites",        Color.web("#ff6600")),
        new DisasterInfo(4, "TORNADO SIMULATION",    "🌪",
            "Spinning destruction — a vortex unleashed", Color.web("#80c0e0")),
        new DisasterInfo(5, "VOLCANIC ACTIVITY",     "🌋",
            "Magma rising — the earth erupts",           Color.web("#ff2200")),
    };

    // ── Button grid geometry ──────────────────────────────────────────────
    private static final double BTN_W     = 580;
    private static final double BTN_H     = 94;
    private static final double BTN_GAP_X = 20;
    private static final double BTN_GAP_Y = 16;
    private static final double GRID_TOP  = 175;
    private static final double GRID_LEFT = (WIDTH - (BTN_W * 2 + BTN_GAP_X)) / 2.0;

    // Nav buttons
    private static final double BACK_X = 50,         BACK_Y = HEIGHT - 58;
    private static final double BACK_W = 165,         BACK_H = 38;
    private static final double EXIT_X = WIDTH - 215, EXIT_Y = HEIGHT - 58;
    private static final double EXIT_W = 165,         EXIT_H = 38;

    // ── State ─────────────────────────────────────────────────────────────
    private final Stage    stage;
    private final Runnable onBack;
    private final Canvas   canvas;
    private AnimationTimer loop;

    private int    hoveredBtn = -1;
    private int    hoveredNav = 0;    // 0=none 1=back 2=exit
    private double fadeAlpha  = 0.0;

    // ── Particles ─────────────────────────────────────────────────────────
    private static class Ember {
        double x, y, vx, vy, size, opacity, life;
        Color color;
    }
    private final List<Ember> embers = new ArrayList<>();
    private final Random rng = new Random();

    // ─────────────────────────────────────────────────────────────────────
    public SimulationMenu(Stage stage, Runnable onBack) {
        this.stage  = stage;
        this.onBack = onBack;

        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);

        canvas = new Canvas(WIDTH, HEIGHT);
        getChildren().add(canvas);

        spawnEmbers(110);
        hookInput();
        startLoop();
    }

    // ── Input ─────────────────────────────────────────────────────────────
    private void hookInput() {
        canvas.setFocusTraversable(true);
        canvas.setOnMouseMoved(this::onMove);
        canvas.setOnMouseClicked(this::onClick);
        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) goBack();
        });
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) goBack();
        });
    }

    private void onMove(MouseEvent e) {
        double mx = e.getX(), my = e.getY();
        hoveredBtn = -1;
        hoveredNav = 0;

        for (int i = 0; i < DISASTERS.length; i++) {
            double[] r = btnRect(i);
            if (hit(mx, my, r)) { hoveredBtn = i; break; }
        }
        if (hoveredBtn < 0) {
            if      (hit(mx, my, new double[]{BACK_X, BACK_Y, BACK_W, BACK_H})) hoveredNav = 1;
            else if (hit(mx, my, new double[]{EXIT_X, EXIT_Y, EXIT_W, EXIT_H})) hoveredNav = 2;
        }

        canvas.setCursor(hoveredBtn >= 0 || hoveredNav > 0
            ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
    }

    private void onClick(MouseEvent e) {
        double mx = e.getX(), my = e.getY();

        for (int i = 0; i < DISASTERS.length; i++) {
            if (hit(mx, my, btnRect(i))) { launchSim(DISASTERS[i].id); return; }
        }
        if (hit(mx, my, new double[]{BACK_X, BACK_Y, BACK_W, BACK_H})) { goBack();       return; }
        if (hit(mx, my, new double[]{EXIT_X, EXIT_Y, EXIT_W, EXIT_H}))  { System.exit(0); }
    }

    private boolean hit(double mx, double my, double[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    private double[] btnRect(int i) {
        int col = i % 2, row = i / 2;
        return new double[]{
            GRID_LEFT + col * (BTN_W + BTN_GAP_X),
            GRID_TOP  + row * (BTN_H  + BTN_GAP_Y),
            BTN_W, BTN_H
        };
    }

    // ── Simulation launcher ───────────────────────────────────────────────
    private void launchSim(int id) {
        loop.stop();

        // Build a fresh SimulationMenu as the return destination.
        // We must NOT reuse `this` — a node can only belong to one Scene at a time.
        SimulationMenu returnMenu = new SimulationMenu(stage, onBack);
        javafx.scene.Scene returnScene = new javafx.scene.Scene(returnMenu, WIDTH, HEIGHT);

        Pane sim = switch (id) {
            case 0 -> new FloodSimulation     (stage, returnScene);
            case 1 -> new MeteorSimulation    (stage, returnScene);
            case 2 -> new EarthquakeSimulation(stage, returnScene);
            case 3 -> new WildfireSimulation  (stage, returnScene);
            case 4 -> new TornadoSimulation   (stage, returnScene);
            case 5 -> new VolcanoSimulation   (stage, returnScene);
            default -> null;
        };

        if (sim == null) { loop.start(); return; }

        javafx.scene.Scene simScene = new javafx.scene.Scene(sim, WIDTH, HEIGHT);
        stage.setScene(simScene);
        sim.requestFocus();
    }

    // ── Back navigation ───────────────────────────────────────────────────
    public void goBack() {
        loop.stop();
        if (onBack != null) onBack.run();
    }

    // ── Loop ─────────────────────────────────────────────────────────────
    private void startLoop() {
        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (fadeAlpha < 1.0) fadeAlpha = Math.min(1.0, fadeAlpha + 0.035);
                tickParticles();
                render();
            }
        };
        loop.start();
    }

    // ── Render ────────────────────────────────────────────────────────────
    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Background gradient row by row
        for (int y = 0; y < HEIGHT; y++) {
            double t = (double) y / HEIGHT;
            Color c = t < 0.5
                ? BG_TOP.interpolate(BG_MID, t / 0.5)
                : BG_MID.interpolate(Color.web("#110e07"), (t - 0.5) / 0.5);
            gc.setFill(c);
            gc.fillRect(0, y, WIDTH, 1);
        }

        // Vignette
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.85, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.TRANSPARENT), new Stop(1, Color.color(0, 0, 0, 0.65))));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.setGlobalAlpha(fadeAlpha);
        drawParticles(gc);
        drawHeader(gc);
        for (int i = 0; i < DISASTERS.length; i++) drawButton(gc, i);
        drawNavBtn(gc, "◀  BACK TO MENU", BACK_X, BACK_Y, BACK_W, BACK_H, hoveredNav == 1, false);
        drawNavBtn(gc, "EXIT  ✕",          EXIT_X, EXIT_Y, EXIT_W, EXIT_H, hoveredNav == 2, true);
        gc.setGlobalAlpha(1.0);
    }

    private void drawHeader(GraphicsContext gc) {
        String t1 = "NATURAL DISASTER  ", t2 = "SIMULATIONS";
        double charW = 26.5;
        double sx = (WIDTH - (t1.length() + t2.length()) * charW) / 2.0;

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 46));
        gc.setFill(TEXT_PRIMARY);  gc.fillText(t1, sx, 92);
        gc.setFill(ACCENT_GOLD);   gc.fillText(t2, sx + t1.length() * charW, 92);

        double lw = 660, lx = (WIDTH - lw) / 2.0;
        gc.setStroke(ACCENT_EMBER); gc.setLineWidth(2);
        gc.strokeLine(lx, 106, lx + lw, 106);

        gc.setFont(Font.font("Courier New", 15));
        gc.setFill(TEXT_DIM);
        String sub = "Select a disaster simulation to explore";
        gc.fillText(sub, (WIDTH - sub.length() * 9.0) / 2.0, 132);
    }

    private void drawButton(GraphicsContext gc, int i) {
        double[] r = btnRect(i);
        double x = r[0], y = r[1], w = r[2], h = r[3];
        DisasterInfo d   = DISASTERS[i];
        boolean      hov = (hoveredBtn == i);

        gc.setFill(hov ? BTN_HOVER : BTN_BASE);
        fillRR(gc, x, y, w, h, 13);

        gc.setStroke(hov ? d.accent : BTN_BORDER);
        gc.setLineWidth(hov ? 2.0 : 1.5);
        strokeRR(gc, x, y, w, h, 13);

        if (hov) {
            gc.setFill(Color.color(d.accent.getRed(), d.accent.getGreen(), d.accent.getBlue(), 0.07));
            fillRR(gc, x - 5, y - 5, w + 10, h + 10, 17);
        }

        // Accent bar
        gc.setFill(d.accent);
        fillRR(gc, x + 11, y + (h - 58) / 2.0, 4, 58, 4);

        // Emoji
        gc.setFont(Font.font(28));
        gc.setFill(Color.WHITE);
        gc.fillText(d.emoji, x + 30, y + h / 2.0 + 8);

        // Label
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 19));
        gc.setFill(hov ? d.accent : TEXT_PRIMARY);
        gc.fillText(d.label, x + 74, y + h / 2.0 - 4);

        // Tagline
        gc.setFont(Font.font("Courier New", 12));
        gc.setFill(hov ? TEXT_PRIMARY : TEXT_DIM);
        gc.fillText(d.tagline, x + 74, y + h / 2.0 + 16);
    }

    private void drawNavBtn(GraphicsContext gc, String text,
                             double x, double y, double w, double h,
                             boolean hov, boolean danger) {
        gc.setStroke(hov ? (danger ? Color.web("#cc2020") : ACCENT_EMBER)
                         : (danger ? Color.web("#6a1010") : Color.web("#3a2a14")));
        gc.setLineWidth(1.5);
        strokeRR(gc, x, y, w, h, 6);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 13));
        gc.setFill(hov ? (danger ? Color.web("#ff8080") : TEXT_PRIMARY)
                       : (danger ? Color.web("#ff5050") : TEXT_DIM));
        gc.fillText(text, x + 14, y + h / 2.0 + 5);
    }

    // ── Rounded rect helpers ──────────────────────────────────────────────
    private void fillRR(GraphicsContext gc, double x, double y, double w, double h, double r) {
        gc.beginPath();
        gc.moveTo(x + r, y);
        gc.lineTo(x + w - r, y);     gc.arcTo(x+w, y,   x+w,   y+r,   r);
        gc.lineTo(x + w, y + h - r); gc.arcTo(x+w, y+h, x+w-r, y+h,   r);
        gc.lineTo(x + r, y + h);     gc.arcTo(x,   y+h, x,     y+h-r, r);
        gc.lineTo(x, y + r);         gc.arcTo(x,   y,   x+r,   y,     r);
        gc.closePath(); gc.fill();
    }

    private void strokeRR(GraphicsContext gc, double x, double y, double w, double h, double r) {
        gc.beginPath();
        gc.moveTo(x + r, y);
        gc.lineTo(x + w - r, y);     gc.arcTo(x+w, y,   x+w,   y+r,   r);
        gc.lineTo(x + w, y + h - r); gc.arcTo(x+w, y+h, x+w-r, y+h,   r);
        gc.lineTo(x + r, y + h);     gc.arcTo(x,   y+h, x,     y+h-r, r);
        gc.lineTo(x, y + r);         gc.arcTo(x,   y,   x+r,   y,     r);
        gc.closePath(); gc.stroke();
    }

    // ── Particles ─────────────────────────────────────────────────────────
    private void spawnEmbers(int count) {
        for (int i = 0; i < count; i++) embers.add(newEmber(true));
    }

    private Ember newEmber(boolean scatter) {
        Ember p = new Ember();
        p.x  = rng.nextDouble() * WIDTH;
        p.y  = scatter ? rng.nextDouble() * HEIGHT : HEIGHT + 4;
        p.vx = (rng.nextDouble() - 0.5) * 0.55;
        p.vy = -(0.3 + rng.nextDouble() * 0.85);
        p.size    = 1 + rng.nextDouble() * 2.4;
        p.opacity = 0.15 + rng.nextDouble() * 0.5;
        p.life    = 0;
        double roll = rng.nextDouble();
        p.color = roll < 0.4 ? ACCENT_EMBER : roll < 0.7 ? ACCENT_GOLD : Color.web("#ff3300");
        return p;
    }

    private void tickParticles() {
        for (int i = 0; i < embers.size(); i++) {
            Ember p = embers.get(i);
            p.x += p.vx; p.y += p.vy; p.life += 0.012;
            if (p.y < -10 || p.life > Math.PI) embers.set(i, newEmber(false));
        }
    }

    private void drawParticles(GraphicsContext gc) {
        for (Ember p : embers) {
            double a = p.opacity * Math.sin(Math.min(p.life, Math.PI));
            if (a <= 0.01) continue;
            gc.setFill(Color.color(
                p.color.getRed(), p.color.getGreen(), p.color.getBlue(), Math.min(a, 1.0)));
            gc.fillOval(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
        }
    }
}