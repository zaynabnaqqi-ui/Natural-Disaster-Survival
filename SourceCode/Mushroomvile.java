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
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
public class Mushroomvile extends Pane {

    // ── dimensions ────────────────────────────────────────────────────────
    static final int W = 1310;
    static final int H = 730;

    // ── physics ───────────────────────────────────────────────────────────
    private static final double GRAVITY    = 0.55;
    private static final double JUMP_FORCE = -13.5;
    private static final double MOVE_SPEED = 4.5;
    private static final double GROUND_Y   = H - 110;
    private static final double FINISH_X   = 6000;

    // ── volcano positions ─────────────────────────────────────────────────
    private static final double VOLCANO_X  = FINISH_X * 0.5 - 55;
    private static final double VOLCANO_Y  = GROUND_Y - 280;
    // Second volcano — placed close to the end, shoots fast red balls
    private static final double VOLCANO2_X = FINISH_X * 0.85 - 55;
    private static final double VOLCANO2_Y = GROUND_Y - 280;

    // ── shelter position (at finish) ──────────────────────────────────────
    private static final double SHELTER_W  = 120;
    private static final double SHELTER_H  = 100;

    // ── references ────────────────────────────────────────────────────────
    private final Stage       parentStage;
    private final WorldScreen worldScreen;

    // ── canvas ────────────────────────────────────────────────────────────
    private final Canvas          canvas;
    private final GraphicsContext gc;

    // ── input ─────────────────────────────────────────────────────────────
    private boolean keyLeft, keyRight;
    private boolean upPressed;

    // ── state machine ─────────────────────────────────────────────────────
    enum State { STORY, PLAYING, WIN, LOSE }
    private State state = State.STORY;

    private double  storyAlpha  = 0;
    private boolean storyFadeIn = true;
    private double  storyTimer  = 0;
    private double  endAlpha    = 0;
    private boolean endFadeIn   = true;

    // ── world / camera ────────────────────────────────────────────────────
    private double cameraX = 0;

    // ── player ────────────────────────────────────────────────────────────
    private final PlayerObj player = new PlayerObj(120, GROUND_Y - 40);

    // ── world objects ─────────────────────────────────────────────────────
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Pipe>     pipes     = new ArrayList<>();
    private final List<Coin>     coins     = new ArrayList<>();
    private final List<Enemy>    enemies   = new ArrayList<>();
    private final List<Fireball> fireballs = new ArrayList<>();

    private final VolcanoObj volcano  = new VolcanoObj(VOLCANO_X,  VOLCANO_Y,  false);
    private final VolcanoObj volcano2 = new VolcanoObj(VOLCANO2_X, VOLCANO2_Y, true);

    // ── assets ────────────────────────────────────────────────────────────
    private Image imgVolcano, imgShelter;

    // ── HUD ───────────────────────────────────────────────────────────────
    private int     score      = 0;
    private int     lives      = 3;
    private boolean isDead     = false;
    private double  deathTimer = 0;

    // ── day/night cycle ───────────────────────────────────────────────────
    private double dayPhase    = 0.0;
    private static final double DAY_CYCLE_SPEED = 1.0 / 60.0;

    // ── random ────────────────────────────────────────────────────────────
    private final Random rng = new Random(42);

    // ── loop ──────────────────────────────────────────────────────────────
    private AnimationTimer gameLoop;
    private long           lastNano = 0;

    // ── Sprites ───────────────────────────────────────────────────────────
    private Image sprIdle, sprLeft, sprRight, sprLeftRun, sprRightRun;
    private Image sprEnemy, sprEnemyDead;

    private double walkAnimTimer = 0;
    private int    walkFrame     = 0;

    // ── Audio ─────────────────────────────────────────────────────────────
    private AudioClip   sndJump, sndStomp, sndGameOver, sndWin;
    private MediaPlayer bgMusic;

    // ─────────────────────────────────────────────────────────────────────
    public Mushroomvile(Stage stage, WorldScreen ws) {
        this.parentStage = stage;
        this.worldScreen = ws;

        canvas = new Canvas(W, H);
        gc     = canvas.getGraphicsContext2D();
        getChildren().add(canvas);
        setFocusTraversable(true);

        loadAssets();
        buildWorld();

        canvas.setOnMouseClicked(e -> {
            canvas.requestFocus();
            double mx = e.getX(), my = e.getY();
            if (state == State.WIN || state == State.LOSE) {
                double btnX = W / 2.0 - 110, btnY = H * 0.74, btnW = 220, btnH = 44;
                if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                    restartGame(); return;
                }
                double escY = H * 0.80;
                if (my >= escY && my <= escY + 28 && mx >= W / 2.0 - 130 && mx <= W / 2.0 + 130) {
                    stopBgMusic();
                    gameLoop.stop();
                    worldScreen.returnToWorld();
                }
            }
        });

