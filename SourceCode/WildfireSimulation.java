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

import java.util.*;

/** Wildfire simulation — forest fire spreading tree-to-tree. */
public class WildfireSimulation extends StackPane {

    private static final int W=SimUtils.WIDTH, H=SimUtils.HEIGHT;
    private final Stage parentStage; private final Scene returnScene;
    private final List<Tree> trees=new ArrayList<>();
    private final List<FP> flames=new ArrayList<>();
    private final List<Smoke> smokes=new ArrayList<>();
    private final Random rng=new Random();
    private double time=0; private int burned=0;
    private AnimationTimer timer; private long last=0;

    public WildfireSimulation(Stage stage,Scene ret){
        parentStage=stage;returnScene=ret;setPrefSize(W,H);
        Canvas cv=new Canvas(W,H);getChildren().add(cv);
        buildOverlay();genForest();if(!trees.isEmpty())trees.get(0).state=1;
        startLoop(cv);wireESC();
    }

    private void buildOverlay(){
        Text title=t("🔥  WILDFIRE SIMULATION","#ff8c30",27,true);
        Text info=t("Fire spreading through the forest!","#cc6020",13,false);
        Button back=SimUtils.backButton(this::goBack);
        VBox top=new VBox(5,title,info);top.setPadding(new Insets(18,0,0,28));
        BorderPane ov=new BorderPane();ov.setBackground(Background.EMPTY);
        ov.setTop(top);ov.setBottom(SimUtils.footerBox(back));ov.setPrefSize(W,H);
        getChildren().add(ov);
    }

    private static class Tree{double x,y,th,cr;int state=0;double bp,bt;Color crown;}
    private static class FP{double x,y,vx,vy,life,max,sz;Color col;}
    private static class Smoke{double x,y,vx,vy,sz,life,max;}

