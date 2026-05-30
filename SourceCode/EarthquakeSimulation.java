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

// Earthquake simulation — swaying buildings, ground cracks, dust, Richter meter
public class EarthquakeSimulation extends StackPane {

    private static final int W=SimUtils.WIDTH, H=SimUtils.HEIGHT;
    private final Stage parentStage; private final Scene returnScene;
    private double time=0, magnitude=0, shakeX=0, shakeY=0;
    private final double targetMag=7.5; private boolean peaked=false;
    private final List<Building> buildings=new ArrayList<>();
    private final List<double[][]> cracks=new ArrayList<>();
    private final List<Dust> dusts=new ArrayList<>();
    private final Random rng=new Random();
    private AnimationTimer timer; private long last=0;

    public EarthquakeSimulation(Stage stage,Scene ret){
        parentStage=stage; returnScene=ret; setPrefSize(W,H);
        Canvas cv=new Canvas(W,H); getChildren().add(cv);
        buildOverlay(); genBuildings(); startLoop(cv); wireESC();
    }

    private void buildOverlay(){
        Text title=txt("⚡  EARTHQUAKE SIMULATION","#e8c840",27,true);
        Text info=txt("Tectonic plates shifting — brace for impact!","#b09020",13,false);
        Button back=SimUtils.backButton(this::goBack);
        VBox top=new VBox(5,title,info); top.setPadding(new Insets(18,0,0,28));
        BorderPane ov=new BorderPane(); ov.setBackground(Background.EMPTY);
        ov.setTop(top); ov.setBottom(SimUtils.footerBox(back)); ov.setPrefSize(W,H);
        getChildren().add(ov);
    }

    private static class Building { double x,baseY,w,h,sway,phase; Color wall; int wc,wr; boolean collapsed; double cp; }
    private static class Dust { double x,y,vx,vy,sz,life,max; }

    private void genBuildings(){
        int n=13; double bw=(double)W/n;
        Color[] walls={Color.web("#3a3028"),Color.web("#2a2820"),Color.web("#3a2820"),Color.web("#282838")};
        for(int i=0;i<n;i++){
            Building b=new Building(); b.w=bw-14+rng.nextDouble()*30; b.h=100+rng.nextDouble()*280;
            b.x=i*bw+(bw-b.w)/2; b.baseY=H*0.82; b.phase=rng.nextDouble()*Math.PI*2;
            b.wall=walls[rng.nextInt(walls.length)]; b.wc=Math.max(1,(int)(b.w/20)); b.wr=Math.max(1,(int)(b.h/25));
            buildings.add(b);
        }
    }

    private double[][] newCrack(double sx){
        List<double[]> pts=new ArrayList<>(); double cx=sx,cy=H*0.82; pts.add(new double[]{cx,cy});
        for(int i=0;i<8+rng.nextInt(6);i++){cx+=(rng.nextDouble()-0.5)*30;cy+=10+rng.nextDouble()*15;if(cy>H)break;pts.add(new double[]{cx,cy});}
        return pts.toArray(new double[0][]);
    }

    private void spawnDust(double x,double y){
        for(int i=0;i<12;i++){Dust d=new Dust();d.x=x+(rng.nextDouble()-0.5)*60;d.y=y;d.vx=(rng.nextDouble()-0.5)*1.5;d.vy=-1-rng.nextDouble()*2;d.sz=10+rng.nextDouble()*30;d.max=1+rng.nextDouble();dusts.add(d);}
    }

    private void startLoop(Canvas cv){
        GraphicsContext gc=cv.getGraphicsContext2D();
        timer=new AnimationTimer(){@Override public void handle(long now){
            if(last==0){last=now;return;}double dt=(now-last)/1e9;last=now;time+=dt;
            if(!peaked){magnitude=Math.min(targetMag,magnitude+dt*1.8);if(magnitude>=targetMag)peaked=true;}
            else magnitude=Math.max(0,magnitude-dt*0.8);
            double sh=magnitude*0.8; shakeX=(rng.nextDouble()-0.5)*sh; shakeY=(rng.nextDouble()-0.5)*sh*0.5;
            for(Building b:buildings){double ts=Math.sin(time*3+b.phase)*magnitude*0.6;b.sway+=(ts-b.sway)*0.15;
                if(magnitude>6&&!b.collapsed&&rng.nextDouble()<dt*0.04){b.collapsed=true;spawnDust(b.x+b.w/2,b.baseY);}
                if(b.collapsed&&b.cp<1)b.cp=Math.min(1,b.cp+dt*1.2);}
            if(magnitude>3&&rng.nextDouble()<dt*0.5*magnitude)cracks.add(newCrack(rng.nextDouble()*W));
            dusts.removeIf(d->{d.x+=d.vx*60*dt;d.y+=d.vy*60*dt;d.vy+=0.01;d.life+=dt;d.sz+=dt*8;return d.life>d.max;});
            draw(gc);
        }};timer.start();
    }

