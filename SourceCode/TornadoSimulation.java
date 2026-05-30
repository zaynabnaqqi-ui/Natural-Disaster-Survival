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
import java.util.List;
import java.util.Random;

/** Tornado simulation — swaying funnel, orbiting debris, lightning, dust. */
public class TornadoSimulation extends StackPane {

    private static final int W=SimUtils.WIDTH, H=SimUtils.HEIGHT;
    private final Stage parentStage; private final Scene returnScene;
    private double tornadoX=W/2.0, sway=0, swayDir=1, wind=180, time=0, lightAlpha=0;
    private final List<Deb> debris=new ArrayList<>();
    private final List<Dust> dusts=new ArrayList<>();
    private final Random rng=new Random();
    private AnimationTimer timer; private long last=0;

    public TornadoSimulation(Stage stage,Scene ret){
        parentStage=stage;returnScene=ret;setPrefSize(W,H);
        Canvas cv=new Canvas(W,H);getChildren().add(cv);
        buildOverlay();initDebris(60);startLoop(cv);wireESC();
    }

    private void buildOverlay(){
        Text title=t("🌪  TORNADO SIMULATION","#90d0f0",27,true);
        Text info=t("Category F5 vortex — wind speeds exceeding 300 km/h!","#60a0c0",13,false);
        Button back=SimUtils.backButton(this::goBack);
        VBox top=new VBox(5,title,info);top.setPadding(new Insets(18,0,0,28));
        BorderPane ov=new BorderPane();ov.setBackground(Background.EMPTY);
        ov.setTop(top);ov.setBottom(SimUtils.footerBox(back));ov.setPrefSize(W,H);
        getChildren().add(ov);
    }

    private static class Deb{double angle,radius,speed,y,sz;Color col;}
    private static class Dust{double x,y,vx,vy,sz,life,max;}

    private void initDebris(int n){
        Color[] c={Color.web("#6a5030"),Color.web("#808080"),Color.web("#504030"),Color.web("#404040")};
        for(int i=0;i<n;i++){Deb d=new Deb();d.angle=rng.nextDouble()*Math.PI*2;d.radius=20+rng.nextDouble()*120;
            d.speed=(1.5+rng.nextDouble()*3)*(rng.nextBoolean()?1:-1);d.y=rng.nextDouble()*H*0.7;
            d.sz=3+rng.nextDouble()*10;d.col=c[rng.nextInt(c.length)];debris.add(d);}
    }

    private double funnelR(double y){double gY=H*0.85,cY=H*0.05,tt=Math.max(0,Math.min(1,(y-cY)/(gY-cY)));return 8+(1-tt)*(1-tt)*180;}

    private void startLoop(Canvas cv){
        GraphicsContext gc=cv.getGraphicsContext2D();
        timer=new AnimationTimer(){@Override public void handle(long now){
            if(last==0){last=now;return;}double dt=(now-last)/1e9;last=now;time+=dt;
            sway+=swayDir*30*dt;if(Math.abs(sway)>180)swayDir*=-1;tornadoX=W/2.0+sway;
            wind=180+Math.sin(time*0.5)*80+rng.nextDouble()*20;
            if(rng.nextDouble()<dt*0.8)lightAlpha=0.6+rng.nextDouble()*0.4; else lightAlpha=Math.max(0,lightAlpha-dt*4);
            for(Deb d:debris){d.angle+=d.speed*dt;double fr=funnelR(d.y);d.radius+=(fr*(0.5+rng.nextDouble())-d.radius)*0.01;}
            if(rng.nextDouble()<dt*5){Dust p=new Dust();p.x=tornadoX+(rng.nextDouble()-0.5)*80;p.y=H*0.85;p.vx=(rng.nextDouble()-0.5)*3;p.vy=-0.5-rng.nextDouble()*1.5;p.sz=20+rng.nextDouble()*40;p.max=1.2+rng.nextDouble();dusts.add(p);}
            dusts.removeIf(p->{p.x+=p.vx*60*dt;p.y+=p.vy*60*dt;p.sz+=dt*12;p.life+=dt;return p.life>=p.max;});
            draw(gc);
        }};timer.start();
    }