        canvas.setOnMouseMoved(e -> {
            double mx = e.getX(), my = e.getY();
            if (state == State.WIN || state == State.LOSE) {
                double btnX = W / 2.0 - 110, btnY = H * 0.74, btnW = 220, btnH = 44;
                double escY = H * 0.80;
                boolean onBtn = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
                boolean onEsc = my >= escY && my <= escY + 28 && mx >= W / 2.0 - 130 && mx <= W / 2.0 + 130;
                canvas.setCursor(onBtn || onEsc ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
            } else {
                canvas.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });

        startLoop();
    }

    // ════════════════════════════════════════════════════════════════
    // Asset loading
    // ════════════════════════════════════════════════════════════════
    private void loadAssets() {
        sprIdle     = loadImg("Assets/mushroomvile/idle.png");
        sprLeft     = loadImg("Assets/mushroomvile/left.png");
        sprRight    = loadImg("Assets/mushroomvile/right.png");
        sprLeftRun  = loadImg("Assets/mushroomvile/leftrun.png");
        sprRightRun = loadImg("Assets/mushroomvile/rightrun.png");

        sprEnemy     = loadImg("Assets/mushroomvile/enemy.png");
        sprEnemyDead = loadImg("Assets/mushroomvile/enemydead.png");

        // Volcano and shelter images (optional — fallback to procedural if missing)
        imgVolcano = loadImg("Assets/volcano.png");
        imgShelter = loadImg("Assets/shelter.png");

        sndJump     = loadClip("Assets/mushroomvile/jump.wav");
        sndStomp    = loadClip("Assets/mushroomvile/steppingonenemy.wav");
        sndGameOver = loadClip("Assets/mushroomvile/gameover.wav");
        sndWin      = loadClip("Assets/mushroomvile/win.wav");

        File bgFile = new File("Assets/mushroomvile/bgmusic.wav");
        if (bgFile.exists()) {
            bgMusic = new MediaPlayer(new Media(bgFile.toURI().toString()));
            bgMusic.setCycleCount(MediaPlayer.INDEFINITE);
            bgMusic.setVolume(0.55);
            bgMusic.play();
        } else {
            System.out.println("Missing audio: Assets/mushroomvile/bgmusic.wav");
        }
    }

    private Image loadImg(String path) {
        File f = new File(path);
        if (!f.exists()) { System.out.println("Missing image: " + path); return null; }
        return new Image(f.toURI().toString());
    }

    private AudioClip loadClip(String path) {
        File f = new File(path);
        if (!f.exists()) { System.out.println("Missing sound: " + path); return null; }
        return new AudioClip(f.toURI().toString());
    }

    private void playClip(AudioClip c)             { if (c != null) c.play(); }
    private void playClip(AudioClip c, double vol) { if (c != null) c.play(vol); }

    private void stopBgMusic() {
        if (bgMusic != null) bgMusic.stop();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  World generation
    // ─────────────────────────────────────────────────────────────────────
    private void buildWorld() {
        double[][] plats = {
            {350,  GROUND_Y-100,130}, {550,  GROUND_Y-160,110}, {720,  GROUND_Y-90, 150},
            {950,  GROUND_Y-140,120}, {1150, GROUND_Y-80, 100}, {1350, GROUND_Y-170,140},
            {1600, GROUND_Y-120,120}, {1820, GROUND_Y-90, 160}, {2050, GROUND_Y-180,110},
            {2280, GROUND_Y-130,130}, {2500, GROUND_Y-100,150}, {2750, GROUND_Y-200,100},
            {2980, GROUND_Y-150,120}, {3200, GROUND_Y-90, 140}, {3450, GROUND_Y-160,130},
            {3700, GROUND_Y-120,120}, {3950, GROUND_Y-200,110}, {4200, GROUND_Y-140,150},
            {4500, GROUND_Y-170,130}, {4780, GROUND_Y-100,140}, {5000, GROUND_Y-180,120},
            {5250, GROUND_Y-130,130}, {5500, GROUND_Y-90, 160},
        };
        for (double[] p : plats) platforms.add(new Platform(p[0], p[1], p[2], 20));

        double[] pipeXs = {450,800,1250,1700,2100,2600,3000,3500,3900,4300,4900,5300};
        for (double px : pipeXs) {
            double ph = 60 + rng.nextInt(60);
            pipes.add(new Pipe(px, GROUND_Y - ph, 50, ph));
        }

        for (Platform pl : platforms) {
            int count = 1 + rng.nextInt(3);
            for (int i = 0; i < count; i++)
                coins.add(new Coin(pl.x + 20 + i * 30, pl.y - 25));
        }
        for (int i = 0; i < 30; i++)
            coins.add(new Coin(300 + i * 185.0, GROUND_Y - 160 - rng.nextInt(80)));

        double[] enemyXs = {500,900,1400,1900,2400,2900,3300,3800,4200,4700,5200};
        for (double ex : enemyXs) enemies.add(new Enemy(ex, GROUND_Y - 28));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Input
    // ─────────────────────────────────────────────────────────────────────
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.LEFT)  keyLeft  = true;
        if (c == KeyCode.RIGHT) keyRight = true;
        if ((c == KeyCode.UP || c == KeyCode.SPACE) && !upPressed) {
            upPressed = true;
            if (player.jump()) playClip(sndJump, 0.7);
        }
        if (c == KeyCode.ESCAPE) {
            stopBgMusic();
            gameLoop.stop();
            worldScreen.returnToWorld();
            return;
        }
        if (c == KeyCode.R && (state == State.WIN || state == State.LOSE)) restartGame();
        if (c == KeyCode.ENTER && state == State.STORY) storyTimer = 999;
    }

    public void handleKeyReleased(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.LEFT)  keyLeft   = false;
        if (c == KeyCode.RIGHT) keyRight  = false;
        if (c == KeyCode.UP || c == KeyCode.SPACE) upPressed = false;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Loop
    // ─────────────────────────────────────────────────────────────────────
    private void startLoop() {
        gameLoop = new AnimationTimer() {
            @Override public void handle(long now) {
                double dt = lastNano == 0 ? 0.016 : (now - lastNano) / 1_000_000_000.0;
                dt = Math.min(dt, 0.05);
                lastNano = now;
                update(dt);
                render();
            }
        };
        gameLoop.start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Update
    // ─────────────────────────────────────────────────────────────────────
    private void update(double dt) {
        if (state == State.PLAYING) dayPhase = (dayPhase + dt * DAY_CYCLE_SPEED) % 1.0;
        switch (state) {
            case STORY         -> updateStory(dt);
            case PLAYING       -> updateGame(dt);
            case WIN, LOSE     -> updateEndScreen(dt);
        }
    }

    private void updateStory(double dt) {
        storyTimer += dt;
        if (storyFadeIn) {
            storyAlpha = Math.min(1.0, storyAlpha + dt * 0.8);
            if (storyTimer > 2.5) storyFadeIn = false;
        } else {
            storyAlpha = Math.max(0.0, storyAlpha - dt * 0.6);
            if (storyAlpha <= 0) state = State.PLAYING;
        }
    }

    private void updateEndScreen(double dt) {
        if (endFadeIn) endAlpha = Math.min(1.0, endAlpha + dt * 1.2);
    }

    private void updateGame(double dt) {
        if (isDead) {
            deathTimer -= dt;
            if (deathTimer <= 0) { isDead = false; respawn(); }
            return;
        }

        if (keyLeft)       { player.vx = -MOVE_SPEED; player.facingRight = false; }
        else if (keyRight) { player.vx =  MOVE_SPEED; player.facingRight = true;  }
        else               player.vx *= 0.7;

        if (player.onGround && Math.abs(player.vx) > 0.5) {
            walkAnimTimer += dt;
            if (walkAnimTimer > 0.13) { walkAnimTimer = 0; walkFrame = (walkFrame + 1) % 2; }
        } else {
            walkFrame = 0; walkAnimTimer = 0;
        }

        player.vy += GRAVITY;
        player.x  += player.vx;
        player.y  += player.vy;
        if (player.x < 0) player.x = 0;

        boolean onGround = false;
        if (player.y + player.r >= GROUND_Y) {
            player.y = GROUND_Y - player.r;
            player.vy = 0; player.jumpsLeft = 2; onGround = true;
        }

        for (Platform pl : platforms) {
            double prevBottom = (player.y - player.vy) + player.r;
            double currBottom = player.y + player.r;
            if (player.x + player.r > pl.x && player.x - player.r < pl.x + pl.w) {
                if (prevBottom <= pl.y && currBottom >= pl.y) {
                    player.y = pl.y - player.r;
                    player.vy = 0; player.jumpsLeft = 2; onGround = true;
                }
            }
        }

        for (Pipe p : pipes) resolveAABB(p.x, p.y, p.w, p.h);
        player.onGround = onGround;

        double targetCam = player.x - W * 0.3;
        cameraX = Math.max(0, targetCam);

        Iterator<Coin> ci = coins.iterator();
        while (ci.hasNext()) {
            Coin co = ci.next();
            if (dist(player.x, player.y, co.x, co.y) < player.r + 10) {
                score += 100; ci.remove();
            }
        }

        for (Enemy en : enemies) {
            en.update(dt, platforms);
            if (!en.dead && dist(player.x, player.y, en.x, en.y) < player.r + en.r) {
                if (player.vy > 0 && player.y < en.y) {
                    en.dead = true;
                    player.vy = JUMP_FORCE * 0.6;
                    score += 200;
                    playClip(sndStomp, 0.8);
                } else {
                    triggerDeath();
                }
            }
        }

        // ── Volcano 1 (normal speed) ──────────────────────────────────────
        volcano.update(dt, fireballs, rng);
        // ── Volcano 2 (fast red balls) ────────────────────────────────────
        volcano2.update(dt, fireballs, rng);

        Iterator<Fireball> fi = fireballs.iterator();
        while (fi.hasNext()) {
            Fireball fb = fi.next();
            fb.update(dt);
            if (fb.y > H + 50) { fi.remove(); continue; }
            if (!isDead && dist(player.x, player.y, fb.x, fb.y) < player.r + fb.r) {
                triggerDeath(); fi.remove();
            }
        }

        if (player.y > H + 100) triggerDeath();

        // ── Win condition: reach shelter at FINISH_X ──────────────────────
        if (player.x >= FINISH_X - SHELTER_W * 0.5) {
            state = State.WIN; endAlpha = 0; endFadeIn = true;
            stopBgMusic();
            playClip(sndWin, 0.9);
        }
    }

    private void resolveAABB(double bx, double by, double bw, double bh) {
        double pr=player.r, px=player.x, py=player.y;
        if (px+pr>bx && px-pr<bx+bw && py+pr>by && py-pr<by+bh) {
            double oL=(px+pr)-bx, oR=(bx+bw)-(px-pr), oT=(py+pr)-by, oB=(by+bh)-(py-pr);
            double m=Math.min(Math.min(oL,oR),Math.min(oT,oB));
            if      (m==oT && player.vy>=0) { player.y=by-pr;    player.vy=0; player.jumpsLeft=2; }
            else if (m==oB && player.vy<0)  { player.y=by+bh+pr; player.vy=0; }
            else if (m==oL)                  { player.x=bx-pr;    player.vx=0; }
            else                             { player.x=bx+bw+pr; player.vx=0; }
        }
    }

    private void triggerDeath() {
        if (isDead) return;
        isDead=true; deathTimer=1.5; lives--;
        player.vy = JUMP_FORCE * 0.8;
        if (lives<=0) {
            state=State.LOSE; endAlpha=0; endFadeIn=true;
            stopBgMusic();
            playClip(sndGameOver, 0.9);
        }
    }

    private void respawn() {
        player.x=Math.max(0,cameraX+60); player.y=GROUND_Y-player.r-10;
        player.vx=0; player.vy=0; player.jumpsLeft=2;
    }

    private void restartGame() {
        score=0; lives=3; isDead=false; deathTimer=0; cameraX=0; dayPhase=0;
        player.x=120; player.y=GROUND_Y-40; player.vx=0; player.vy=0; player.jumpsLeft=2;
        player.facingRight=true;
        platforms.clear(); pipes.clear(); coins.clear(); enemies.clear(); fireballs.clear();
        buildWorld();
        state=State.STORY; storyAlpha=0; storyFadeIn=true; storyTimer=0; endAlpha=0; endFadeIn=true;
        walkFrame=0; walkAnimTimer=0;
        if (bgMusic != null) {
            bgMusic.seek(bgMusic.getStartTime());
            bgMusic.play();
        }
    }

    private double dist(double x1,double y1,double x2,double y2) {
        double dx=x1-x2,dy=y1-y2; return Math.sqrt(dx*dx+dy*dy);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Day / Night
    // ─────────────────────────────────────────────────────────────────────
    private double dayBrightness() {
        return (Math.cos(dayPhase * Math.PI * 2) + 1.0) / 2.0;
    }

    private Color skyTop() {
        double b = dayBrightness();
        if (b >= 0.5) { double t=(b-0.5)/0.5; return lerp(Color.web("#c26230"), Color.web("#5ba3e0"), t); }
        else          { double t=b/0.5;        return lerp(Color.web("#020614"), Color.web("#c26230"), t); }
    }

    private Color skyBot() {
        double b = dayBrightness();
        if (b >= 0.5) { double t=(b-0.5)/0.5; return lerp(Color.web("#e8824a"), Color.web("#c9e8fb"), t); }
        else          { double t=b/0.5;        return lerp(Color.web("#050a1a"), Color.web("#e8824a"), t); }
    }

    private Color tint(Color base) {
        double b = 0.35 + dayBrightness() * 0.65;
        return Color.color(base.getRed()*b, base.getGreen()*b, base.getBlue()*b, base.getOpacity());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Render
    // ─────────────────────────────────────────────────────────────────────
    private void render() {
        gc.clearRect(0, 0, W, H);
        switch (state) {
            case STORY       -> { drawGame(); drawStoryOverlay(); }
            case PLAYING     -> drawGame();
            case WIN, LOSE   -> { drawGame(); if (state==State.WIN) drawWinScreen(); else drawLoseScreen(); }
        }
    }

    private void drawGame() {
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
            new Stop(0, skyTop()), new Stop(1, skyBot())));
        gc.fillRect(0,0,W,H);
        drawStars();
        drawSunMoon();
        drawClouds();
        drawHills();
        drawGround();
        drawPlatforms();
        drawPipes();
        drawCoins();
        drawEnemies();
        drawFireballs();

        // Draw both volcanoes
        volcano.draw(gc, cameraX, dayBrightness(), imgVolcano);
        volcano2.draw(gc, cameraX, dayBrightness(), imgVolcano);

        // Draw shelter at finish line
        drawShelter();

        drawPlayer();
        drawHUD();
        if (isDead) drawDeathFlash();
    }

    // ── Shelter at finish line ────────────────────────────────────────────
    private void drawShelter() {
        double sx = FINISH_X - cameraX - SHELTER_W * 0.5;
        if (sx > W + 50 || sx < -SHELTER_W - 50) return;

        double sy = GROUND_Y - SHELTER_H;

        if (imgShelter != null) {
            // Draw shelter image
            gc.drawImage(imgShelter, sx, sy, SHELTER_W, SHELTER_H);
        } else {
            // Fallback procedural shelter — a simple hut shape
            double b = 0.4 + dayBrightness() * 0.6;

            // Walls
            gc.setFill(Color.color(0.6*b, 0.45*b, 0.28*b));
            gc.fillRect(sx + 10, sy + SHELTER_H * 0.4, SHELTER_W - 20, SHELTER_H * 0.6);

            // Roof (triangle)
            gc.setFill(Color.color(0.55*b, 0.25*b, 0.15*b));
            gc.fillPolygon(
                new double[]{sx, sx + SHELTER_W, sx + SHELTER_W * 0.5},
                new double[]{sy + SHELTER_H * 0.42, sy + SHELTER_H * 0.42, sy},
                3
            );

            // Door
            gc.setFill(Color.color(0.3*b, 0.18*b, 0.08*b));
            double dw = SHELTER_W * 0.25, dh = SHELTER_H * 0.4;
            gc.fillRoundRect(sx + SHELTER_W * 0.375, sy + SHELTER_H * 0.6, dw, dh, 6, 6);

            // SAFE ZONE label
            gc.setFill(Color.color(0.2*b, 0.8*b, 0.3*b, 0.9));
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
            gc.fillText("SAFE ZONE", sx + 12, sy - 8);
        }

        // Pulsing glow ring to draw attention
        double pulse = 0.4 + 0.3 * Math.sin(System.currentTimeMillis() / 400.0);
        gc.setFill(Color.color(0.3, 1.0, 0.4, pulse * 0.25));
        gc.fillRoundRect(sx - 10, sy - 10, SHELTER_W + 20, SHELTER_H + 14, 12, 12);
        gc.setStroke(Color.color(0.4, 1.0, 0.5, pulse * 0.6));
        gc.setLineWidth(2);
        gc.strokeRoundRect(sx - 10, sy - 10, SHELTER_W + 20, SHELTER_H + 14, 12, 12);
    }

    // ── Stars ─────────────────────────────────────────────────────────────
    private void drawStars() {
        double night = 1.0 - dayBrightness();
        if (night < 0.05) return;
        Random sr = new Random(12345);
        for (int i = 0; i < 120; i++) {
            double sx = sr.nextDouble() * W;
            double sy = sr.nextDouble() * H * 0.65;
            double sz = 0.8 + sr.nextDouble() * 1.6;
            double twinkle = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 600.0 + i);
            gc.setFill(Color.color(1,1,0.9, Math.min(night * twinkle, 1.0)));
            gc.fillOval(sx, sy, sz, sz);
        }
    }

    // ── Sun / Moon ────────────────────────────────────────────────────────
    private void drawSunMoon() {
        double b = dayBrightness();
        double angle = dayPhase * Math.PI * 2;
        double arcX = W * 0.5 + Math.cos(angle + Math.PI) * W * 0.45;
        double arcY = H * 0.5 - Math.sin(angle + Math.PI) * H * 0.7;
        if (b > 0.15) {
            double sunA = Math.min(b * 2, 1.0);
            RadialGradient sunG = new RadialGradient(0,0,0.5,0.5,0.5,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.color(1,0.97,0.6,sunA)), new Stop(0.5,Color.color(1,0.85,0.2,sunA*0.6)), new Stop(1,Color.TRANSPARENT));
            gc.setFill(sunG); gc.fillOval(arcX-40,arcY-40,80,80);
            gc.setFill(Color.color(1,0.95,0.5,sunA)); gc.fillOval(arcX-18,arcY-18,36,36);
        }
        if (b < 0.5) {
            double moonA = Math.min((0.5-b)*2.5, 1.0);
            double mAngle = angle + Math.PI;
            double mx = W * 0.5 + Math.cos(mAngle + Math.PI) * W * 0.4;
            double my = H * 0.5 - Math.sin(mAngle + Math.PI) * H * 0.65;
            gc.setFill(Color.color(0.95,0.95,0.85,moonA)); gc.fillOval(mx-20,my-20,40,40);
            gc.setFill(Color.color(0.1,0.12,0.18,moonA*0.7)); gc.fillOval(mx-12,my-16,30,30);
        }
    }

