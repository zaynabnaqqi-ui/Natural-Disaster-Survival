import java.awt.*;
import javax.swing.*;

public class WorldScreen extends JPanel {

    final int WIDTH = 1310;
    final int HEIGHT = 730;

    String worldName;
    Color bgColor;
    JFrame parentWindow;
    GamePanel mainScreen;

    public WorldScreen(JFrame window, String name, Color color, GamePanel main) {
        this.parentWindow = window;
        this.worldName = name;
        this.bgColor = color;
        this.mainScreen = main;
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setLayout(null);

        // Back button
        JButton backBtn = new JButton("◀ BACK");
        backBtn.setBounds(30, 30, 120, 40);
        backBtn.setBackground(Color.BLACK);
        backBtn.setForeground(color);
        backBtn.setFont(new Font("Courier New", Font.BOLD, 16));
        backBtn.setFocusPainted(false);
        backBtn.setBorder(BorderFactory.createLineBorder(color, 2));
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        backBtn.addActionListener(e -> {
            parentWindow.getContentPane().removeAll();
            parentWindow.add(mainScreen);
            parentWindow.revalidate();
            parentWindow.repaint();
        });

        this.add(backBtn);
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Unique background color per world
        g.setColor(bgColor.darker().darker());
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // World name centered
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font("Courier New", Font.BOLD, 60));
        g2.setColor(bgColor);

        FontMetrics fm = g2.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(worldName)) / 2;
        int y = HEIGHT / 2;
        g2.drawString(worldName, x, y);

        // Subtitle
        g2.setFont(new Font("Courier New", Font.PLAIN, 24));
        g2.setColor(Color.WHITE);
        String sub = "Coming Soon...";
        int sx = (WIDTH - g2.getFontMetrics().stringWidth(sub)) / 2;
        g2.drawString(sub, sx, y + 50);
    }
}