package manager.ui.splash;


import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;


public final class SplashScreen extends JFrame {
    
    private JPanel infoPanel;
    private ImagePanel imagePanel;
    private Rectangle2D.Double progressArea;
    private Rectangle2D.Double descriptionArea;

    public SplashScreen() {
        setUndecorated(true);
        setLayout(new BorderLayout());
        setType(javax.swing.JFrame.Type.UTILITY);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        ImageIcon splash = ImageUtils.getByPath("/images/splash.png");
        BufferedImage bimage = new BufferedImage(splash.getIconWidth(), splash.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(splash.getImage(), 0, 0, null);
        bGr.dispose();
        Dimension size = new Dimension(splash.getIconWidth(), splash.getIconHeight());
        
        imagePanel = new ImagePanel();
        imagePanel.setPreferredSize(size);
        imagePanel.setBorder(new MatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY));
        imagePanel.setStretchMode(ImagePanel.STRETCH_PRESERVE);
        imagePanel.setImage(bimage);
        
        JLabel logo = new JLabel(ImageUtils.getByPath("/images/logo.png"));
        logo.setOpaque(true);
        logo.setBackground(new Color(255, 255, 255, 128));
        
        JPanel logoPanel = new JPanel();
        logoPanel.setOpaque(false);
        logoPanel.setLayout(new BoxLayout(logoPanel, BoxLayout.PAGE_AXIS));
        logoPanel.add(logo);

        imagePanel.add(logoPanel, BorderLayout.EAST);
        
        infoPanel = new JPanel();
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setBorder( new MatteBorder(0, 1, 1, 1, Color.LIGHT_GRAY));
        infoPanel.setPreferredSize(new Dimension(size.width, 35));
        
        getContentPane().add(imagePanel, BorderLayout.CENTER);
        getContentPane().add(infoPanel,  BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        progressArea = new Rectangle2D.Double(
                0, infoPanel.getHeight()-10, 
                infoPanel.getWidth(), 3
        );
        descriptionArea = new Rectangle2D.Double(
                0, infoPanel.getHeight()-30,
                infoPanel.getWidth(), 20
        );
        SwingUtilities.invokeLater(() -> {
            setProgress(0);
        });
        pack();
    }
    
    public void setProgress(int progress, String text) {
        setProgress(progress);
        if (isVisible()) {
            Graphics2D g = (Graphics2D) infoPanel.getGraphics();
            g.setPaint(infoPanel.getBackground());
            g.fillRect(
                (int) descriptionArea.getX()+1,
                (int) descriptionArea.getY(),
                (int) descriptionArea.getWidth()-2,
                (int) descriptionArea.getHeight()
            );

            g.setPaint(Color.BLACK);
            g.drawString(
                    text, 
                    (int)(descriptionArea.getX()+5),
                    (int)(descriptionArea.getY()+g.getFontMetrics().getHeight())
            );
        }
        try { Thread.sleep(100); } catch (InterruptedException e) {}
    }
    
    public void setProgress(int progress) {
        if (isVisible()) {
            Graphics2D g = (Graphics2D) infoPanel.getGraphics();

            int x   = (int) progressArea.getMinX()+1;
            int y   = (int) progressArea.getMinY();
            int wid = (int) progressArea.getWidth()-2;
            int hgt = (int) progressArea.getHeight();
            
            g.setPaint(Color.LIGHT_GRAY);
            g.fillRect(x, y, wid, hgt);

            int doneWidth = Math.round(progress * wid / 100.f);
                doneWidth = Math.max(0, Math.min(doneWidth, wid));

            g.setPaint(Color.RED);
            g.fillRect(x, y, doneWidth, hgt);
        }
    }
    
}
