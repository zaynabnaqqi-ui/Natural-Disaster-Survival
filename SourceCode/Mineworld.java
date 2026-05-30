import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.*;

public class Mineworld extends Pane {

    static final int W = 1310, H = 730;

    static final double GRAVITY        = 0.55;
    static final double MAX_FALL       = 14.0;
    static final double MOVE_SPEED     = 3.8;
    static final double JUMP_FORCE     = -13.0;
    static final double FRICTION       = 0.82;
    static final int    PLAYER_W       = 36;
    static final int    PLAYER_H       = 44;
    static final int    MAX_LIVES      = 3;
    static final double INVINCIBLE_T   = 2.0;
    static final int    METEOR_W       = 38;
    static final int    METEOR_H       = 38;
    static final double EQ_SHAKE_AMT   = 6.0;
    static final double EQ_SLIP_FORCE  = 1.6;
    static final int    CRYSTAL_SCORE  = 10;
    static final int    LEVEL_SCORE    = 500;
    static final int    MAX_LEVEL      = 3;

    // ── Double jump constants ─────────────────────────────────────────────
    static final int    MAX_JUMPS      = 2;
    static final double DOUBLE_JUMP_FORCE = -11.0;

    enum State { STORY, PLAYING, PAUSED, GAME_OVER, VICTORY }

    private final Stage       parentStage;
    private final WorldScreen prevScreen;
    private final Canvas      canvas;
    private final GraphicsContext gc;

    // ── Fireboy sprites ───────────────────────────────────────────────────
    private Image imgFbIdle, imgFbLeft, imgFbRight, imgFbUp, imgFbDown;
    // ── Watergirl sprites ─────────────────────────────────────────────────
    private Image imgWgIdle, imgWgLeft, imgWgRight, imgWgUp, imgWgDown;
    // ── Crystal images ────────────────────────────────────────────────────
    private Image imgRedCrystal, imgBlueCrystal;
    // ── Door images ───────────────────────────────────────────────────────
    private Image imgRedDoor, imgBlueDoor;
    // ── Other images ──────────────────────────────────────────────────────
    private Image imgMeteor, imgBackground, imgPlatformTile;
    private Image imgStoryBg;
    // ── Sounds ────────────────────────────────────────────────────────────
    private AudioClip sndJump, sndDoubleJump, sndMeteor, sndEarthquake;
    private AudioClip sndDamage, sndCrystal, sndDoorEnter, sndGameOver, sndVictory;
    private MediaPlayer bgMusic;

    private AnimationTimer gameLoop;
    private long lastTime = 0;

    private final Set<KeyCode> keys = new HashSet<>();

    private int   currentLevel = 1;
    private State state        = State.STORY;
    private int   totalScore   = 0;

    private MWPlayer  fireboy, watergirl;
    private final List<MWPlayer>   players   = new ArrayList<>();
    private final List<MWPlatform> platforms = new ArrayList<>();
    private final List<MWCrystal>  crystals  = new ArrayList<>();
    private final List<MWMeteor>   meteors   = new ArrayList<>();
    private MWExitDoor exitDoor;

    private double meteorTimer    = 1.5;
    private int    meteorMaxCount = 3;
    private double meteorSpeed    = 3.5;
    private double meteorInterval = 2.5;
    private final Random rng = new Random();

    private double eqShakeX = 0, eqShakeY = 0;
    private boolean eqActive  = false;
    private boolean eqWarning = false;
    private double  eqShakeTimer   = 0;
    private double  eqNextTimer    = 5;
    private double  eqWarnTimer    = 0;
    private double  eqDuration     = 2.0;
    private double  eqIntensity    = 0.5;
    private double  eqIntervalMin  = 6.0;
    private double  eqIntervalMax  = 12.0;
    private double  eqCrackAlpha   = 0;

    private long storyStartMs = 0;
    private static final String[] STORY_TITLES = {
        "LEVEL 1  —  THE MINE SHAKES",
        "LEVEL 2  —  DEEPER INTO RUIN",
        "LEVEL 3  —  THE FINAL ESCAPE"
    };
    private static final String[] STORY_BODIES = {
        "An ancient mine is collapsing after a massive earthquake.\nFireboy and Watergirl must work together to escape!\n\nDodge falling meteors, collect crystals, and reach the EXIT DOORS.",
        "The tremors grow stronger as you descend deeper.\nBeware of moving platforms over deadly drops!\n\nMeteors rain faster — stay alert and keep moving.",
        "Volcanic activity erupts! Extreme earthquakes!\nThe ground itself tears apart beneath your feet.\n\nMove fast. Jump true. SURVIVE."
    };

    private boolean storyBtnHovered = false;
    private boolean backBtnHovered  = false;

    // ── Platform tile animation ───────────────────────────────────────────
    private double tileAnimTimer = 0;

