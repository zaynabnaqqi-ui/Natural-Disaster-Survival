import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
public class GamePanel extends JPanel implements Runnable, MouseMotionListener, MouseListener {

    final int WIDTH = 1310;
    final int HEIGHT = 730;

    BufferedImage backgroundImage;
    Thread gameThread;
    JFrame parentWindow;

    String[] stages = {"FLOOD", "EARTHQUAKE", "VOLCANIC ACTIVITY", "HURRICANES", "WILDFIRE", "METEOROIDS", "MAMA'S CHAPPAL"};
    String[] stageInfo = {
    "Flooding occurs when water overflows onto normally dry land.",
    "Sudden shaking of the ground caused by tectonic plate movement.",
    "Eruption of magma, ash and gases from a volcano.",
    "Powerful tropical storms with strong winds and heavy rain.",
    "Uncontrolled fire that spreads rapidly through vegetation.",
    "Rocky debris from space that enters Earth's atmosphere.",
    "The most feared natural disaster. No survival tips exist."
    };
    int[] triOffsetStage = {0, 0, 0, 0, 0, 0, 0};
    Rectangle[] stageBounds = new Rectangle[7];
    int hoveredStage = -1;
    int lastHoveredStage = -1;
    int startXStage = 163;
    int[] stageY = {390, 416, 442, 469, 495, 521, 547};
    boolean showPopup = false;
    int popupStageIndex = -1;
    // World names and positions
    String[] worlds = {"SUGARCREST", "MUSHROOMVILE", "ENDER WORLD", "PACMAN"};
    Color[] worldColors = {Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE};

    // Triangle x offset per world for animation
    int[] triOffset = {0, 0, 0, 0};

    // Clickable area for each world (triangle + text)
    Rectangle[] worldBounds = new Rectangle[4];

    int hoveredIndex = -1;
    int lastHoveredIndex = -1;
    Clip hoverSound;
    Clip clickSound;
    // World text starts at x=855, each row y position
    int startX = 825;
    int[] worldY = {390, 416, 442, 469};

    public GamePanel(JFrame window) {
        this.parentWindow = window;
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        try {
            backgroundImage = ImageIO.read(new File("Assets/background.png"));
        } catch (Exception e) {
            System.out.println("Background image not found!");
        }

        // Define clickable bounds for each world (triangle to end of text)
        for (int i = 0; i < 4; i++) {
            worldBounds[i] = new Rectangle(startX, worldY[i] - 20, 350, 28);
        }
        for (int i = 0; i < 7; i++) {
            stageBounds[i] = new Rectangle(startXStage, stageY[i] - 20, 350, 28);
        }
    try {
      hoverSound = AudioSystem.getClip();
      hoverSound.open(AudioSystem.getAudioInputStream(new File("Assets/hover.wav")));
      clickSound = AudioSystem.getClip();
      clickSound.open(AudioSystem.getAudioInputStream(new File("Assets/click.wav")));
    }
    catch (Exception e) {
      System.out.println("Sound error: " + e.getMessage());
    }
        addMouseMotionListener(this);
        addMouseListener(this);

        gameThread = new Thread(this);
        gameThread.start();
    }

    public void run() {
    while (true) {
        // Worlds
        for (int i = 0; i < 4; i++) {
            if (i == hoveredIndex) {
                if (triOffset[i] < 10) triOffset[i] += 2;
            } else {
                if (triOffset[i] > 0) triOffset[i] -= 2;
            }
        }
        // Stages
        for (int i = 0; i < 7; i++) {
            if (i == hoveredStage) {
                if (triOffsetStage[i] < 10) triOffsetStage[i] += 2;
            } else {
                if (triOffsetStage[i] > 0) triOffsetStage[i] -= 2;
            }
        }

        repaint();
        try { Thread.sleep(16); } catch (Exception e) {}
    }
}

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw background
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, WIDTH, HEIGHT, null);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);
        }

        // Draw world options
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font("Courier New", Font.BOLD, 29));

        for (int i = 0; i < worlds.length; i++) {
            int tx = startX + triOffset[i];
            int ty = worldY[i];

            // Draw triangle
            int[] xp = {tx, tx + 14, tx};
            int[] yp = {ty - 14, ty - 7, ty};
            g2.setColor(worldColors[i]);
            g2.fillPolygon(xp, yp, 3);

            // Draw world name
            g2.setColor(worldColors[i]);
            g2.drawString(worlds[i], tx + 20, ty - 2);
        }
        for (int i = 0; i < stages.length; i++) {
          int tx = startXStage + triOffsetStage[i];
          int ty = stageY[i];

          int[] xp = {tx, tx + 14, tx};
          int[] yp = {ty - 14, ty - 7, ty};
          g2.setColor(Color.WHITE);
          g2.fillPolygon(xp, yp, 3);

          g2.setColor(Color.WHITE);
          g2.drawString(stages[i], tx + 20, ty - 2);
        }

    // Draw popup
    if (showPopup && popupStageIndex >= 0) {
    g2.setColor(new Color(0, 0, 0, 200));
g2.fillRoundRect(360, 560, 650, 110, 20, 20);  // moved up a bit
g2.setColor(Color.WHITE);
g2.setFont(new Font("Courier New", Font.BOLD, 17));
g2.drawString(stages[popupStageIndex], 380, 590);   // 20px from left edge of box
g2.setFont(new Font("Courier New", Font.PLAIN, 16));
g2.drawString(stageInfo[popupStageIndex], 380, 618);
g2.setColor(Color.GRAY);
g2.setFont(new Font("Courier New", Font.PLAIN, 13));
g2.drawString("click anywhere to close", 380, 648);
     }
    }
 
    // Mouse hover detection
    public void mouseMoved(MouseEvent e) {
    hoveredStage = -1;     
    hoveredIndex = -1;
    for (int i = 0; i < 4; i++) {
        if (worldBounds[i].contains(e.getPoint())) {
            hoveredIndex = i;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (hoveredIndex != lastHoveredIndex && hoverSound != null) {
                hoverSound.setFramePosition(0);
                hoverSound.start();
            }
            lastHoveredIndex = hoveredIndex;
            return;
        }
    }
    lastHoveredIndex = -1;
    for (int i = 0; i < 7; i++) {
    if (stageBounds[i].contains(e.getPoint())) {
        hoveredStage = i;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (hoveredStage != lastHoveredStage && hoverSound != null) {
            hoverSound.setFramePosition(0);
            hoverSound.start();
        }
        lastHoveredStage = hoveredStage;
        return;
    }
   }
    lastHoveredStage = -1;
    setCursor(Cursor.getDefaultCursor());
}

    public void mouseClicked(MouseEvent e) {
        for (int i = 0; i < 4; i++) {
            if (worldBounds[i].contains(e.getPoint())) {
                // Open world screen
                if (clickSound != null) {
                    clickSound.setFramePosition(0);
                    clickSound.start();
                }
                parentWindow.getContentPane().removeAll();
                WorldScreen ws = new WorldScreen(parentWindow, worlds[i], worldColors[i], this);
                parentWindow.add(ws);
                parentWindow.revalidate();
                parentWindow.repaint();
                return;
            }
        }
        for (int i = 0; i < 7; i++) {
          if (stageBounds[i].contains(e.getPoint())) {
             if (clickSound != null) {
                 clickSound.setFramePosition(0);
                 clickSound.start();
        }
        popupStageIndex = i;
        showPopup = true;
        repaint();
        return;
     }
    }
     // Close popup on any other click
     showPopup = false;
     repaint();
    }

    public void mouseDragged(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}