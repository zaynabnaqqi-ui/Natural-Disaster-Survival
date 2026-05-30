import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
// Meteor shower simulation: stars, rotating meteors with tails, craters
public class MeteorSimulation extends StackPane {

    private static final int W = SimUtils.WIDTH, H = SimUtils.HEIGHT;
    private final Stage parentStage;
    private final Scene returnScene;

    private final List<Meteor>   meteors   = new ArrayList<>();
    private final List<Crater>   craters   = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<double[]> stars     = new ArrayList<>();
    private final Random rng = new Random();
    private double time = 0; private int impacts = 0;
    private AnimationTimer timer; private long last = 0;

    public MeteorSimulation(Stage stage, Scene ret) {
        parentStage=stage; returnScene=ret; setPrefSize(W,H);
        Canvas cv = new Canvas(W,H); getChildren().add(cv);
        buildOverlay(); initStars(200); startLoop(cv); wireESC();
    }

    private void buildOverlay() {
        Text title = txt("☄  METEOR SIMULATION","#ffb060",27,true);
        Text info  = txt("Incoming impacts detected — seek shelter!","#cc7030",13,false);
        Button back = SimUtils.backButton(this::goBack);
        VBox top = new VBox(5,title,info); top.setPadding(new Insets(18,0,0,28));
        BorderPane ov = new BorderPane(); ov.setBackground(Background.EMPTY);
        ov.setTop(top); ov.setBottom(SimUtils.footerBox(back)); ov.setPrefSize(W,H);
        getChildren().add(ov);
    }

    private static class Meteor { double x,y,vx,vy,size,tail; boolean active=true; }
    private static class Crater { double x,y,r; }
    private static class Particle { double x,y,vx,vy,life,max,sz; Color col; }

    private void initStars(int n) {
        for(int i=0;i<n;i++) stars.add(new double[]{rng.nextDouble()*W, rng.nextDouble()*H*0.85,
                0.5+rng.nextDouble()*2, rng.nextDouble()*Math.PI*2});
    }

    private Meteor newMeteor() {
        Meteor m = new Meteor(); m.x = rng.nextDouble()*W*1.5-W*0.25; m.y = -30;
        double ang = Math.toRadians(50+rng.nextDouble()*30), spd = 5+rng.nextDouble()*7;
        m.vx = Math.cos(ang)*spd*(rng.nextBoolean()?1:-0.5); m.vy = Math.sin(ang)*spd;
        m.size = 4+rng.nextDouble()*10; m.tail = m.size*(6+rng.nextDouble()*8); return m;
    }

