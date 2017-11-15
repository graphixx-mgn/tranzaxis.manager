package manager.ui;

import codex.utils.ImageUtils;
import com.sun.java.swing.plaf.motif.MotifProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Заставка загрузки приложения с прогрессом процесса инициализации.
 */
public final class Splash extends JFrame {
    
    private final JProgressBar progress;
    private final JLabel       progressText;
    
    /**
     * Конструктор заставки.
     */
    public Splash() {
        super();
        setSize(400, 220);
        setUndecorated(true);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        JLabel icon = new JLabel(ImageUtils.getByPath("/images/splash.png"));
        icon.setBorder(new MatteBorder(1, 1, 0, 1, Color.GRAY));
        add(icon, BorderLayout.NORTH);
        
        progress = new JProgressBar(0, 100);
        progress.setBorder(new EmptyBorder(0, 0, 0, 0));
        progress.setBorderPainted(false);
        progress.setStringPainted(true);
        progress.setUI(new MotifProgressBarUI());
        progress.setOpaque(true);
        progress.setBackground(Color.WHITE);
        progress.setForeground(Color.decode("#D93B3B"));
        progress.setString("");
        progress.setPreferredSize(new Dimension(getWidth(), 3));
        
        progressText = new JLabel("");
        progressText.setOpaque(true);
        progressText.setBackground(Color.WHITE);
        progressText.setBorder(new EmptyBorder(0, 5, 5, 0));
        
        JPanel info = new JPanel(new BorderLayout());
        info.setBorder(new MatteBorder(0, 1, 1, 1, Color.GRAY));
        info.add(progress, BorderLayout.CENTER);
        info.add(progressText, BorderLayout.SOUTH);
                
        add(info, BorderLayout.SOUTH);
    }
    
    /**
     * Установить процент прогресса (0-100).
     */
    public void setProgress(int percent) {
        if (percent >= 0 && percent <= 100) {
            progress.setValue(percent);
        }
    }
    
    /**
     * Установить процент прогресса (0-100) и вывести текстовое пояснение.
     */
    public void setProgress(int percent, String text) {
        if (percent >= 0 && percent <= 100) {
            progress.setValue(percent);
        }
        progressText.setText(text);
    }
    
}