    private void drawClouds() {
        double b = 0.4 + dayBrightness() * 0.6;
        double[][] clouds = {{200,80,90},{500,60,70},{900,90,110},{1300,70,80},{1700,85,95},
            {2200,75,85},{2600,65,90},{3000,95,105},{3500,70,80},{4000,80,100},{4500,60,75},{5000,90,110}};
        for (double[] c : clouds) {
            double cx=c[0]-cameraX*0.3; if (cx<-120||cx>W+120) continue;
            double cy=c[1], r=c[2];
            gc.setFill(Color.color(b,b,b,0.85));
            gc.fillOval(cx-r*0.6,cy,r*1.2,r*0.6); gc.fillOval(cx-r*0.3,cy-r*0.3,r*0.8,r*0.5); gc.fillOval(cx+r*0.1,cy-r*0.25,r*0.7,r*0.45);
        }
    }

    private void drawHills() {
        gc.setFill(tint(Color.web("#4caa52")));
        double[][] hills = {{300,0.5,200},{700,0.4,180},{1200,0.5,220},{1800,0.4,170},
            {2500,0.5,200},{3200,0.45,190},{4000,0.5,210},{4800,0.4,180},{5500,0.5,200}};
        for (double[] h : hills) { double hx=h[0]-cameraX*h[1],r=h[2]; gc.fillOval(hx-r,GROUND_Y-r*0.55,r*2,r); }
    }

