import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    public static final int WIDTH  = 1310;
    public static final int HEIGHT = 730;

    @Override
    public void start(Stage stage) {
        // javafx calls this automatically when the app boots up — stage is the window
        GamePanel gamePanel = new GamePanel(stage);

        // scene is what lives inside the window, gamepanel is the first thing shown
        Scene scene = new Scene(gamePanel, WIDTH, HEIGHT);
        stage.setTitle("Natural Disaster Survival");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        // this hands control over to javafx which eventually calls start()
        launch(args);
    }
}