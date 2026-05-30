import javax.swing.*;
import java.awt.*;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class GamePanel extends JPanel implements Runnable {

    final int WIDTH = 1366;
    final int HEIGHT = 770;

    BufferedImage backgroundImage;
    Thread gameThread;

    public GamePanel() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        // load background image
        try {
            backgroundImage = ImageIO.read(new File("Assets/background.png"));
        } catch (Exception e) {
            System.out.println("Background image not found!");
        }

        gameThread = new Thread(this);
        gameThread.start();
    }

    public void run() {
        while (true) {
            repaint();
            try { Thread.sleep(16); } catch (Exception e) {}
        }
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, WIDTH, HEIGHT, null);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);
        }
    }
}