    // ══════════════════════════════════════════════════════════════════════
    public Mineworld(Stage stage, WorldScreen prev) {
        this.parentStage = stage;
        this.prevScreen  = prev;

        canvas = new Canvas(W, H);
        gc     = canvas.getGraphicsContext2D();
        getChildren().add(canvas);
        setFocusTraversable(true);

        loadAssets();
        loadLevel(1);
        hookMouseInput();

        startLoop();
        javafx.application.Platform.runLater(this::requestFocus);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Asset loading
    // ══════════════════════════════════════════════════════════════════════
    private void loadAssets() {
        imgFbIdle  = img("Assets/fireboywatergirl/fb_idle.png");
        imgFbLeft  = img("Assets/fireboywatergirl/fb_left.png");
        imgFbRight = img("Assets/fireboywatergirl/fb_right.png");
        imgFbUp    = img("Assets/fireboywatergirl/fb_up.png");
        imgFbDown  = img("Assets/fireboywatergirl/fb_down.png");

        imgWgIdle  = img("Assets/fireboywatergirl/wg_idle.png");
        imgWgLeft  = img("Assets/fireboywatergirl/wg_left.png");
        imgWgRight = img("Assets/fireboywatergirl/wg_right.png");
        imgWgUp    = img("Assets/fireboywatergirl/wg_up.png");
        imgWgDown  = img("Assets/fireboywatergirl/wg_down.png");

        imgRedCrystal  = img("Assets/fireboywatergirl/redcrystal.png");
        imgBlueCrystal = img("Assets/fireboywatergirl/bluecrystal.png");

        // ── Door images ────────────────────────────────────────────────
        imgRedDoor  = img("Assets/fireboywatergirl/reddoor.png");
        imgBlueDoor = img("Assets/fireboywatergirl/bluedoor.png");

        imgMeteor       = img("Assets/meteor.png");
        imgBackground   = img("Assets/mineBackground.png");
        imgPlatformTile = img("Assets/fireboywatergirl/platform.png");
        imgStoryBg      = img("Assets/fireboywatergirl/bg.png");

        sndJump       = clip("Assets/fireboywatergirl/jump.wav");
        sndDoubleJump = clip("Assets/fireboywatergirl/doublejump.wav");
        sndMeteor     = clip("Assets/fireboywatergirl/meteor.wav");
        sndEarthquake = clip("Assets/fireboywatergirl/earthquake.wav");
        sndDamage     = clip("Assets/fireboywatergirl/damage.wav");
        sndCrystal    = clip("Assets/fireboywatergirl/crystal.wav");
        sndDoorEnter  = clip("Assets/fireboywatergirl/doorenter.wav");
        sndGameOver   = clip("Assets/fireboywatergirl/gameover.wav");
        sndVictory    = clip("Assets/fireboywatergirl/victory.wav");

        File mf = new File("Assets/fireboywatergirl/background.wav");
        if (mf.exists()) {
            bgMusic = new MediaPlayer(new Media(mf.toURI().toString()));
            bgMusic.setCycleCount(MediaPlayer.INDEFINITE);
            bgMusic.setVolume(0.6);
            bgMusic.play();
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
    private void play(AudioClip c, double vol) { if (c != null) c.play(vol); }

    // ══════════════════════════════════════════════════════════════════════
    // Level loading
    // ══════════════════════════════════════════════════════════════════════
    private void loadLevel(int lvl) {
        currentLevel = lvl;
        meteors.clear();
        platforms.clear();
        crystals.clear();
        meteorTimer = 1.5;

        switch (lvl) {
            case 1 -> buildLevel1();
            case 2 -> buildLevel2();
            case 3 -> buildLevel3();
            default -> buildLevel1();
        }

        double[][] eqTuning = {
            {6.0, 12.0, 2.0, 0.5},
            {4.0,  8.0, 3.0, 0.85},
            {2.5,  5.0, 4.5, 1.3}
        };
        int ei = Math.min(lvl - 1, 2);
        eqIntervalMin = eqTuning[ei][0];
        eqIntervalMax = eqTuning[ei][1];
        eqDuration    = eqTuning[ei][2];
        eqIntensity   = eqTuning[ei][3];
        eqNextTimer   = 5;
        eqActive      = false;
        eqWarning     = false;
        eqShakeX      = 0;
        eqShakeY      = 0;

        state = State.STORY;
        storyStartMs = System.currentTimeMillis();
    }

    private void buildLevel1() {
        meteorMaxCount = 3; meteorSpeed = 3.5; meteorInterval = 2.5;
        double sx = W / 960.0, sy = H / 640.0;

        platforms.add(new MWPlatform(0, scale(610,sy), W, scale(30,sy)));
        platforms.add(new MWPlatform(scale(50,sx),  scale(480,sy), scale(180,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(50,sx),  scale(350,sy), scale(150,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(100,sx), scale(220,sy), scale(180,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(340,sx), scale(520,sy), scale(200,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(310,sx), scale(400,sy), scale(160,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(380,sx), scale(280,sy), scale(180,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(300,sx), scale(160,sy), scale(220,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(650,sx), scale(490,sy), scale(180,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(700,sx), scale(370,sy), scale(150,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(660,sx), scale(240,sy), scale(180,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(800,sx), scale(200,sy), scale(140,sx), scale(20,sy)));

        addCrystals("fire",  sx,sy, new double[][]{{140,465},{120,335},{190,205}});
        addCrystals("water", sx,sy, new double[][]{{430,505},{390,385},{460,265}});
        addCrystals("any",   sx,sy, new double[][]{{730,475},{770,355}});

        exitDoor = new MWExitDoor(scale(820,sx), scale(140,sy));

        fireboy   = new MWPlayer("Fireboy",   Color.web("#e84020"), Color.web("#ff8040"),
                                  imgFbIdle, imgFbLeft, imgFbRight, imgFbUp, imgFbDown,
                                  scale(80,sx),  scale(560,sy));
        watergirl = new MWPlayer("Watergirl", Color.web("#2080e8"), Color.web("#40c8ff"),
                                  imgWgIdle, imgWgLeft, imgWgRight, imgWgUp, imgWgDown,
                                  scale(180,sx), scale(560,sy));
        resetPlayers();
    }

    private void buildLevel2() {
        meteorMaxCount = 5; meteorSpeed = 5.0; meteorInterval = 1.8;
        double sx = W / 960.0, sy = H / 640.0;

        platforms.add(new MWPlatform(0, scale(610,sy), W, scale(30,sy)));
        platforms.add(new MWPlatform(scale(80,sx),  scale(540,sy), scale(140,sx), scale(20,sy), true, scale(80,sx),  scale(260,sx), 0.012));
        platforms.add(new MWPlatform(scale(350,sx), scale(460,sy), scale(120,sx), scale(20,sy), true, scale(320,sx), scale(520,sx), 0.015));
        platforms.add(new MWPlatform(scale(620,sx), scale(510,sy), scale(140,sx), scale(20,sy), true, scale(580,sx), scale(760,sx), 0.013));
        platforms.add(new MWPlatform(scale(120,sx), scale(400,sy), scale(130,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(310,sx), scale(320,sy), scale(150,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(500,sx), scale(250,sy), scale(140,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(680,sx), scale(310,sy), scale(120,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(750,sx), scale(180,sy), scale(170,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(200,sx), scale(220,sy), scale(160,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(420,sx), scale(140,sy), scale(200,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(820,sx), scale(165,sy), scale(130,sx), scale(20,sy)));

        addCrystals("fire",  sx,sy, new double[][]{{160,385},{370,305},{540,235}});
        addCrystals("water", sx,sy, new double[][]{{670,295},{760,165},{230,205}});
        addCrystals("any",   sx,sy, new double[][]{{470,125},{830,165}});

        exitDoor = new MWExitDoor(scale(840,sx), scale(120,sy));

        fireboy   = new MWPlayer("Fireboy",   Color.web("#e84020"), Color.web("#ff8040"),
                                  imgFbIdle, imgFbLeft, imgFbRight, imgFbUp, imgFbDown,
                                  scale(60,sx),  scale(555,sy));
        watergirl = new MWPlayer("Watergirl", Color.web("#2080e8"), Color.web("#40c8ff"),
                                  imgWgIdle, imgWgLeft, imgWgRight, imgWgUp, imgWgDown,
                                  scale(160,sx), scale(555,sy));
        resetPlayers();
    }

    private void buildLevel3() {
        meteorMaxCount = 8; meteorSpeed = 7.0; meteorInterval = 1.0;
        double sx = W / 960.0, sy = H / 640.0;

        platforms.add(new MWPlatform(0,             scale(610,sy), scale(200,sx), scale(30,sy)));
        platforms.add(new MWPlatform(scale(760,sx), scale(610,sy), scale(200,sx), scale(30,sy)));
        platforms.add(new MWPlatform(scale(160,sx), scale(540,sy), scale(100,sx), scale(20,sy), true, scale(160,sx), scale(380,sx), 0.02));
        platforms.add(new MWPlatform(scale(450,sx), scale(560,sy), scale(100,sx), scale(20,sy), true, scale(380,sx), scale(600,sx), 0.018));
        platforms.add(new MWPlatform(scale(650,sx), scale(530,sy), scale(120,sx), scale(20,sy), true, scale(580,sx), scale(760,sx), 0.02));
        platforms.add(new MWPlatform(scale(80,sx),  scale(440,sy), scale(120,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(240,sx), scale(380,sy), scale(110,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(400,sx), scale(310,sy), scale(120,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(560,sx), scale(360,sy), scale(110,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(720,sx), scale(280,sy), scale(130,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(180,sx), scale(260,sy), scale(100,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(340,sx), scale(190,sy), scale(120,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(530,sx), scale(140,sy), scale(110,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(700,sx), scale(160,sy), scale(140,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(50,sx),  scale(160,sy), scale(110,sx), scale(20,sy)));
        platforms.add(new MWPlatform(scale(816,sx), scale(145,sy), scale(130,sx), scale(20,sy)));

        addCrystals("fire",  sx,sy, new double[][]{{135,425},{290,365},{455,295},{725,265}});
        addCrystals("water", sx,sy, new double[][]{{240,365},{400,295},{565,345},{190,245}});
        addCrystals("any",   sx,sy, new double[][]{{360,175},{545,125},{735,145}});

        exitDoor = new MWExitDoor(scale(836,sx), scale(100,sy));

        fireboy   = new MWPlayer("Fireboy",   Color.web("#e84020"), Color.web("#ff8040"),
                                  imgFbIdle, imgFbLeft, imgFbRight, imgFbUp, imgFbDown,
                                  scale(30,sx),  scale(560,sy));
        watergirl = new MWPlayer("Watergirl", Color.web("#2080e8"), Color.web("#40c8ff"),
                                  imgWgIdle, imgWgLeft, imgWgRight, imgWgUp, imgWgDown,
                                  scale(100,sx), scale(560,sy));
        resetPlayers();
    }

    private double scale(double v, double s) { return v * s; }

    private void addCrystals(String type, double sx, double sy, double[][] positions) {
        for (double[] p : positions)
            crystals.add(new MWCrystal(scale(p[0],sx), scale(p[1],sy), type));
    }

    private void resetPlayers() {
        players.clear();
        players.add(fireboy);
        players.add(watergirl);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Input
    // ══════════════════════════════════════════════════════════════════════
    public void onKeyDown(KeyEvent e) {
        KeyCode code = e.getCode();

        if (code == KeyCode.ESCAPE) {
            exitGame();
            return;
        }

        if (code == KeyCode.SPACE) {
            if (state == State.PLAYING) { state = State.PAUSED; return; }
            if (state == State.PAUSED)  { state = State.PLAYING; return; }
        }

        if (!keys.contains(code)) {
            if (code == KeyCode.W && state == State.PLAYING) {
                fireboy.tryJump(this);
            }
            if (code == KeyCode.UP && state == State.PLAYING) {
                watergirl.tryJump(this);
            }
        }

        keys.add(code);
    }

    public void onKeyUp(KeyEvent e) {
        keys.remove(e.getCode());
    }

    private void applyInput() {
        fireboy.keyLeft  = keys.contains(KeyCode.A);
        fireboy.keyRight = keys.contains(KeyCode.D);

        watergirl.keyLeft  = keys.contains(KeyCode.LEFT);
        watergirl.keyRight = keys.contains(KeyCode.RIGHT);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mouse
    // ══════════════════════════════════════════════════════════════════════
    private void hookMouseInput() {
        canvas.setOnMouseMoved(e -> {
            storyBtnHovered = inStoryStartBtn(e.getX(), e.getY());
            backBtnHovered  = inStoryBackBtn(e.getX(), e.getY());
        });
        canvas.setOnMouseClicked(e -> {
            if (state == State.STORY) {
                if (inStoryStartBtn(e.getX(), e.getY())) startPlaying();
                else if (inStoryBackBtn(e.getX(), e.getY())) exitGame();
            }
        });
    }

    private boolean inStoryStartBtn(double mx, double my) {
        return mx >= W/2.0-140 && mx <= W/2.0+140 && my >= 420 && my <= 480;
    }
    private boolean inStoryBackBtn(double mx, double my) {
        return mx >= 30 && mx <= 170 && my >= 30 && my <= 70;
    }

    private void startPlaying() {
        state = State.PLAYING;
        lastTime = 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Game loop
    // ══════════════════════════════════════════════════════════════════════
    private void startLoop() {
        gameLoop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastTime == 0) { lastTime = now; }
                double dt = Math.min((now - lastTime) / 1_000_000_000.0, 0.05);
                lastTime = now;
                tileAnimTimer += dt;
                update(dt);
                render();
            }
        };
        gameLoop.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Update
    // ══════════════════════════════════════════════════════════════════════
    private void update(double dt) {
        if (state != State.PLAYING) return;

        applyInput();
        updateEarthquake(dt);

        for (MWPlayer p : players) p.update(dt, eqActive);

        for (MWPlayer p : players) {
            resolvePlatforms(p);
            resolveGround(p);
        }

        for (MWPlatform plat : platforms) plat.update(dt, eqActive, 3.0 + currentLevel * 2.0);

        spawnMeteors(dt);
        for (MWMeteor m : meteors) m.update(dt);
        meteors.removeIf(m -> !m.active || m.y > H + 60);

        for (MWMeteor m : meteors) {
            if (!m.landed && m.y + METEOR_H >= H - 30) {
                m.y = H - 30 - METEOR_H;
                m.impact(); play(sndMeteor, 0.6);
            }
            if (!m.landed) {
                for (MWPlatform plat : platforms) {
                    if (overlaps(m.hitRect(), plat.rect()) && m.vy > 0) {
                        m.impact(); play(sndMeteor, 0.6);
                    }
                }
            }
        }

        for (MWMeteor m : meteors) {
            if (!m.active || m.landed) continue;
            for (MWPlayer p : players) {
                if (!p.alive) continue;
                if (overlaps(p.rect(), m.hitRect())) {
                    if (p.takeDamage()) play(sndDamage, 0.7);
                }
            }
        }

        for (MWCrystal c : crystals) c.update(dt);
        for (MWCrystal c : crystals) {
            if (c.collected) continue;
            for (MWPlayer p : players) {
                if (!p.alive) continue;
                if (!overlaps(p.rect(), c.rect())) continue;
                boolean can = c.type.equals("any")
                    || (c.type.equals("fire")  && p.name.equals("Fireboy"))
                    || (c.type.equals("water") && p.name.equals("Watergirl"));
                if (can) {
                    c.collected = true;
                    p.score += CRYSTAL_SCORE;
                    play(sndCrystal, 0.7);
                }
            }
        }

        exitDoor.update(dt);
        boolean fbWasIn = exitDoor.fireboyIn, wgWasIn = exitDoor.watergirlIn;
        exitDoor.fireboyIn   = fireboy.alive   && overlaps(fireboy.rect(),   exitDoor.fireRect());
        exitDoor.watergirlIn = watergirl.alive && overlaps(watergirl.rect(), exitDoor.waterRect());

        if ((exitDoor.fireboyIn && !fbWasIn) || (exitDoor.watergirlIn && !wgWasIn)) {
            play(sndDoorEnter, 0.8);
        }

        if (exitDoor.fireboyIn && exitDoor.watergirlIn) {
            totalScore += fireboy.score + watergirl.score + LEVEL_SCORE;
            if (currentLevel < MAX_LEVEL) {
                loadLevel(currentLevel + 1);
            } else {
                state = State.VICTORY;
                play(sndVictory);
                stopBgMusic();
            }
        }

        if (!fireboy.alive || !watergirl.alive) {
            state = State.GAME_OVER;
            play(sndGameOver);
            stopBgMusic();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Earthquake
    // ══════════════════════════════════════════════════════════════════════
    private void updateEarthquake(double dt) {

        // ── Warning phase ─────────────────────────────────────────────
        if (eqWarning) {
            eqWarnTimer -= dt;
            eqShakeX = 0;
            eqShakeY = 0;
            if (eqWarnTimer <= 0) {
                eqWarning = false;
                eqActive = true;
                eqShakeTimer = eqDuration;
                play(sndEarthquake, 0.8);
            }
            return;
        }

        // ── Active earthquake ─────────────────────────────────────────
        if (eqActive) {
            eqShakeTimer -= dt;
            double maxShake = EQ_SHAKE_AMT * eqIntensity;
            eqShakeX = (rng.nextDouble() - 0.5) * 2 * maxShake;
            eqShakeY = (rng.nextDouble() - 0.5) * 2 * maxShake * 0.5;
            eqCrackAlpha = Math.min(0.35, eqCrackAlpha + dt * 0.3);

            for (MWPlayer p : players) {
                if (p.alive) {
                    p.slipForce = (rng.nextDouble() - 0.5) * EQ_SLIP_FORCE * eqIntensity;
                }
            }

            if (eqShakeTimer <= 0) {
                eqActive = false;
                eqShakeX = 0;
                eqShakeY = 0;
                eqCrackAlpha = 0;
                for (MWPlayer p : players) p.slipForce = 0;
                scheduleNextEq();
            }

        } else {
            // ── Idle — absolutely no shake ────────────────────────────
            eqShakeX = 0;
            eqShakeY = 0;
            eqCrackAlpha = 0;
            for (MWPlayer p : players) p.slipForce = 0;

            eqNextTimer -= dt;
            if (eqNextTimer <= 0) {
                eqWarning = true;
                eqWarnTimer = 1.2;
            }
        }
    }

    private void scheduleNextEq() {
        eqNextTimer = eqIntervalMin + rng.nextDouble() * (eqIntervalMax - eqIntervalMin);
    }

    private void spawnMeteors(double dt) {
        meteorTimer -= dt;
        if (meteorTimer <= 0) {
            meteorTimer = meteorInterval * (0.7 + rng.nextDouble() * 0.6);
            if (meteors.size() < meteorMaxCount) {
                double sx = 30 + rng.nextDouble() * (W - 60);
                meteors.add(new MWMeteor(sx, meteorSpeed, currentLevel));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Collision
    // ══════════════════════════════════════════════════════════════════════
    private void resolvePlatforms(MWPlayer p) {
        p.onGround = false;
        for (MWPlatform plat : platforms) {
            double[] pb    = p.rectArr();
            double[] platb = plat.rectArr();
            if (!overlapsArr(pb, platb)) continue;

            double overlapLeft   = pb[0]+pb[2] - platb[0];
            double overlapRight  = platb[0]+platb[2] - pb[0];
            double overlapTop    = pb[1]+pb[3] - platb[1];
            double overlapBottom = platb[1]+platb[3] - pb[1];

            boolean fromTop = overlapTop < overlapBottom;
            double minH = Math.min(overlapLeft, overlapRight);
            double minV = Math.min(overlapTop,  overlapBottom);

            if (minV < minH) {
                if (fromTop && p.vy >= 0) {
                    p.y = platb[1] - PLAYER_H;
                    p.vy = 0; p.onGround = true;
                    p.jumpsLeft = MAX_JUMPS;
                } else if (!fromTop && p.vy < 0) {
                    p.y = platb[1] + platb[3];
                    p.vy = 1;
                }
            } else {
                if (overlapLeft < overlapRight) { p.x = platb[0] - PLAYER_W; p.vx = 0; }
                else                            { p.x = platb[0] + platb[2]; p.vx = 0; }
            }
        }
    }

    private void resolveGround(MWPlayer p) {
        if (p.y + PLAYER_H >= H - 30) {
            p.y = H - 30 - PLAYER_H;
            p.vy = 0; p.onGround = true;
            p.jumpsLeft = MAX_JUMPS;
        }
    }

    private boolean overlaps(double[] a, double[] b) {
        return a[0] < b[0]+b[2] && a[0]+a[2] > b[0] && a[1] < b[1]+b[3] && a[1]+a[3] > b[1];
    }
    private boolean overlapsArr(double[] a, double[] b) { return overlaps(a, b); }

    // ══════════════════════════════════════════════════════════════════════
    // Rendering
    // ══════════════════════════════════════════════════════════════════════
    private void render() {
        gc.clearRect(0, 0, W, H);
        switch (state) {
            case STORY     -> renderStory();
            case PLAYING   -> renderGame();
            case PAUSED    -> { renderGame(); renderPause(); }
            case GAME_OVER -> { renderGame(); renderGameOver(); }
            case VICTORY   -> { renderGame(); renderVictory(); }
        }
    }

    private void renderBackground(double sx, double sy) {
        if (imgBackground != null) {
            gc.drawImage(imgBackground, sx, sy, W, H);
        } else {
            LinearGradient bg = new LinearGradient(0,0,0,1,true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#1a0e06")),
                new Stop(0.5, Color.web("#251408")),
                new Stop(1.0, Color.web("#0e0804")));
            gc.setFill(bg); gc.fillRect(0,0,W,H);
            gc.setFill(Color.color(0,0,0,0.15));
            for (int row = 0; row < H; row += 28) {
                double offset = (row/28%2==0) ? 0 : 40;
                for (int col = (int)(-offset+sx); col < W+40; col+=80)
                    gc.fillRect(col, row+sy, 78, 26);
            }
        }
    }

    private void renderGame() {
        double sx = eqShakeX, sy = eqShakeY;
        renderBackground(sx, sy);
        if (eqCrackAlpha > 0.01) renderCracks(sx, sy);

        for (MWPlatform p : platforms) p.render(gc, sx, sy, imgPlatformTile, tileAnimTimer);
        for (MWCrystal  c : crystals)  c.render(gc, sx, sy, imgRedCrystal, imgBlueCrystal);
        exitDoor.render(gc, sx, sy, imgRedDoor, imgBlueDoor);
        for (MWPlayer   p : players)   p.render(gc, sx, sy);
        for (MWMeteor   m : meteors)   m.render(gc, sx, sy);

        renderEqBanner();
        renderHUD();
    }

    private void renderCracks(double sx, double sy) {
        gc.setStroke(Color.color(0.9, 0.7, 0.4, eqCrackAlpha));
        gc.setLineWidth(1.5);
        Random cr = new Random(currentLevel * 17L);
        for (int i = 0; i < 12; i++) {
            double cx = cr.nextDouble() * W;
            double cy = cr.nextDouble() * H;
            gc.strokeLine(cx+sx, cy+sy,
                cx+(cr.nextDouble()-0.5)*80+sx,
                cy+(cr.nextDouble()-0.5)*120+sy);
        }
    }

    private void renderEqBanner() {
        if (!eqWarning && !eqActive) return;
        double alpha = eqActive ? 0.7 : (0.5 + 0.5*Math.sin(System.currentTimeMillis()*0.01));
        gc.setFill(Color.color(0.8, 0.2, 0.0, alpha*0.25));
        gc.fillRect(0, 0, W, H);
        String msg = eqActive ? "⚠  EARTHQUAKE!" : "⚠  TREMORS DETECTED...";
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 26));
        gc.setFill(Color.color(1, 0.9, 0.2, alpha));
        gc.fillText(msg, W/2.0 - msg.length()*7.3, 85);
    }

    private void renderHUD() {
        gc.setFill(Color.color(0,0,0,0.65));
        gc.fillRect(0, 0, W, 54);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 16));
        gc.setFill(Color.web("#ff4040"));
        gc.fillText("FIREBOY: " + hearts(fireboy.lives), 14, 20);
        gc.setFont(Font.font("Courier New", 13));
        gc.setFill(Color.web("#ffaa60"));
        gc.fillText("Score: " + fireboy.score, 14, 40);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 15));
        gc.setFill(Color.web("#e8c870"));
        String lvl = "LEVEL " + currentLevel + " / " + MAX_LEVEL;
        gc.fillText(lvl, W/2.0 - lvl.length()*4.5, 20);

        gc.setFont(Font.font("Courier New", 13));
        gc.setFill(Color.web("#c8a040"));
        String tot = "Total: " + (totalScore + fireboy.score + watergirl.score);
        gc.fillText(tot, W/2.0 - tot.length()*3.5, 40);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 16));
        gc.setFill(Color.web("#40aaff"));
        String wt = "WATERGIRL: " + hearts(watergirl.lives);
        gc.fillText(wt, W - wt.length()*9.5 - 14, 20);
        gc.setFont(Font.font("Courier New", 13));
        gc.setFill(Color.web("#80d0ff"));
        String ws = "Score: " + watergirl.score;
        gc.fillText(ws, W - ws.length()*7.8 - 14, 40);

        gc.setFont(Font.font("Courier New", 11));
        gc.setFill(Color.color(1,1,1,0.38));
        gc.fillText("Fireboy: A/D/W(×2)   Watergirl: ←/→/↑(×2)   SPACE: pause   ESC: exit", 10, H-8);
    }

    private String hearts(int lives) {
        return "♥".repeat(Math.max(0,lives)) + "♡".repeat(Math.max(0, MAX_LIVES-lives));
    }

    // ── Story screen ──────────────────────────────────────────────────────
    private void renderStory() {
        if (imgStoryBg != null) {
            gc.drawImage(imgStoryBg, 0, 0, W, H);
        } else {
            renderBackground(0, 0);
        }
        int idx = Math.min(currentLevel-1, 2);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 13));
        gc.setFill(Color.web("#8a7a50"));
        String badge = "CHAPTER " + currentLevel;
        gc.fillText(badge, W/2.0 - badge.length()*3.9, 160);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 28));
        gc.setFill(Color.web("#e8c870"));
        String title = STORY_TITLES[idx];
        gc.fillText(title, W/2.0 - title.length()*8.4, 200);

        gc.setFill(Color.color(1,1,1,0.15));
        gc.fillRect(W/2.0-300, 215, 600, 2);

        gc.setFont(Font.font("Courier New", 16));
        gc.setFill(Color.web("#c0a878"));
        String[] lines = STORY_BODIES[idx].split("\n");
        double ly = 305;
        for (String line : lines) {
            gc.fillText(line, W/2.0 - line.length()*4.8, ly);
            ly += 26;
        }

        Color btnCol = storyBtnHovered ? Color.web("#fff0c0") : Color.web("#e8c870");
        gc.setFill(btnCol);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        String startTxt = "▶  BEGIN LEVEL " + currentLevel;
        gc.fillText(startTxt, W/2.0 - startTxt.length()*6.6, 455);
        if (storyBtnHovered) {
            gc.setStroke(btnCol); gc.setLineWidth(2);
            gc.strokeLine(W/2.0-140, 460, W/2.0+140, 460);
        }

        Color backCol = backBtnHovered ? Color.web("#fff0c0") : Color.WHITE;
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 15));
        gc.setFill(backCol);
        gc.fillPolygon(new double[]{38,52,38}, new double[]{44,51,58}, 3);
        gc.fillText("BACK", 58, 57);

        gc.setFont(Font.font("Courier New", 13));
        gc.setFill(Color.web("#6a5a30"));
        String ctrl = "Fireboy: A D W(×2)   |   Watergirl: ← → ↑(×2)     Both must reach EXIT DOORS";
        gc.fillText(ctrl, W/2.0 - ctrl.length()*3.9, H - 40);

        long elapsed = System.currentTimeMillis() - storyStartMs;
        long remaining = Math.max(0, 5000 - elapsed) / 1000 + 1;
        gc.setFont(Font.font("Courier New", 12));
        gc.setFill(Color.web("#6a5a30"));
        String auto = "Auto-start in " + remaining + "s…";
        gc.fillText(auto, W/2.0 - auto.length()*3.6, H - 20);

        if (elapsed >= 5000) startPlaying();
    }

    private void renderPause() {
        gc.setFill(Color.color(0,0,0,0.55));
        gc.fillRect(0,0,W,H);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 52));
        gc.setFill(Color.web("#e8c870"));
        gc.fillText("PAUSED", W/2.0-95, H/2.0-20);
        gc.setFont(Font.font("Courier New", 18));
        gc.setFill(Color.color(1,1,1,0.75));
        gc.fillText("Press SPACE to continue", W/2.0-110, H/2.0+28);
    }

    private void renderGameOver() {
        gc.setFill(Color.color(0,0,0,0.75));
        gc.fillRect(0,0,W,H);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 64));
        gc.setFill(Color.web("#ff4040"));
        gc.fillText("GAME OVER", W/2.0-175, H/2.0-30);
        gc.setFont(Font.font("Courier New", 18));
        gc.setFill(Color.web("#c0a060"));
        gc.fillText("Fireboy and Watergirl didn't make it…", W/2.0-165, H/2.0+20);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 20));
        gc.setFill(Color.WHITE);
        gc.fillText("Press R to retry  |  Press ESC to return to world", W/2.0-210, H/2.0+70);
        if (keys.contains(KeyCode.R)) {
            totalScore = 0;
            loadLevel(1);
            if (bgMusic != null) bgMusic.play();
        }
    }

    private void renderVictory() {
        gc.setFill(Color.color(0,0,0,0.75));
        gc.fillRect(0,0,W,H);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 64));
        gc.setFill(Color.web("#e8c870"));
        gc.fillText("YOU ESCAPED!", W/2.0-220, H/2.0-40);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 24));
        gc.setFill(Color.web("#ffd060"));
        int fs = totalScore + fireboy.score + watergirl.score;
        gc.fillText("FINAL SCORE: " + fs, W/2.0-100, H/2.0+55);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 20));
        gc.setFill(Color.WHITE);
        gc.fillText("Press R to play again  |  Press ESC to return to world", W/2.0-230, H/2.0+100);
        if (keys.contains(KeyCode.R)) {
            totalScore = 0;
            loadLevel(1);
            if (bgMusic != null) bgMusic.play();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Exit
    // ══════════════════════════════════════════════════════════════════════
    private void exitGame() {
        gameLoop.stop();
        stopBgMusic();
        prevScreen.returnToWorld();
    }

    private void stopBgMusic() {
        if (bgMusic != null) { bgMusic.stop(); }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Inner classes
    // ══════════════════════════════════════════════════════════════════════

    // ── Player ────────────────────────────────────────────────────────────
    class MWPlayer {
        final String name;
        final Color bodyColor, glowColor;

        final Image sprIdle, sprLeft, sprRight, sprUp, sprDown;

        double x, y, vx, vy;
        boolean onGround = false, facingRight = true, alive = true;
        boolean keyLeft, keyRight;
        int lives     = MAX_LIVES;
        int score     = 0;
        int jumpsLeft = MAX_JUMPS;
        double invincibleTimer = 0;
        boolean damageFlash = false;
        double slipForce = 0;
        final double spawnX, spawnY;

        double animTimer = 0;
        int    animFrame = 0;

        // Smoothed position for rendering — eliminates idle jitter
        double renderX, renderY;

        MWPlayer(String name, Color body, Color glow,
                 Image idle, Image left, Image right, Image up, Image down,
                 double spawnX, double spawnY) {
            this.name      = name;
            this.bodyColor = body;
            this.glowColor = glow;
            this.sprIdle   = idle;
            this.sprLeft   = left;
            this.sprRight  = right;
            this.sprUp     = up;
            this.sprDown   = down;
            this.spawnX    = spawnX;
            this.spawnY    = spawnY;
            this.x         = spawnX;
            this.y         = spawnY;
            this.renderX   = spawnX;
            this.renderY   = spawnY;
        }

        void tryJump(Mineworld game) {
            if (!alive) return;
            if (jumpsLeft > 0) {
                if (jumpsLeft == MAX_JUMPS) {
                    vy = JUMP_FORCE;
                    play(sndJump, 0.6);
                } else {
                    vy = DOUBLE_JUMP_FORCE;
                    play(sndDoubleJump, 0.7);
                }
                onGround = false;
                jumpsLeft--;
            }
        }

        void update(double dt, boolean eq) {
            if (!alive) return;

            double speed = MOVE_SPEED;
            if (keyLeft)  { vx = -speed; facingRight = false; }
            if (keyRight) { vx =  speed; facingRight = true;  }
            if (!keyLeft && !keyRight) {
                vx *= FRICTION;
                if (Math.abs(vx) < 0.08) vx = 0;
            }

            // Earthquake slip — only during active tremor
            if (eq && eqActive && slipForce != 0) {
                vx += slipForce * 0.3;
            }

            vy += GRAVITY;
            if (vy > MAX_FALL) vy = MAX_FALL;
            x += vx; y += vy;

            if (x < 0)          { x = 0; vx = 0; }
            if (x+PLAYER_W > W) { x = W-PLAYER_W; vx = 0; }

            if (invincibleTimer > 0) {
                invincibleTimer -= dt;
                damageFlash = (int)(invincibleTimer*8)%2 == 0;
            } else { damageFlash = false; }

            // Walk frame animation
            if (Math.abs(vx) > 0.5 && onGround) {
                animTimer += dt;
                if (animTimer > 0.12) { animTimer = 0; animFrame = (animFrame+1)%2; }
            } else { animFrame = 0; animTimer = 0; }

            // ── Snap to integer position when idle on ground to kill micro-jitter ──
            if (onGround && !keyLeft && !keyRight && Math.abs(vx) < 0.1) {
                vx = 0;
                // Round x so the character doesn't drift sub-pixel while idle
                x = Math.round(x);
            }

            // Smooth render position — lerp toward physics position
            // This prevents any remaining per-frame noise from appearing on screen
            double lerpSpeed = 18.0; // fast enough to feel instant, slow enough to filter noise
            renderX = renderX + (x - renderX) * Math.min(1.0, lerpSpeed * dt);
            renderY = renderY + (y - renderY) * Math.min(1.0, lerpSpeed * dt);

            // When very close, just snap to avoid permanent micro-oscillation
            if (Math.abs(renderX - x) < 0.3) renderX = x;
            if (Math.abs(renderY - y) < 0.3) renderY = y;
        }

        boolean takeDamage() {
            if (invincibleTimer > 0) return false;
            lives--;
            invincibleTimer = INVINCIBLE_T;
            if (lives <= 0) { alive = false; lives = 0; }
            return true;
        }

        double[] rect()    { return new double[]{x+5, y+5, PLAYER_W-10, PLAYER_H-10}; }
        double[] rectArr() { return rect(); }

        void render(GraphicsContext gc, double sx, double sy) {
            if (!alive) return;
            if (damageFlash) return;

            // Use smoothed render position — no jitter during idle
            double rx = Math.round(renderX + sx);
            double ry = Math.round(renderY + sy);

            Image spr;
            if (!onGround && vy < 0) {
                spr = sprUp != null ? sprUp : sprIdle;
            } else if (!onGround && vy > 1) {
                spr = sprDown != null ? sprDown : sprIdle;
            } else if (keyLeft) {
                spr = sprLeft != null ? sprLeft : sprIdle;
            } else if (keyRight) {
                spr = sprRight != null ? sprRight : sprIdle;
            } else {
                spr = sprIdle;
            }

            if (spr != null) {
                gc.drawImage(spr, rx, ry, PLAYER_W, PLAYER_H);
            } else {
                gc.setFill(bodyColor);
                gc.fillRoundRect(rx, ry, PLAYER_W, PLAYER_H, 14, 14);
                gc.setFill(glowColor.deriveColor(0,1,1,0.4));
                gc.fillRoundRect(rx-4, ry-4, PLAYER_W+8, PLAYER_H+8, 18, 18);
            }

            // Double-jump indicator dots
            if (!onGround && jumpsLeft > 0) {
                gc.setFill(Color.color(1, 1, 0.4, 0.7));
                double t = System.currentTimeMillis() / 200.0;
                for (int i = 0; i < 3; i++) {
                    double angle = t + i * (Math.PI * 2 / 3);
                    double ox = Math.cos(angle) * 14;
                    double oy = Math.sin(angle) * 6 - 12;
                    gc.fillOval(rx + PLAYER_W/2.0 + ox - 3, ry + oy - 3, 6, 6);
                }
            }
        }
    }

    // ── Platform ──────────────────────────────────────────────────────────
    class MWPlatform {
        double x, y, w, h;
        final boolean moving;
        final double moveMin, moveMax, moveSpeed;
        private boolean movingFwd = true;
        double baseX, baseY;

        MWPlatform(double x, double y, double w, double h) {
            this(x,y,w,h,false,0,0,0);
        }
        MWPlatform(double x, double y, double w, double h,
                   boolean moving, double min, double max, double speed) {
            this.x=this.baseX=x; this.y=this.baseY=y;
            this.w=w; this.h=h;
            this.moving=moving; this.moveMin=min; this.moveMax=max; this.moveSpeed=speed;
        }

        void update(double dt, boolean eq, double trembleAmt) {
            if (moving) {
                if (movingFwd) { x += moveSpeed*dt*60; if (x>moveMax){x=moveMax;movingFwd=false;} }
                else           { x -= moveSpeed*dt*60; if (x<moveMin){x=moveMin;movingFwd=true;}  }
                baseX = x;
            }
            if (eq) {
                x = baseX + (int)((rng.nextDouble() - 0.5) * trembleAmt * 0.25);
                y = baseY;
            } else {
                x = baseX;
                y = baseY;
            }
        }

        double[] rect()    { return new double[]{x,y,w,h}; }
        double[] rectArr() { return rect(); }

        void render(GraphicsContext gc, double sx, double sy, Image tileImg, double animT) {
            double rx=x+sx, ry=y+sy;

            if (tileImg != null) {
                double tileW = tileImg.getWidth();
                gc.save();
                gc.beginPath();
                gc.rect(rx, ry, w, h);
                gc.clip();
                for (double tx = rx; tx < rx + w; tx += tileW) {
                    gc.drawImage(tileImg, tx, ry, tileW, h);
                }
                gc.restore();
            } else {
                LinearGradient stone = new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#3a5a28")),
                    new Stop(0.25, Color.web("#2a4018")),
                    new Stop(0.26, Color.web("#5a4020")),
                    new Stop(1.0, Color.web("#3a2810")));
                gc.setFill(stone);
                gc.fillRoundRect(rx, ry, w, h, 4, 4);

                gc.setFill(Color.color(0,0,0,0.2));
                gc.fillRect(rx, ry+h*0.3, w, 1.5);

                gc.setFill(Color.color(0,0,0,0.15));
                double brickW = 28;
                double rowOffset = ((int)(ry / h) % 2 == 0) ? 0 : brickW/2;
                for (double bx = rx + rowOffset; bx < rx+w; bx += brickW)
                    gc.fillRect(bx, ry+h*0.3, 1.5, h*0.7);

                gc.setFill(Color.web("#4a8a30"));
                gc.fillRoundRect(rx, ry, w, h*0.28, 4, 4);

                gc.setFill(Color.color(0.4, 0.7, 0.2, 0.5));
                gc.fillRect(rx+2, ry+2, w-4, h*0.1);

                double vineAlpha = 0.5 + 0.2*Math.sin(animT*2);
                gc.setFill(Color.color(0.2, 0.5, 0.1, vineAlpha));
                Random vr = new Random((long)(rx * 17 + ry * 31));
                for (int i = 0; i < (int)(w/22); i++) {
                    double vx2 = rx + 4 + vr.nextDouble()*(w-8);
                    double vineLen = 6 + vr.nextDouble()*10;
                    double swing = Math.sin(animT*1.5 + i) * 2;
                    gc.fillRoundRect(vx2+swing, ry+h-2, 3, vineLen, 2, 2);
                }

                if (moving) {
                    gc.setFill(Color.color(1.0, 0.8, 0.2, 0.18 + 0.08*Math.sin(animT*3)));
                    gc.fillRoundRect(rx-2, ry-2, w+4, h+4, 6, 6);
                    gc.setStroke(Color.color(1.0, 0.85, 0.3, 0.5));
                    gc.setLineWidth(1.5);
                    gc.strokeRoundRect(rx, ry, w, h, 4, 4);
                }
            }
        }
    }

    // ── Crystal ───────────────────────────────────────────────────────────
    class MWCrystal {
        double x, y;
        final String type;
        boolean collected = false;
        double bobTimer;
        double sparkTimer = 0;

        MWCrystal(double x, double y, String type) {
            this.x=x; this.y=y; this.type=type;
            this.bobTimer = rng.nextDouble()*Math.PI*2;
        }

        void update(double dt) {
            if (!collected) {
                bobTimer  += dt*3;
                sparkTimer += dt;
            }
        }

        double[] rect() {
            double s=20;
            return new double[]{x-s/2, y-s/2+Math.sin(bobTimer)*3, s, s};
        }

        void render(GraphicsContext gc, double sx, double sy, Image redImg, Image blueImg) {
            if (collected) return;
            double bob = Math.sin(bobTimer)*4;
            double rx=x+sx, ry=y+sy+bob;
            double size = 24;

            Color glowCol;
            Image crystalImg;
            switch(type) {
                case "fire"  -> { glowCol=Color.color(1,0.3,0.2,0.35); crystalImg=redImg; }
                case "water" -> { glowCol=Color.color(0.2,0.6,1,0.35); crystalImg=blueImg; }
                default      -> { glowCol=Color.color(1,0.85,0.2,0.35); crystalImg=blueImg; }
            }

            double glow = 0.5 + 0.5*Math.sin(sparkTimer*4);
            gc.setFill(glowCol.deriveColor(0,1,1, glow));
            gc.fillOval(rx-size, ry-size, size*2, size*2);

            if (crystalImg != null) {
                gc.drawImage(crystalImg, rx-size/2, ry-size/2, size, size);
            } else {
                Color fill = type.equals("fire") ? Color.web("#ff4040")
                           : type.equals("water") ? Color.web("#40a0ff")
                           : Color.web("#ffd040");
                double half = size/2;
                gc.setFill(fill);
                gc.fillPolygon(
                    new double[]{rx, rx+half, rx, rx-half},
                    new double[]{ry-half, ry, ry+half, ry}, 4);
                gc.setFill(Color.color(1,1,1,0.5));
                gc.fillOval(rx-half*0.3, ry-half*0.6, half*0.4, half*0.4);
            }

            if (sparkTimer % 0.4 < 0.2) {
                gc.setFill(glowCol.brighter().deriveColor(0,1,1,0.8));
                double sp = sparkTimer * 120;
                gc.fillOval(rx + Math.cos(Math.toRadians(sp))*16 - 2,
                            ry + Math.sin(Math.toRadians(sp))*10 - 2, 4, 4);
                gc.fillOval(rx + Math.cos(Math.toRadians(sp+180))*14 - 2,
                            ry + Math.sin(Math.toRadians(sp+180))*9 - 2, 3, 3);
            }
        }
    }

    // ── Exit door — now uses reddoor.png / bluedoor.png if available ──────
    class MWExitDoor {
        final double x, y;
        static final double DW=40, DH=60;
        boolean fireboyIn=false, watergirlIn=false;
        double glowTimer=0;

        MWExitDoor(double x, double y) { this.x=x; this.y=y; }
        void update(double dt) { glowTimer+=dt; }

        double[] fireRect()  { return new double[]{x, y, DW, DH}; }
        double[] waterRect() { return new double[]{x+DW+4, y, DW, DH}; }

        void render(GraphicsContext gc, double sx, double sy, Image redDoorImg, Image blueDoorImg) {
            double rx=x+sx, ry=y+sy;
            double glow=0.5+0.5*Math.sin(glowTimer*3);

            renderDoor(gc, rx,      ry, redDoorImg,  Color.web("#c03010"), Color.web("#ff6040"), fireboyIn,   glow);
            renderDoor(gc, rx+DW+4, ry, blueDoorImg, Color.web("#104080"), Color.web("#40a0ff"), watergirlIn, glow);
        }

        private void renderDoor(GraphicsContext gc, double dx, double dy,
                                 Image doorImg, Color door, Color glowC,
                                 boolean entered, double glow) {
            if (doorImg != null) {
                // ── Draw PNG door image ────────────────────────────────
                // Outer glow ring when waiting for entry
                if (!entered) {
                    gc.setFill(glowC.deriveColor(0,1,1, glow*0.4));
                    gc.fillRoundRect(dx-7, dy-7, DW+14, DH+14, 10, 10);
                }
                // Draw the door image — brighten slightly when entered
                if (entered) {
                    gc.setGlobalAlpha(1.0);
                } else {
                    gc.setGlobalAlpha(0.92);
                }
                gc.drawImage(doorImg, dx, dy, DW, DH);
                gc.setGlobalAlpha(1.0);

                // Flash on entry
                if (entered) {
                    gc.setFill(glowC.deriveColor(0,1,1,0.35));
                    gc.fillRoundRect(dx, dy, DW, DH, 4, 4);
                }
            } else {
                // ── Fallback procedural door ───────────────────────────
                gc.setFill(Color.web("#5a3a18"));
                gc.fillRoundRect(dx-3, dy-3, DW+6, DH+6, 6, 6);
                gc.setFill(entered ? door.brighter() : door);
                gc.fillRoundRect(dx, dy, DW, DH, 4, 4);
                if (!entered) {
                    gc.setFill(glowC.deriveColor(0,1,1,glow*0.35));
                    gc.fillRoundRect(dx-6, dy-6, DW+12, DH+12, 8, 8);
                }
                gc.setFill(Color.color(0,0,0,entered?0.1:0.4));
                gc.fillRoundRect(dx+DW*0.25, dy+DH*0.25, DW*0.5, DH*0.65, 4, 4);
            }
        }
    }

    // ── Meteor ────────────────────────────────────────────────────────────
    class MWMeteor {
        double x, y, vx, vy, rotation=0;
        boolean active=true, landed=false;
        double landTimer=0;
        final double mw, mh;

        MWMeteor(double spawnX, double speed, int level) {
            mw = METEOR_W + (level-1)*4;
            mh = METEOR_H + (level-1)*4;
            x = spawnX; y = -mh - 10;
            vx = (rng.nextDouble()-0.5)*1.5*level;
            vy = speed;
        }

        void update(double dt) {
            if (!active) return;
            vy += 0.18; x += vx; y += vy;
            rotation += 4*(1+Math.abs(vx)*0.3);
            if (landed) { landTimer+=dt; if (landTimer>0.4) active=false; }
        }

        void impact() { landed=true; vy=0; vx=0; }

        double[] hitRect() { return new double[]{x+mw*0.1, y+mh*0.1, mw*0.8, mh*0.8}; }

        void render(GraphicsContext gc, double sx, double sy) {
            if (!active) return;
            double rx=x+sx, ry=y+sy;
            double cx=rx+mw/2, cy=ry+mh/2;
            gc.save();
            gc.translate(cx,cy); gc.rotate(rotation); gc.translate(-mw/2,-mh/2);
            if (imgMeteor != null) {
                gc.drawImage(imgMeteor, 0, 0, mw, mh);
            } else {
                RadialGradient grad = new RadialGradient(0,0,0.4,0.4,0.6,true,CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#ffd060")),
                    new Stop(0.4, Color.web("#ff4400")),
                    new Stop(1.0, Color.web("#301008")));
                gc.setFill(grad); gc.fillOval(0,0,mw,mh);
            }
            gc.restore();
            if (landed) {
                double alpha=Math.max(0, 1-landTimer/0.4);
                gc.setFill(Color.color(1,0.8,0.3,alpha*0.7));
                gc.fillOval(rx-mw*0.5, ry+mh*0.3, mw*2, mh*0.8);
            }
        }
    }
}