    private void startLoop(Canvas cv) {
        GraphicsContext gc = cv.getGraphicsContext2D();
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if(last==0){last=now;return;} double dt=(now-last)/1e9; last=now; time+=dt;
                if(rng.nextDouble()<dt*1.8) meteors.add(newMeteor());
                update(dt); draw(gc);
            }
        }; timer.start();
    }

    private void update(double dt) {
        double groundY = H*0.78;
        Iterator<Meteor> mi = meteors.iterator();
        while(mi.hasNext()) {
            Meteor m = mi.next();
            m.x += m.vx*60*dt; m.y += m.vy*60*dt;
            if(m.y >= groundY) { craters.add(crater(m.x,groundY,m.size)); explode(m.x,groundY,m.size); impacts++; mi.remove(); }
            else if(m.x<-200||m.x>W+200) mi.remove();
        }
        particles.removeIf(p -> { p.x+=p.vx*60*dt; p.y+=p.vy*60*dt; p.vy+=0.08; p.life+=dt; return p.life>=p.max; });
    }

    private Crater crater(double x,double y,double s){ Crater c=new Crater(); c.x=x;c.y=y;c.r=s*2.5+rng.nextDouble()*s; return c; }

    private void explode(double x,double y,double s) {
        Color[] cols={Color.web("#ff6600"),Color.web("#ff3300"),Color.web("#ffaa00")};
        for(int i=0;i<(int)(s*5);i++) {
            Particle p=new Particle(); p.x=x;p.y=y;
            double a=rng.nextDouble()*Math.PI*2, sp=1+rng.nextDouble()*4;
            p.vx=Math.cos(a)*sp; p.vy=Math.sin(a)*sp-2; p.max=0.4+rng.nextDouble()*0.6;
            p.sz=2+rng.nextDouble()*4; p.col=cols[rng.nextInt(cols.length)]; particles.add(p);
        }
    }

    private void draw(GraphicsContext gc) {
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#020408")),new Stop(0.7,Color.web("#06080f")),new Stop(1,Color.web("#0a0a06"))));
        gc.fillRect(0,0,W,H);
        for(double[] s : stars) {
            double tw=0.4+0.6*Math.abs(Math.sin(time*1.5+s[3]));
            gc.setFill(Color.color(1,1,0.9,tw)); gc.fillOval(s[0]-s[2]/2,s[1]-s[2]/2,s[2],s[2]);
        }
        double gY=H*0.78;
        gc.setFill(new LinearGradient(0,gY,0,H,false,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#2a2018")),new Stop(1,Color.web("#0e0c08"))));
        gc.fillRect(0,gY,W,H-gY);
        for(Crater c:craters) {
            gc.setFill(Color.color(0.05,0.04,0.03,0.85)); gc.fillOval(c.x-c.r,c.y-c.r*0.4,c.r*2,c.r*0.8);
            gc.setStroke(Color.color(0.4,0.3,0.2,0.55)); gc.setLineWidth(1.5);
            gc.strokeOval(c.x-c.r,c.y-c.r*0.4,c.r*2,c.r*0.8);
        }
        for(Meteor m : meteors) {
            double nx=-m.vx/Math.hypot(m.vx,m.vy), ny=-m.vy/Math.hypot(m.vx,m.vy);
            gc.setStroke(new LinearGradient(m.x+nx*m.tail,m.y+ny*m.tail,m.x,m.y,false,CycleMethod.NO_CYCLE,
                    new Stop(0,Color.TRANSPARENT),new Stop(1,Color.color(1,0.6,0.2,0.85))));
            gc.setLineWidth(m.size*0.5);
            gc.strokeLine(m.x+nx*m.tail,m.y+ny*m.tail,m.x,m.y);
            gc.setFill(new RadialGradient(0,0,m.x,m.y,m.size*1.8,false,CycleMethod.NO_CYCLE,
                    new Stop(0,Color.color(1,0.8,0.4,1)),new Stop(0.5,Color.color(1,0.4,0.1,0.6)),new Stop(1,Color.TRANSPARENT)));
            gc.fillOval(m.x-m.size*1.8,m.y-m.size*1.8,m.size*3.6,m.size*3.6);
            gc.setFill(Color.WHITE); gc.fillOval(m.x-m.size/2,m.y-m.size/2,m.size,m.size);
        }
        for(Particle p : particles) {
            double t=p.life/p.max;
            gc.setFill(Color.color(p.col.getRed(),p.col.getGreen(),p.col.getBlue(),1-t));
            gc.fillOval(p.x-p.sz/2,p.y-p.sz/2,p.sz,p.sz);
        }
        gc.setFill(Color.color(1,0.7,0.3,0.85)); gc.setFont(Font.font("Courier New",FontWeight.BOLD,14));
        gc.fillText("IMPACTS: "+impacts,W-180,70); gc.fillText("IN FLIGHT: "+meteors.size(),W-180,88);
    }

    private void wireESC() { setFocusTraversable(true); setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)goBack();}); }
    private void goBack() { timer.stop(); FadeTransition ft=new FadeTransition(Duration.millis(260),this); ft.setToValue(0); ft.setOnFinished(e->parentStage.setScene(returnScene)); ft.play(); }
    private static Text txt(String t,String c,double sz,boolean bold) { Text tx=new Text(t); tx.setFont(Font.font("Courier New",bold?FontWeight.BOLD:FontWeight.NORMAL,sz)); tx.setFill(Color.web(c)); return tx; }
}
