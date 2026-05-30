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

/** Volcanic eruption simulation — lava rivers, bombs, ash clouds. */
public class VolcanoSimulation extends StackPane {

    private static final int W=SimUtils.WIDTH, H=SimUtils.HEIGHT;
    private final Stage parentStage; private final Scene returnScene;
    private double time=0, intensity=0, ashCover=0;
    private final List<EP> parts=new ArrayList<>();
    private final List<LB> bombs=new ArrayList<>();
    private final List<AC> ash=new ArrayList<>();
    private final List<List<double[]>> rivers=new ArrayList<>();
    private final List<double[]> flowProg=new ArrayList<>();
    private final Random rng=new Random();
    private AnimationTimer timer; private long last=0;

    private static final double VX=W*0.5, VY=H*0.28;

    public VolcanoSimulation(Stage stage,Scene ret){
        parentStage=stage;returnScene=ret;setPrefSize(W,H);
        Canvas cv=new Canvas(W,H);getChildren().add(cv);
        buildOverlay();initRivers();startLoop(cv);wireESC();
    }

    private void buildOverlay(){
        Text title=t("🌋  VOLCANIC ACTIVITY SIMULATION","#ff5520",27,true);
        Text info=t("Magma pressure critical — eruption imminent!","#cc3010",13,false);
        Button back=SimUtils.backButton(this::goBack);
        VBox top=new VBox(5,title,info);top.setPadding(new Insets(18,0,0,28));
        BorderPane ov=new BorderPane();ov.setBackground(Background.EMPTY);
        ov.setTop(top);ov.setBottom(SimUtils.footerBox(back));ov.setPrefSize(W,H);
        getChildren().add(ov);
    }

    private static class EP{double x,y,vx,vy,sz,life,max;Color col;boolean isAsh;}
    private static class LB{double x,y,vx,vy,sz;}
    private static class AC{double x,y,vx,vy,sz,life,max,alpha;}

    private void initRivers(){
        double[][][] raw={
            {{VX,VY},{VX-80,H*.42},{VX-160,H*.6},{VX-240,H*.72},{VX-340,H*.82}},
            {{VX,VY},{VX+70,H*.40},{VX+160,H*.58},{VX+260,H*.70},{VX+380,H*.82}},
            {{VX,VY},{VX+20,H*.38},{VX+10,H*.54},{VX-20,H*.68},{VX,H*.82}}
        };
        for(double[][] r:raw){List<double[]> lr=new ArrayList<>();for(double[] p:r)lr.add(p);rivers.add(lr);flowProg.add(new double[]{0,0.03+rng.nextDouble()*0.02});}
    }

    private EP fireP(){EP p=new EP();p.x=VX+(rng.nextDouble()-0.5)*30;p.y=VY;double a=Math.toRadians(-80+(rng.nextDouble()-0.5)*40),sp=2+rng.nextDouble()*5*intensity;p.vx=Math.cos(a)*sp;p.vy=Math.sin(a)*sp;p.sz=4+rng.nextDouble()*8;p.max=0.4+rng.nextDouble()*0.8;p.isAsh=rng.nextDouble()<0.4;p.col=p.isAsh?Color.color(0.4,0.38,0.35):(rng.nextBoolean()?Color.web("#ff4400"):Color.web("#ffaa00"));return p;}
    private LB newBomb(){LB b=new LB();b.x=VX+(rng.nextDouble()-0.5)*20;b.y=VY;double a=Math.toRadians(-70+(rng.nextDouble()-0.5)*60),sp=4+rng.nextDouble()*8;b.vx=Math.cos(a)*sp;b.vy=Math.sin(a)*sp;b.sz=6+rng.nextDouble()*10;return b;}
    private AC newAsh(){AC a=new AC();a.x=VX+(rng.nextDouble()-0.5)*60;a.y=VY-20;a.vx=(rng.nextDouble()-0.5)*0.8;a.vy=-0.4-rng.nextDouble()*0.8;a.sz=30+rng.nextDouble()*60;a.max=4+rng.nextDouble()*4;a.alpha=0.3+rng.nextDouble()*0.4;return a;}

    private void startLoop(Canvas cv){
        GraphicsContext gc=cv.getGraphicsContext2D();
        timer=new AnimationTimer(){@Override public void handle(long now){
            if(last==0){last=now;return;}double dt=(now-last)/1e9;last=now;time+=dt;
            intensity=0.4+0.6*(0.5+0.5*Math.sin(time*0.6));
            for(int i=0;i<(int)(intensity*8);i++)if(rng.nextDouble()<dt*60)parts.add(fireP());
            if(rng.nextDouble()<dt*intensity*1.2)bombs.add(newBomb());
            if(rng.nextDouble()<dt*intensity*3)ash.add(newAsh());
            ashCover=Math.min(0.7,ashCover+dt*0.008*intensity);
            for(int i=0;i<flowProg.size();i++)flowProg.get(i)[0]=Math.min(1,flowProg.get(i)[0]+dt*flowProg.get(i)[1]*intensity);
            parts.removeIf(p->{p.x+=p.vx*60*dt;p.y+=p.vy*60*dt;p.vy+=0.08;p.life+=dt;p.sz+=p.isAsh?dt*4:0;return p.life>=p.max;});
            bombs.removeIf(b->{b.x+=b.vx*60*dt;b.y+=b.vy*60*dt;b.vy+=0.12;return b.y>H*0.86||b.x<0||b.x>W;});
            ash.removeIf(a->{a.x+=a.vx*60*dt;a.y+=a.vy*60*dt;a.sz+=dt*8;a.life+=dt;return a.life>=a.max;});
            draw(gc);
        }};timer.start();
    }

