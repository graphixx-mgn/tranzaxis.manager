package manager.ui.splash;


import codex.utils.ImageUtils;
import com.sixlegs.png.AnimatedPngImage;
import com.sixlegs.png.Animator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.MatteBorder;


public final class SplashScreen extends JFrame {
    
    private JPanel infoPanel;
    private ImagePanel imagePanel;
    private Rectangle2D.Double progressArea;
    private Rectangle2D.Double descriptionArea;

    public SplashScreen(String path) {
        setUndecorated(true);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        
        imagePanel = new ImagePanel();
        imagePanel.setStretchMode(ImagePanel.STRETCH_PRESERVE);
        imagePanel.setBorder(new MatteBorder(1, 1, 0, 1, Color.decode("#0070C5")));
        getContentPane().add(imagePanel, BorderLayout.CENTER);
        
        JLabel logo = new JLabel(ImageUtils.getByPath("/images/logo.png"));
        logo.setOpaque(true);
        logo.setBackground(new Color(255, 255, 255, 128));
        
        JPanel logoPanel = new JPanel();
        logoPanel.setOpaque(false);
        logoPanel.setLayout(new BoxLayout(logoPanel, BoxLayout.PAGE_AXIS));
        logoPanel.add(logo);

        imagePanel.add(logoPanel, BorderLayout.EAST);
        
        infoPanel = new JPanel();
        infoPanel.setBackground(Color.decode("#000332"));
        infoPanel.setBorder(new MatteBorder(0, 1, 1, 1, Color.decode("#0070C5")));
        getContentPane().add(infoPanel, BorderLayout.SOUTH);
        
        File file = new File(this.getClass().getClassLoader().getResource(path).getFile());
        try {
            AnimatedPngImage png = new AnimatedPngImage();
            png.read(file);
            infoPanel.setPreferredSize(new Dimension(png.getWidth(), 50));
            imagePanel.setPreferredSize(new Dimension(png.getWidth(), png.getHeight()));
            BufferedImage[] frames = new AnimatedPngImage().readAllFrames(file);
            
            pack();
            setLocationRelativeTo(null);
            setVisible(true);
                        
            if (png.isAnimated()) {
                imagePanel.setImage(frames[0]);
                final BufferedImage target = imagePanel.getGraphicsConfiguration().createCompatibleImage(
                        png.getWidth(), png.getHeight(),
                        Transparency.TRANSLUCENT
                );
                final Animator animator = new Animator(png, frames, target);
                Timer timer = new Timer(25, null);
                timer.setInitialDelay(0);
                timer.addActionListener(animator);
                
                timer.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        imagePanel.setImage(target);
                    }
                });
                timer.start();
            } else {
                imagePanel.setImage(frames[0]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        progressArea = new Rectangle2D.Double(
                0, infoPanel.getHeight()-10, 
                infoPanel.getWidth(), 5
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

            g.setPaint(Color.WHITE);
            g.drawString(
                    text, 
                    (int)(descriptionArea.getX()+5),
                    (int)(descriptionArea.getY()+g.getFontMetrics().getHeight())
            );
        }
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

            g.setPaint(Color.decode("#0070C5"));
            g.fillRect(x, y, doneWidth, hgt);
        }
        try { Thread.sleep(300); } catch (InterruptedException e) {}
    }
    
}