    private void drawGround() {
        double b = 0.4 + dayBrightness() * 0.6;
        gc.setFill(Color.color(0.545*b,0.369*b,0.235*b)); gc.fillRect(0,GROUND_Y,W,H-GROUND_Y);
        double tileW=50, startT=Math.floor(cameraX/tileW)*tileW;
        for (double tx=startT; tx<cameraX+W; tx+=tileW) {
            double sx=tx-cameraX;
            gc.setFill(Color.color(0.361*b,0.62*b,0.192*b)); gc.fillRect(sx,GROUND_Y,tileW-2,18);
            gc.setFill(Color.color(0.447*b,0.722*b,0.228*b));
            gc.fillOval(sx+10,GROUND_Y-5,12,8); gc.fillOval(sx+25,GROUND_Y-6,12,8);
        }
        gc.setFill(Color.color(0.478*b,0.31*b,0.165*b));
        for (double tx=startT; tx<cameraX+W; tx+=40) gc.fillRect(tx-cameraX,GROUND_Y+18,39,1);
    }

    private void drawPlatforms() {
        double b = 0.4 + dayBrightness() * 0.6;
        for (Platform pl : platforms) {
            double sx=pl.x-cameraX; if (sx>W+10||sx+pl.w<-10) continue;
            gc.setFill(Color.color(0.361*b,0.62*b,0.192*b)); gc.fillRect(sx,pl.y,pl.w,8);
            gc.setFill(Color.color(0.784*b,0.584*b,0.29*b)); gc.fillRect(sx,pl.y+8,pl.w,pl.h-8);
            gc.setFill(Color.color(0.627*b,0.44*b,0.188*b));
            gc.fillRect(sx,pl.y+8+10,pl.w,1); gc.fillRect(sx+pl.w/2.0,pl.y+8,1,pl.h-8);
            gc.setFill(Color.color(0,0,0,0.18)); gc.fillRect(sx+3,pl.y+pl.h,pl.w,4);
        }
    }

