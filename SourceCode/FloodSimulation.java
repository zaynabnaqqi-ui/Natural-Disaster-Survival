import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Flood simulation: rising water, animated waves, floating debris
public class FloodSimulation extends StackPane {

    private static final int W = SimUtils.WIDTH;
    private static final int H = SimUtils.HEIGHT;

    private final Stage parentStage;
    private final Scene returnScene;

    private double waterLevel = H, waveOffset = 0, timeElapsed = 0;
    private final double targetLevel = H * 0.08;
    private final List<Debris> debrisList = new ArrayList<>();
    private final Random rng = new Random();
    private AnimationTimer timer;
    private long lastNano = 0;

    public FloodSimulation(Stage stage, Scene returnTo) {
        this.parentStage = stage; this.returnScene = returnTo;
        setPrefSize(W, H);
        Canvas canvas = new Canvas(W, H);
        getChildren().add(canvas);
        buildOverlay();
        spawnDebris(40);
        startLoop(canvas);
        wireESC();
    }

    private void buildOverlay() {
        Text title = styledText("🌊  FLOOD SIMULATION", "Courier New", FontWeight.BOLD, 27, "#c8e8ff");
        Text info  = styledText("Water is rising — find high ground!", "Courier New", FontWeight.NORMAL, 13, "#7ab8e0");
        Button back = SimUtils.backButton(this::goBack);
        VBox top = new VBox(5, title, info); top.setPadding(new Insets(18, 0, 0, 28));
        BorderPane ov = new BorderPane();
        ov.setBackground(Background.EMPTY); ov.setTop(top);
        ov.setBottom(SimUtils.footerBox(back)); ov.setPrefSize(W, H);
        ov.setMouseTransparent(false);
        getChildren().add(ov);
    }

    private static class Debris { double x, y, vx, w, h, angle, spin; Color color; }

    private void spawnDebris(int n) {
        Color[] p = {Color.web("#5a3a1a"), Color.web("#7a5a30"), Color.web("#3a2a10")};
        for (int i = 0; i < n; i++) {
            Debris d = new Debris();
            d.x = rng.nextDouble()*W; d.y = H - rng.nextDouble()*60;
            d.vx = (rng.nextDouble()-0.5)*0.8; d.w = 10+rng.nextDouble()*40; d.h = 6+rng.nextDouble()*16;
            d.angle = rng.nextDouble()*360; d.spin = (rng.nextDouble()-0.5)*0.5;
            d.color = p[rng.nextInt(p.length)]; debrisList.add(d);
        }
    }

    private void startLoop(Canvas cv) {
        GraphicsContext gc = cv.getGraphicsContext2D();
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNano == 0) { lastNano = now; return; }
                double dt = (now - lastNano)/1e9; lastNano = now;
                timeElapsed += dt; waveOffset += dt*60;
                if (waterLevel > targetLevel) waterLevel -= 18*dt;
                for (Debris d : debrisList) {
                    d.y = waterLevel - 8 + Math.sin(timeElapsed*2+d.x*0.01)*5;
                    d.x += d.vx; d.angle += d.spin;
                    if (d.x < -50) d.x = W+50; if (d.x > W+50) d.x = -50;
                }
                draw(gc);
            }
        };
        timer.start();
    }

    private void draw(GraphicsContext gc) {
        double fill = 1-(waterLevel/H);
        Color skyTop = lerp(Color.web("#b0d0f0"), Color.web("#101820"), fill*0.7);
        Color skyBot = lerp(Color.web("#d0e8ff"), Color.web("#1a2a3a"), fill*0.7);
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,new Stop(0,skyTop),new Stop(1,skyBot)));
        gc.fillRect(0,0,W,H);
        drawCity(gc);
        Color wd = lerp(Color.web("#1a6090"),Color.web("#0a1a2a"),fill*0.6);
        Color ws = lerp(Color.web("#3090d0"),Color.web("#0a2030"),fill*0.5);
        gc.setFill(new LinearGradient(0,waterLevel,0,H,false,CycleMethod.NO_CYCLE,new Stop(0,ws),new Stop(1,wd)));
        gc.fillRect(0,waterLevel,W,H-waterLevel);
        gc.setFill(Color.color(1,1,1,0.12));
        gc.beginPath(); gc.moveTo(0,waterLevel);
        for (double x=0;x<=W;x+=4) {
            double y = waterLevel+Math.sin(x*0.025+Math.toRadians(waveOffset))*6
                                 +Math.sin(x*0.015+Math.toRadians(waveOffset*0.7))*4;
            gc.lineTo(x,y);
        }
        gc.lineTo(W,H); gc.lineTo(0,H); gc.closePath(); gc.fill();
        for (Debris d : debrisList) {
            gc.save(); gc.translate(d.x,d.y); gc.rotate(d.angle);
            gc.setFill(d.color); gc.fillRect(-d.w/2,-d.h/2,d.w,d.h); gc.restore();
        }
        double pct = (1-(waterLevel-targetLevel)/(H-targetLevel))*100;
        gc.setFill(Color.color(1,1,1,0.75));
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,15));
        gc.fillText(String.format("WATER LEVEL: %.0f%%",Math.min(pct,100)),W-220,70);
        if (pct >= 99.5) {
            gc.setFill(Color.color(1,0.3,0.3,0.9));
            gc.setFont(Font.font("Courier New",FontWeight.BOLD,34));
            gc.fillText("⚠  CRITICAL FLOOD LEVEL",W/2.0-220,H/2.0);
        }
    }

    private void drawCity(GraphicsContext gc) {
        double[] hts = {200,280,160,320,240,200,180,300,260,220,190,240,310};
        double bw = (double)W/hts.length;
        for (int i=0;i<hts.length;i++) {
            double bh=hts[i], bx=i*bw, by=H-bh-10;
            gc.setFill(Color.web("#1a1a2a",0.8)); gc.fillRect(bx+5,by,bw-10,bh);
            gc.setFill(Color.color(1,0.9,0.5,0.3));
            for (double wy=by+15;wy<H-15;wy+=22)
                for (double wx=bx+12;wx<bx+bw-12;wx+=18) gc.fillRect(wx,wy,8,12);
            gc.setFill(Color.web("#1a1a2a",0.8));
        }
    }

    private Color lerp(Color a, Color b, double t) {
        t=Math.max(0,Math.min(1,t));
        return Color.color(a.getRed()+(b.getRed()-a.getRed())*t,
                           a.getGreen()+(b.getGreen()-a.getGreen())*t,
                           a.getBlue()+(b.getBlue()-a.getBlue())*t);
    }

    private void wireESC() {
        setFocusTraversable(true);
        setOnKeyPressed(e -> { if (e.getCode()==javafx.scene.input.KeyCode.ESCAPE) goBack(); });
    }

    private void goBack() {
        timer.stop();
        FadeTransition ft = new FadeTransition(Duration.millis(260),this);
        ft.setToValue(0); ft.setOnFinished(e -> parentStage.setScene(returnScene)); ft.play();
    }

    private static Text styledText(String t, String font, FontWeight w, double sz, String hex) {
        Text tx = new Text(t); tx.setFont(Font.font(font,w,sz)); tx.setFill(Color.web(hex)); return tx;
    }
}