    private void draw(GraphicsContext gc){
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,lerp(Color.web("#1a0808"),Color.web("#080404"),ashCover)),
                new Stop(1,lerp(Color.web("#3a1808"),Color.web("#120604"),ashCover))));
        gc.fillRect(0,0,W,H);
        for(AC a:ash){double t=a.life/a.max;gc.setFill(Color.color(0.35,0.3,0.28,a.alpha*(1-t)));gc.fillOval(a.x-a.sz/2,a.y-a.sz/2,a.sz,a.sz);}
        // lava glow
        double glow=intensity;gc.setFill(new RadialGradient(0,0,VX,VY,220*glow,false,CycleMethod.NO_CYCLE,new Stop(0,Color.color(1,0.4,0.1,0.45*glow)),new Stop(1,Color.TRANSPARENT)));gc.fillOval(VX-220*glow,VY-220*glow,440*glow,440*glow);
        // volcano
        double gY=H*0.82;double[] xs={VX-40,VX-340,VX-480,VX+480,VX+340,VX+40},ys={VY,gY-50,gY,gY,gY-50,VY};
        gc.setFill(new LinearGradient(VX-400,0,VX+400,0,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#1a1008")),new Stop(0.5,Color.web("#2a1a0c")),new Stop(1,Color.web("#1a1008"))));gc.fillPolygon(xs,ys,6);
        gc.setFill(Color.web("#3a2010"));gc.fillOval(VX-55,VY-10,110,28);
        double fl=0.6+0.4*Math.sin(time*8);
        gc.setFill(new RadialGradient(0,0,VX,VY+5,40,false,CycleMethod.NO_CYCLE,new Stop(0,Color.color(1,0.7,0.1,0.9*fl)),new Stop(0.5,Color.color(1,0.3,0.05,0.7*fl)),new Stop(1,Color.TRANSPARENT)));gc.fillOval(VX-45,VY-5,90,22);
        gc.setFill(new LinearGradient(0,gY,0,H,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#1e1208")),new Stop(1,Color.web("#080604"))));gc.fillRect(0,gY,W,H-gY);
        // lava rivers
        for(int ri=0;ri<rivers.size();ri++){List<double[]> lr=rivers.get(ri);int mx=(int)(flowProg.get(ri)[0]*(lr.size()-1));if(mx<1)continue;gc.setLineWidth(8);for(int i=0;i<mx;i++){double[] a=lr.get(i),b=lr.get(i+1);double tt=(double)i/lr.size();gc.setStroke(lerp(Color.web("#ff6600"),Color.web("#aa1100"),tt));gc.strokeLine(a[0],a[1],b[0],b[1]);}gc.setLineWidth(1);}
        for(EP p:parts){double t=p.life/p.max;gc.setFill(Color.color(p.col.getRed(),p.col.getGreen(),p.col.getBlue(),p.isAsh?0.55*(1-t):0.9*(1-t*t)));gc.fillOval(p.x-p.sz/2,p.y-p.sz/2,p.sz,p.sz);}
        for(LB b:bombs){gc.setFill(Color.color(1,0.5,0.1,0.92));gc.fillOval(b.x-b.sz/2,b.y-b.sz/2,b.sz,b.sz);}
        // HUD
        gc.setFill(Color.color(0,0,0,0.5));gc.fillRoundRect(W-230,90,200,58,10,10);
        gc.setFill(Color.color(1,0.6,0.2,0.9));gc.setFont(Font.font("Courier New",FontWeight.BOLD,12));gc.fillText("ERUPTION INTENSITY",W-220,108);
        gc.setFill(Color.color(0.15,0.1,0.08));gc.fillRoundRect(W-220,113,170,13,6,6);
        gc.setFill(lerp(Color.web("#ff8800"),Color.web("#ff2200"),intensity));gc.fillRoundRect(W-220,113,170*intensity,13,6,6);
        gc.setFill(Color.web("#ffaa60"));gc.setFont(Font.font("Courier New",FontWeight.BOLD,11));gc.fillText(String.format("ASH COVER: %.0f%%",ashCover*100),W-220,140);
    }

    private Color lerp(Color a,Color b,double t){t=Math.max(0,Math.min(1,t));return Color.color(a.getRed()+(b.getRed()-a.getRed())*t,a.getGreen()+(b.getGreen()-a.getGreen())*t,a.getBlue()+(b.getBlue()-a.getBlue())*t);}
    private void wireESC(){setFocusTraversable(true);setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)goBack();});}
    private void goBack(){timer.stop();FadeTransition ft=new FadeTransition(Duration.millis(260),this);ft.setToValue(0);ft.setOnFinished(e->parentStage.setScene(returnScene));ft.play();}
    private static Text t(String tx,String c,double sz,boolean bold){Text x=new Text(tx);x.setFont(Font.font("Courier New",bold?FontWeight.BOLD:FontWeight.NORMAL,sz));x.setFill(Color.web(c));return x;}
}