    private void drawPipes() {
        double b = 0.4 + dayBrightness() * 0.6;
        for (Pipe p : pipes) {
            double sx=p.x-cameraX; if (sx>W+10||sx+p.w<-10) continue;
            gc.setFill(Color.color(0.176*b,0.541*b,0.176*b)); gc.fillRect(sx+4,p.y+28,p.w-8,p.h-28);
            gc.setFill(Color.color(0.243*b,0.722*b,0.243*b)); gc.fillRect(sx+8,p.y+28,10,p.h-28);
            gc.setFill(Color.color(0.176*b,0.541*b,0.176*b)); gc.fillRect(sx-3,p.y,p.w+6,28);
            gc.setFill(Color.color(0.243*b,0.722*b,0.243*b)); gc.fillRect(sx+2,p.y+4,10,20);
        }
    }

    private void drawCoins() {
        long now = System.currentTimeMillis();
        double b = 0.5 + dayBrightness() * 0.5;
        for (Coin co : coins) {
            double sx=co.x-cameraX; if (sx<-20||sx>W+20) continue;
            double phase=(now%800)/800.0, scaleX=Math.abs(Math.cos(phase*Math.PI*2));
            gc.setFill(Color.color(b,0.843*b,0)); gc.fillOval(sx-8*scaleX,co.y-10,16*scaleX,20);
            gc.setFill(Color.color(b,0.945*b,0.463*b)); gc.fillOval(sx-5*scaleX,co.y-7,10*scaleX,14);
        }
    }

    private void drawEnemies() {
        for (Enemy en : enemies) {
            double sx = en.x - cameraX;
            if (sx < -40 || sx > W + 40) continue;

            double ew = en.r * 2.5;
            double eh = en.r * 2.5;

            if (en.dead) {
                if (sprEnemyDead != null) {
                    gc.save();
                    gc.setGlobalAlpha(0.85);
                    gc.drawImage(sprEnemyDead, sx - ew * 0.5, en.y - eh * 0.3, ew, eh * 0.45);
                    gc.restore();
                } else {
                    double b = 0.5 + dayBrightness() * 0.5;
                    gc.setFill(Color.color(0.8*b, 0.133*b, 0, 0.7));
                    gc.fillOval(sx - en.r, en.y - en.r * 0.4, en.r*2, en.r*0.8);
                }
            } else {
                if (sprEnemy != null) {
                    gc.save();
                    if (en.vx < 0) {
                        gc.drawImage(sprEnemy, sx - ew * 0.5, en.y - eh * 0.5, ew, eh);
                    } else {
                        gc.translate(sx + ew * 0.5, en.y - eh * 0.5);
                        gc.scale(-1, 1);
                        gc.drawImage(sprEnemy, 0, 0, ew, eh);
                    }
                    gc.restore();
                } else {
                    double b = 0.5 + dayBrightness() * 0.5;
                    gc.setFill(Color.color(0.8*b, 0.133*b, 0)); gc.fillOval(sx-en.r,en.y-en.r,en.r*2,en.r*2);
                    gc.setFill(Color.WHITE); gc.fillOval(sx-8,en.y-8,7,7); gc.fillOval(sx+1,en.y-8,7,7);
                    gc.setFill(Color.BLACK); gc.fillOval(sx-6,en.y-6,4,4); gc.fillOval(sx+3,en.y-6,4,4);
                    gc.setStroke(Color.color(0.533*b,0,0)); gc.setLineWidth(2);
                    gc.strokeLine(sx-9,en.y-12,sx-3,en.y-10); gc.strokeLine(sx+2,en.y-10,sx+8,en.y-12);
                }
            }
        }
    }

