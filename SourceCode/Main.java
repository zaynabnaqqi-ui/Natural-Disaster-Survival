import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        JFrame window = new JFrame("Natural Disaster Survival");
        GamePanel game = new GamePanel(window);
        window.add(game);
        window.pack();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }
}