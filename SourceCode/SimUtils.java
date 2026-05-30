import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class SimUtils {

    public static final int WIDTH  = 1310;
    public static final int HEIGHT = 730;

    /** Creates a styled BACK TO MENUbutton. */
    public static Button backButton(Runnable onBack) {
        Button btn = new Button("◀  BACK TO MENU");
        btn.setFont(Font.font("Courier New", FontWeight.BOLD, 13));
        btn.setPrefWidth(170);
        btn.setPrefHeight(38);
        btn.setCursor(javafx.scene.Cursor.HAND);
        applyStyle(btn, false);
        btn.setOnMouseEntered(e -> {
            applyStyle(btn, true);
            ScaleTransition st = new ScaleTransition(Duration.millis(110), btn);
            st.setToX(1.06); st.setToY(1.06); st.play();
        });
        btn.setOnMouseExited(e -> {
            applyStyle(btn, false);
            ScaleTransition st = new ScaleTransition(Duration.millis(110), btn);
            st.setToX(1); st.setToY(1); st.play();
        });
        btn.setOnAction(e -> onBack.run());
        return btn;
    }

    private static void applyStyle(Button btn, boolean hover) {
        String border = hover ? "#ff6a00" : "#4a3a20";
        String fg     = hover ? "#ffcc80" : "#8a7a60";
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: " + border + ";" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: " + fg + ";");
    }

    public static HBox footerBox(Button backBtn) {
        HBox box = new HBox(backBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 18, 28));
        return box;
    }
}