    private void drawFireballs() {
        for (Fireball fb : fireballs) {
            double sx=fb.x-cameraX; if (sx<-20||sx>W+20) continue;

            if (fb.fast) {
                // ── Fast red ball from volcano 2 ──────────────────────
                RadialGradient glow=new RadialGradient(0,0,0.5,0.5,0.5,true,CycleMethod.NO_CYCLE,
                    new Stop(0,Color.web("#ffffff")),new Stop(0.3,Color.web("#ff2200")),new Stop(1,Color.TRANSPARENT));
                gc.setFill(glow); gc.fillOval(sx-fb.r*2.5,fb.y-fb.r*2.5,fb.r*5,fb.r*5);
                gc.setFill(Color.web("#ff0000")); gc.fillOval(sx-fb.r,fb.y-fb.r,fb.r*2,fb.r*2);
                gc.setFill(Color.web("#ff8080")); gc.fillOval(sx-fb.r*0.4,fb.y-fb.r*0.4,fb.r*0.8,fb.r*0.8);
            } else {
                // ── Normal fireball ───────────────────────────────────
                RadialGradient glow=new RadialGradient(0,0,0.5,0.5,0.5,true,CycleMethod.NO_CYCLE,
                    new Stop(0,Color.web("#fff176")),new Stop(0.4,Color.web("#ff8c00")),new Stop(1,Color.TRANSPARENT));
                gc.setFill(glow); gc.fillOval(sx-fb.r*2,fb.y-fb.r*2,fb.r*4,fb.r*4);
                gc.setFill(Color.web("#ff4500")); gc.fillOval(sx-fb.r,fb.y-fb.r,fb.r*2,fb.r*2);
                gc.setFill(Color.web("#ffcc00")); gc.fillOval(sx-fb.r*0.5,fb.y-fb.r*0.5,fb.r,fb.r);
            }
        }
    }

    // ── Player ────────────────────────────────────────────────────────────
    private void drawPlayer() {
        double sx = player.x - cameraX;
        double r  = player.r;
        double sy = player.y;

        gc.setFill(Color.color(0,0,0,0.18));
        gc.fillOval(sx-r*0.8, GROUND_Y-5, r*1.6, 8);

        double scaleY = player.onGround ? 0.9 : 1.0 + Math.abs(player.vy) * 0.012;
        double scaleX = player.onGround ? 1.1 : 1.0 - Math.abs(player.vy) * 0.008;
        scaleY = Math.max(0.7, Math.min(1.3, scaleY));
        scaleX = Math.max(0.7, Math.min(1.3, scaleX));

        double drawW = r * 2 * scaleX;
        double drawH = r * 2 * scaleY;
        double yBob = (player.onGround && Math.abs(player.vx) > 0.5 && walkFrame == 1) ? -2 : 0;

        Image spr;
        if (!player.onGround) {
            spr = (player.facingRight) ? sprRightRun : sprLeftRun;
            if      (player.vx < -0.5) spr = sprLeftRun;
            else if (player.vx >  0.5) spr = sprRightRun;
        } else if (Math.abs(player.vx) > 0.5) {
            spr = player.facingRight ? sprRight : sprLeft;
        } else {
            spr = sprIdle;
        }

        if (spr != null) {
            gc.drawImage(spr, sx - drawW * 0.5, sy - drawH * 0.5 + yBob, drawW, drawH);
        } else {
            gc.setFill(Color.web("#FFE000"));
            gc.fillOval(sx-r*scaleX, sy-r*scaleY, r*2*scaleX, r*2*scaleY);
        }

        if (!player.onGround && player.jumpsLeft == 0) {
            gc.setFill(Color.color(1,0.945,0.463,0.8));
            for (int i=0;i<5;i++) {
                double ang=i*72.0;
                double ox=Math.cos(Math.toRadians(ang))*r*1.3, oy=Math.sin(Math.toRadians(ang))*r*1.3;
                gc.fillOval(sx+ox-3, sy+oy-3, 6,6);
            }
        }
    }