    private void draw(GraphicsContext gc){
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#050810")),new Stop(0.5,Color.web("#101820")),new Stop(1,Color.web("#1a1408"))));
        gc.fillRect(0,0,W,H);
        if(lightAlpha>0.01){gc.setFill(Color.color(0.8,0.9,1,lightAlpha*0.25));gc.fillRect(0,0,W,H);}
        drawCloud(gc);drawFunnel(gc);
        double gY=H*0.85;
        gc.setFill(new LinearGradient(0,gY,0,H,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#302010")),new Stop(1,Color.web("#0a0804"))));
        gc.fillRect(0,gY,W,H-gY);
        for(Dust p:dusts){double t=p.life/p.max;gc.setFill(Color.color(0.55,0.48,0.38,0.35*(1-t)));gc.fillOval(p.x-p.sz/2,p.y-p.sz/2,p.sz,p.sz);}
        for(Deb d:debris){double px=tornadoX+Math.cos(d.angle)*d.radius;gc.setFill(d.col);gc.fillRect(px-d.sz/2,d.y-d.sz/2,d.sz,d.sz);}
        // Wind gauge
        double gx=W-220,gy=100;gc.setFill(Color.color(0,0,0,0.55));gc.fillRoundRect(gx-10,gy-30,195,52,10,10);
        gc.setFill(Color.color(0.7,0.85,1,0.85));gc.setFont(Font.font("Courier New",FontWeight.BOLD,13));gc.fillText("WIND SPEED",gx,gy-10);
        double fr=Math.min(1,wind/320);gc.setFill(Color.color(0.15,0.15,0.2,0.9));gc.fillRoundRect(gx,gy,160,15,6,6);
        gc.setFill(Color.color(0.3,0.7*(1-fr),1,0.9));gc.fillRoundRect(gx,gy,160*fr,15,6,6);
        gc.setFill(Color.WHITE);gc.setFont(Font.font("Courier New",FontWeight.BOLD,13));gc.fillText(String.format("%.0f km/h",wind),gx+168,gy+13);
    }

    private void drawCloud(GraphicsContext gc){double cx=tornadoX,cy=H*0.06;gc.setFill(Color.color(0.12,0.14,0.18,0.92));for(int i=-3;i<=3;i++){double r=90+Math.abs(i)*10;gc.fillOval(cx+i*80-r,cy-r*0.5,r*2,r);}gc.setFill(Color.color(0.08,0.10,0.14,0.7));gc.fillOval(cx-220,cy-30,440,80);}

    private void drawFunnel(GraphicsContext gc){
        double tY=H*0.12,bY=H*0.85; int steps=80;
        double[] xs=new double[steps*2],ys=new double[steps*2];
        gc.setFill(Color.color(0.15,0.18,0.22,0.78));
        for(int i=0;i<steps;i++){double tt=(double)i/steps,y=tY+tt*(bY-tY),r=funnelR(y),sw=Math.sin(tt*Math.PI*4+time*5)*r*0.08;xs[i]=tornadoX-r+sw;ys[i]=y;xs[steps*2-1-i]=tornadoX+r+sw;ys[steps*2-1-i]=y;}
        gc.fillPolygon(xs,ys,steps*2);
        gc.setStroke(Color.color(0.3,0.35,0.45,0.4));gc.setLineWidth(1.2);
        for(int b=0;b<12;b++){double tt=(double)b/12+(time*0.5%(1.0/12)),y1=tY+tt*(bY-tY),y2=tY+Math.min(1,tt+0.06)*(bY-tY),r1=funnelR(y1),r2=funnelR(y2);gc.strokeLine(tornadoX-r1,y1,tornadoX+r2,y2);gc.strokeLine(tornadoX+r1,y1,tornadoX-r2,y2);}
        gc.setFill(Color.color(0.6,0.7,0.8,0.15));
        for(int i=0;i<steps;i++){double tt=(double)i/steps,y=tY+tt*(bY-tY),r=funnelR(y)*0.3;xs[i]=tornadoX-r;ys[i]=y;xs[steps*2-1-i]=tornadoX+r;ys[steps*2-1-i]=y;}
        gc.fillPolygon(xs,ys,steps*2);
    }

    private void wireESC(){setFocusTraversable(true);setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)goBack();});}
    private void goBack(){timer.stop();FadeTransition ft=new FadeTransition(Duration.millis(260),this);ft.setToValue(0);ft.setOnFinished(e->parentStage.setScene(returnScene));ft.play();}
    private static Text t(String tx,String c,double sz,boolean bold){Text x=new Text(tx);x.setFont(Font.font("Courier New",bold?FontWeight.BOLD:FontWeight.NORMAL,sz));x.setFill(Color.web(c));return x;}
}