    private void draw(GraphicsContext gc){
        gc.save(); gc.translate(shakeX,shakeY);
        double dark=Math.min(0.6,magnitude/10*0.6);
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.color(0.3-dark*0.2,0.35-dark*0.25,0.45-dark*0.3)),
                new Stop(1,Color.color(0.5-dark*0.3,0.45-dark*0.2,0.35-dark*0.1))));
        gc.fillRect(-20,-20,W+40,H+40);
        double gY=H*0.82;
        gc.setFill(new LinearGradient(0,gY,0,H,false,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#4a3820")),new Stop(1,Color.web("#1a1208"))));
        gc.fillRect(-20,gY,W+40,H-gY+20);
        gc.setStroke(Color.color(0.1,0.08,0.05,0.9)); gc.setLineWidth(1.5);
        for(double[][] c:cracks){if(c.length<2)continue;gc.beginPath();gc.moveTo(c[0][0],c[0][1]);for(int i=1;i<c.length;i++)gc.lineTo(c[i][0],c[i][1]);gc.stroke();}
        for(Building b:buildings){
            gc.save(); double px=b.x+b.w/2,py=b.baseY; gc.translate(px,py);
            if(b.collapsed)gc.rotate(80*b.cp); else gc.rotate(b.sway); gc.translate(-px,-py);
            double dh=b.collapsed?b.h*(1-b.cp*0.6):b.h,dy=b.baseY-dh;
            gc.setFill(b.wall); gc.fillRect(b.x,dy,b.w,dh);
            if(!b.collapsed){gc.setFill(Color.color(1,0.95,0.6,0.35));double wg=b.w/(b.wc+1),hg=dh/(b.wr+1);
                for(int r=1;r<=b.wr;r++)for(int col=1;col<=b.wc;col++)gc.fillRect(b.x+col*wg-5,dy+r*hg-8,10,14);}
            gc.restore();
        }
        for(Dust d:dusts){double t=d.life/d.max;gc.setFill(Color.color(0.7,0.65,0.55,0.35*(1-t)));gc.fillOval(d.x-d.sz/2,d.y-d.sz/2,d.sz,d.sz);}
        gc.restore();
        // Richter HUD
        double mx=W-220,my=100,mw=180,mh=20;
        gc.setFill(Color.color(0,0,0,0.6)); gc.fillRoundRect(mx-10,my-30,mw+20,mh+50,10,10);
        gc.setFill(Color.color(0.9,0.85,0.6,0.9)); gc.setFont(Font.font("Courier New",FontWeight.BOLD,13));
        gc.fillText("RICHTER SCALE",mx,my-10);
        gc.setFill(Color.color(0.15,0.12,0.08,0.9)); gc.fillRoundRect(mx,my,mw,mh,6,6);
        double frac=magnitude/10; gc.setFill(Color.color(Math.min(1,frac*2),Math.max(0,1-frac),0));
        gc.fillRoundRect(mx,my,mw*frac,mh,6,6);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Courier New",FontWeight.BOLD,13));
        gc.fillText(String.format("%.1f",magnitude),mx+mw+8,my+15);
    }

    private void wireESC(){setFocusTraversable(true);setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)goBack();});}
    private void goBack(){timer.stop();FadeTransition ft=new FadeTransition(Duration.millis(260),this);ft.setToValue(0);ft.setOnFinished(e->parentStage.setScene(returnScene));ft.play();}
    private static Text txt(String t,String c,double sz,boolean bold){Text tx=new Text(t);tx.setFont(Font.font("Courier New",bold?FontWeight.BOLD:FontWeight.NORMAL,sz));tx.setFill(Color.web(c));return tx;}
}