    private void drawHUD() {
        double b   = dayBrightness();
        String timeLabel = b>0.7?"☀ DAY":b>0.35?(dayPhase<0.5?"🌆 DUSK":"🌅 DAWN"):"🌙 NIGHT";
        gc.setFill(Color.color(0,0,0,0.45)); gc.fillRoundRect(8,8,340,70,12,12);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,20)); gc.setFill(Color.web("#FFD700"));
        gc.fillText("SCORE: "+score,22,35); gc.fillText("LIVES: "+"♥".repeat(Math.max(0,lives)),22,58);
        gc.setFill(Color.color(0,0,0,0.4)); gc.fillRoundRect(W-150,8,140,28,8,8);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,13)); gc.setFill(Color.WHITE); gc.fillText(timeLabel,W-140,26);

        gc.setFill(Color.color(0,0,0,0.45)); gc.fillRoundRect(W/2.0-200,12,400,20,10,10);
        gc.setFill(Color.web("#5cdb5c"));
        double progress=Math.min(1.0,player.x/FINISH_X);
        gc.fillRoundRect(W/2.0-198,14,396*progress,16,8,8);

        // Marker for volcano 1
        double v1p=VOLCANO_X/FINISH_X;
        gc.setFill(Color.web("#ff4400")); gc.fillRect(W/2.0-198+396*v1p-2,12,4,20);
        // Marker for volcano 2
        double v2p=VOLCANO2_X/FINISH_X;
        gc.setFill(Color.web("#cc0000")); gc.fillRect(W/2.0-198+396*v2p-2,12,4,20);
        // Marker for shelter/finish
        gc.setFill(Color.web("#00ee66")); gc.fillRect(W/2.0-198+396*0.99,12,4,20);

        gc.setFill(Color.WHITE); gc.setFont(Font.font("Courier New",FontWeight.BOLD,12));
        gc.setTextAlign(TextAlignment.CENTER); gc.fillText("PROGRESS",W/2.0,27); gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.color(1,1,1,0.5)); gc.setFont(Font.font("Courier New",11));
        gc.fillText("ESC: Back  |  ↑↑ Double Jump  |  Reach the SHELTER!",W-330,H-12);
    }

    private void drawDeathFlash() {
        gc.setFill(Color.color(1,0,0,0.3*Math.sin(deathTimer*10))); gc.fillRect(0,0,W,H);
    }

    private void drawStoryOverlay() {
        gc.setFill(Color.color(0,0,0,storyAlpha*0.82)); gc.fillRect(0,0,W,H);
        gc.save(); gc.setGlobalAlpha(storyAlpha);
        gc.setStroke(Color.web("#FFD700")); gc.setLineWidth(3);
        gc.strokeRoundRect(W*0.18,H*0.15,W*0.64,H*0.65,20,20);
        double cx=W/2.0; gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,42)); gc.setFill(Color.web("#FFD700"));
        gc.fillText("MUSHROOMVILE",cx,H*0.28);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,18)); gc.setFill(Color.web("#c9e8fb"));
        String[] lines={"A volcanic eruption has awakened","ancient lava beasts in Mushroomvile!","",
            "You are the last GOLDEN GUARDIAN —","brave the burning valley, cross the land,",
            "and reach the SHELTER before the","twin volcanoes swallow the world!","",
            "← → to move   ↑↑ to Double Jump","Stomp enemies • Collect coins • Survive!"};
        double ly=H*0.38;
        for (String line:lines){gc.fillText(line,cx,ly);ly+=30;}
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,15));
        gc.setFill(Color.color(1,1,1,0.6+0.4*Math.sin(storyTimer*4)));
        gc.fillText("Press ENTER to begin  or  wait...",cx,H*0.84);
        gc.restore(); gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawWinScreen() {
        gc.setFill(Color.color(0,0,0,endAlpha*0.78)); gc.fillRect(0,0,W,H);
        gc.save(); gc.setGlobalAlpha(endAlpha);
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#1a1a2e")),new Stop(1,Color.web("#16213e"))));
        gc.fillRoundRect(W*0.2,H*0.12,W*0.6,H*0.72,24,24);
        gc.setStroke(Color.web("#FFD700")); gc.setLineWidth(3); gc.strokeRoundRect(W*0.2,H*0.12,W*0.6,H*0.72,24,24);
        double cx=W/2.0; gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,18)); gc.setFill(Color.web("#FFD700")); gc.fillText("★ ★ ★  YOU MADE IT!  ★ ★ ★",cx,H*0.24);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,52)); gc.setFill(Color.web("#FFE000")); gc.fillText("SHELTER REACHED!",cx,H*0.40);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,20)); gc.setFill(Color.web("#a8d4f5"));
        gc.fillText("You survived the twin volcanoes.",cx,H*0.50);
        gc.fillText("Mushroomvile is saved!",cx,H*0.555);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,22)); gc.setFill(Color.web("#FFD700")); gc.fillText("SCORE:  "+score,cx,H*0.635);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,16)); gc.setFill(Color.WHITE); gc.fillText("★  Lives Remaining: "+Math.max(0,lives),cx,H*0.69);
        double pulse=0.85+0.15*Math.sin(System.currentTimeMillis()/300.0);
        gc.setFill(Color.web("#FFD700",pulse)); gc.fillRoundRect(cx-110,H*0.74,220,44,12,12);
        gc.setFill(Color.web("#1a1a2e")); gc.setFont(Font.font("Courier New",FontWeight.BOLD,18)); gc.fillText("[ R ] Play Again",cx,H*0.77);
        gc.setFill(Color.web("#aaaaaa")); gc.setFont(Font.font("Courier New",13)); gc.fillText("ESC  →  Back to World",cx,H*0.81);
        gc.restore(); gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawLoseScreen() {
        gc.setFill(Color.color(0,0,0,endAlpha*0.82)); gc.fillRect(0,0,W,H);
        gc.save(); gc.setGlobalAlpha(endAlpha);
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#1a0000")),new Stop(1,Color.web("#2e0000"))));
        gc.fillRoundRect(W*0.2,H*0.12,W*0.6,H*0.72,24,24);
        gc.setStroke(Color.web("#cc2200")); gc.setLineWidth(3); gc.strokeRoundRect(W*0.2,H*0.12,W*0.6,H*0.72,24,24);
        double cx=W/2.0; gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,18)); gc.setFill(Color.web("#cc2200")); gc.fillText("✦ ✦ ✦  GAME OVER  ✦ ✦ ✦",cx,H*0.24);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,52)); gc.setFill(Color.web("#ff3300")); gc.fillText("DEFEATED!",cx,H*0.40);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,20)); gc.setFill(Color.web("#f4a0a0"));
        gc.fillText("The volcanoes consumed Mushroomvile...",cx,H*0.50); gc.fillText("The Guardian has fallen.",cx,H*0.555);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,22)); gc.setFill(Color.web("#ffcc00")); gc.fillText("FINAL SCORE:  "+score,cx,H*0.635);
        double pulse=0.85+0.15*Math.sin(System.currentTimeMillis()/300.0);
        gc.setFill(Color.web("#cc2200",pulse)); gc.fillRoundRect(cx-110,H*0.74,220,44,12,12);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Courier New",FontWeight.BOLD,18)); gc.fillText("[ R ] Try Again",cx,H*0.77);
        gc.setFill(Color.web("#aaaaaa")); gc.setFont(Font.font("Courier New",13)); gc.fillText("ESC  →  Back to World",cx,H*0.81);
        gc.restore(); gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Utility
    // ─────────────────────────────────────────────────────────────────────
    private Color lerp(Color a, Color b, double t) {
        t=Math.max(0,Math.min(1,t));
        return Color.color(a.getRed()+(b.getRed()-a.getRed())*t, a.getGreen()+(b.getGreen()-a.getGreen())*t, a.getBlue()+(b.getBlue()-a.getBlue())*t);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Inner classes
    // ─────────────────────────────────────────────────────────────────────

    static class PlayerObj {
        double x, y, vx, vy;
        final double r = 22;
        boolean onGround    = false;
        boolean facingRight = true;
        int jumpsLeft = 2;

        PlayerObj(double x, double y) { this.x=x; this.y=y; }

        boolean jump() {
            if (jumpsLeft > 0) { vy = JUMP_FORCE; jumpsLeft--; onGround = false; return true; }
            return false;
        }
    }

    static class Platform { double x,y,w,h; Platform(double x,double y,double w,double h){this.x=x;this.y=y;this.w=w;this.h=h;} }
    static class Pipe     { double x,y,w,h; Pipe    (double x,double y,double w,double h){this.x=x;this.y=y;this.w=w;this.h=h;} }
    static class Coin     { double x,y;     Coin    (double x,double y){this.x=x;this.y=y;} }

    static class Enemy {
        double x, y, vx;
        final double r = 18;
        boolean dead = false;
        Enemy(double x, double y) { this.x=x; this.y=y; this.vx=-1.2; }
        void update(double dt, List<Platform> platforms) {
            x+=vx;
            for (Platform pl : platforms) {
                if (x+r>pl.x&&x-r<pl.x+pl.w&&y+r>=pl.y&&y-r<pl.y+pl.h) {
                    if (x+r>pl.x+pl.w){vx=-Math.abs(vx);x=pl.x+pl.w-r;}
                    else if(x-r<pl.x){vx=Math.abs(vx);x=pl.x+r;}
                }
            }
            if (x<50||x>FINISH_X-50) vx=-vx;
        }
    }

    /** Fireball — supports both normal and fast (volcano2) variants. */
    static class Fireball {
        double x,y,vx,vy;
        final double r;
        final boolean fast;  // true = fast red ball from volcano2

        Fireball(double x, double y, double vx, double vy, boolean fast) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.fast=fast;
            this.r = fast ? 7 : 9;
        }
        void update(double dt) { vy += 0.25; x+=vx; y+=vy; }
    }

    /** Volcano — fast=true makes it a fast red-ball second volcano. */
    static class VolcanoObj {
        double x, y;
        double shootTimer=0, shootInterval;
        final boolean fast;

        VolcanoObj(double x, double y, boolean fast) {
            this.x=x; this.y=y; this.fast=fast;
            // Fast volcano shoots much more frequently
            this.shootInterval = fast ? 0.9 : 2.2;
        }

        void update(double dt, List<Fireball> fireballs, Random rng) {
            shootTimer += dt;
            if (shootTimer >= shootInterval) {
                shootTimer = 0;
                if (fast) {
                    // Rapid shotgun burst of fast red balls
                    shootInterval = 0.6 + rng.nextDouble() * 0.6;
                    for (int i = -2; i <= 2; i++) {
                        double angle = Math.toRadians(-90 + i * 14);
                        double speed = 9.0 + rng.nextDouble() * 4.0; // high speed
                        fireballs.add(new Fireball(x+55, y+20,
                            Math.cos(angle)*speed, Math.sin(angle)*speed, true));
                    }
                } else {
                    shootInterval = 1.8 + rng.nextDouble() * 1.2;
                    for (int i = -1; i <= 1; i++) {
                        double angle = Math.toRadians(-80 + i * 18);
                        double speed = 5.5 + rng.nextDouble() * 2;
                        fireballs.add(new Fireball(x+55, y+20,
                            Math.cos(angle)*speed, Math.sin(angle)*speed, false));
                    }
                }
            }
        }

        void draw(GraphicsContext gc, double cameraX, double dayBrightness, Image imgVolcano) {
            double sx=x-cameraX; if (sx>1500||sx<-300) return;
            double w=110, h=280, sy=y, b=0.5+dayBrightness*0.5;

            if (imgVolcano != null) {
                // Draw the volcano image
                gc.drawImage(imgVolcano, sx, sy, w + 10, h);
                // Still add the lava glow on top
                double glowPulse=0.7+0.3*Math.sin(System.currentTimeMillis()/250.0);
                RadialGradient lavaGlow=new RadialGradient(0,0,0.5,0.5,0.5,true,CycleMethod.NO_CYCLE,
                    new Stop(0, fast ? Color.color(1,0.1,0.1,0.9*glowPulse) : Color.color(1,0.6,0.1,0.9*glowPulse)),
                    new Stop(0.5, fast ? Color.color(0.9,0,0,0.5*glowPulse) : Color.color(1,0.3,0.05,0.5*glowPulse)),
                    new Stop(1,Color.TRANSPARENT));
                gc.setFill(lavaGlow); gc.fillOval(sx+w*0.5-55,sy-45,110,80);
            } else {
                // Procedural fallback
                gc.setFill(Color.color(0,0,0,0.25)); gc.fillOval(sx-30,sy+h-8,w+60,22);
                gc.setFill(Color.color(0.255*b,0.196*b,0.137*b));
                gc.fillPolygon(new double[]{sx+w*0.5,sx-40,sx+w+40},new double[]{sy,sy+h,sy+h},3);
                gc.setFill(Color.color(0.318*b,0.247*b,0.18*b));
                gc.fillPolygon(new double[]{sx+w*0.5,sx+20,sx+w*0.5-15},new double[]{sy,sy+h*0.55,sy+h*0.65},3);
                gc.setStroke(fast ? Color.color(1,0.1,0.05,0.85) : Color.color(1,0.4,0.05,0.75));
                gc.setLineWidth(5);
                gc.strokeLine(sx+w*0.5,sy+30,sx+20,sy+h*0.7); gc.strokeLine(sx+w*0.5,sy+30,sx+w-10,sy+h*0.65);
                double glowPulse=0.7+0.3*Math.sin(System.currentTimeMillis()/250.0);
                RadialGradient lavaGlow=new RadialGradient(0,0,0.5,0.5,0.5,true,CycleMethod.NO_CYCLE,
                    new Stop(0, fast ? Color.color(1,0.1,0.1,0.9*glowPulse) : Color.color(1,0.6,0.1,0.9*glowPulse)),
                    new Stop(0.5, fast ? Color.color(0.9,0,0,0.5*glowPulse) : Color.color(1,0.3,0.05,0.5*glowPulse)),
                    new Stop(1,Color.TRANSPARENT));
                gc.setFill(lavaGlow); gc.fillOval(sx+w*0.5-55,sy-45,110,80);
                gc.setFill(fast ? Color.color(0.2,0.0,0.0,1) : Color.color(0.2,0.18,0.16,1));
                gc.fillOval(sx+w*0.5-32,sy-12,64,26);
                gc.setFill(fast ? Color.web("#cc0000") : Color.color(1,0.27,0,1));
                gc.fillOval(sx+w*0.5-20,sy-8,40,18);
            }

            // Smoke puffs
            long t=System.currentTimeMillis();
            for (int i=0;i<4;i++) {
                double phase=((t/900.0+i*0.25)%1.0),smokeR=12+phase*28,smokeY2=sy-20-phase*70,smokeA=0.4*(1-phase);
                gc.setFill(fast ? Color.color(0.6,0.1,0.1,smokeA) : Color.color(0.5,0.48,0.45,smokeA));
                gc.fillOval(sx+w*0.5-smokeR,smokeY2-smokeR,smokeR*2,smokeR*2);
            }

            // Label
            String label = fast ? "⚠ DANGER ZONE" : "⚠ VOLCANO";
            gc.setFill(fast ? Color.color(1,0.2,0.2,0.9) : Color.color(1,0.843,0,0.9));
            gc.setFont(Font.font("Courier New",FontWeight.BOLD,14));
            gc.fillText(label, sx - (fast ? 15 : 8), sy+h+24);
        }
    }
}