    private void genForest(){
        int cols=28,rows=3; double cw=(double)W/cols;
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            Tree tr=new Tree();tr.x=col*cw+cw*0.5+(rng.nextDouble()-0.5)*cw*0.5;
            tr.y=H*(0.65+row*0.08);tr.th=30+rng.nextDouble()*40;tr.cr=18+rng.nextDouble()*32;
            tr.crown=Color.color(0.05+rng.nextDouble()*0.15,0.3+rng.nextDouble()*0.35,0.05+rng.nextDouble()*0.1);
            trees.add(tr);
        }
    }

    private void flame(double x,double y,double r){
        Color[] c={Color.web("#ff6600"),Color.web("#ff3300"),Color.web("#ffaa00"),Color.web("#ffdd00")};
        for(int i=0;i<(int)(r/5);i++){FP f=new FP();f.x=x+(rng.nextDouble()-0.5)*20;f.y=y;
            f.vx=(rng.nextDouble()-0.5)*1.5;f.vy=-1.5-rng.nextDouble()*3;f.max=0.25+rng.nextDouble()*0.35;
            f.sz=4+rng.nextDouble()*8;f.col=c[rng.nextInt(c.length)];flames.add(f);}
    }
    private void smoke(double x,double y){Smoke s=new Smoke();s.x=x+(rng.nextDouble()-0.5)*15;s.y=y;
        s.vx=(rng.nextDouble()-0.5)*0.5;s.vy=-0.5-rng.nextDouble();s.sz=15+rng.nextDouble()*25;s.max=2+rng.nextDouble()*2;smokes.add(s);}

    private void startLoop(Canvas cv){
        GraphicsContext gc=cv.getGraphicsContext2D();
        timer=new AnimationTimer(){@Override public void handle(long now){
            if(last==0){last=now;return;}double dt=(now-last)/1e9;last=now;time+=dt;
            burned=0;
            for(Tree tr:trees){
                if(tr.state==1){tr.bt+=dt;tr.bp=Math.min(1,tr.bt/6);
                    double topY=tr.y-tr.th-tr.cr;
                    if(rng.nextDouble()<dt*12)flame(tr.x,topY+tr.cr,tr.cr);
                    if(rng.nextDouble()<dt*4)smoke(tr.x,topY);
                    if(rng.nextDouble()<dt*0.4)spread(tr);
                    if(tr.bp>=1)tr.state=2;}
                if(tr.state==2)burned++;
            }
            flames.removeIf(f->{f.x+=f.vx*60*dt;f.y+=f.vy*60*dt;f.life+=dt;return f.life>=f.max;});
            smokes.removeIf(s->{s.x+=s.vx*60*dt;s.y+=s.vy*60*dt;s.sz+=dt*5;s.life+=dt;return s.life>=s.max;});
            draw(gc);
        }};timer.start();
    }

    private void spread(Tree src){double r=120;
        for(Tree o:trees){if(o.state!=0)continue;double d=Math.hypot(o.x-src.x,o.y-src.y);
            if(d<r&&rng.nextDouble()<(1-d/r)*0.06)o.state=1;}}

    private void draw(GraphicsContext gc){
        double ff=(double)burned/Math.max(1,trees.size());
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,lerp(Color.web("#4a6080"),Color.web("#2a1008"),ff)),
                new Stop(1,lerp(Color.web("#c08060"),Color.web("#601008"),ff))));
        gc.fillRect(0,0,W,H);
        double gY=H*0.90;
        gc.setFill(new LinearGradient(0,gY,0,H,false,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#3a2810")),new Stop(1,Color.web("#0e0904"))));
        gc.fillRect(0,gY,W,H-gY);
        for(Smoke s:smokes){double t=s.life/s.max;gc.setFill(Color.color(0.25,0.22,0.20,0.35*(1-t)));gc.fillOval(s.x-s.sz/2,s.y-s.sz/2,s.sz,s.sz);}
        for(Tree tr:trees)drawTree(gc,tr);
        for(FP f:flames){double t=f.life/f.max;gc.setFill(Color.color(f.col.getRed(),f.col.getGreen(),f.col.getBlue(),1-t));gc.fillOval(f.x-f.sz/2,f.y-f.sz/2,f.sz,f.sz);}
        gc.setFill(Color.color(0,0,0,0.5));gc.fillRoundRect(W-240,90,210,42,10,10);
        gc.setFill(Color.color(1,0.6,0.2,0.9));gc.setFont(Font.font("Courier New",FontWeight.BOLD,13));
        gc.fillText(String.format("BURNED: %d / %d",burned,trees.size()),W-228,112);
        gc.fillText(String.format("SPREAD: %.0f%%",ff*100),W-228,128);
    }

    private void drawTree(GraphicsContext gc,Tree tr){
        if(tr.state==2){gc.setFill(Color.color(0.1,0.08,0.06));gc.fillRect(tr.x-4,tr.y-tr.th*0.3,8,tr.th*0.3);return;}
        gc.setFill(Color.web("#3a2810"));gc.fillRect(tr.x-4,tr.y-tr.th,8,tr.th);
        Color c=tr.crown;
        if(tr.state==1)c=lerp(c,Color.color(0.15,0.05,0.02),tr.bp);
        gc.setFill(c);gc.fillOval(tr.x-tr.cr,tr.y-tr.th-tr.cr*1.8,tr.cr*2,tr.cr*2);
    }

    private Color lerp(Color a,Color b,double t){t=Math.max(0,Math.min(1,t));return Color.color(a.getRed()+(b.getRed()-a.getRed())*t,a.getGreen()+(b.getGreen()-a.getGreen())*t,a.getBlue()+(b.getBlue()-a.getBlue())*t);}
    private void wireESC(){setFocusTraversable(true);setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)goBack();});}
    private void goBack(){timer.stop();FadeTransition ft=new FadeTransition(Duration.millis(260),this);ft.setToValue(0);ft.setOnFinished(e->parentStage.setScene(returnScene));ft.play();}
    private static Text t(String tx,String c,double sz,boolean bold){Text x=new Text(tx);x.setFont(Font.font("Courier New",bold?FontWeight.BOLD:FontWeight.NORMAL,sz));x.setFill(Color.web(c));return x;}
}
