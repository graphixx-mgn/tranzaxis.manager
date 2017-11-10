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
        
        JLabel icon = new JLabel(ImageUtils.getByPath("/images/splash.png"));
        add(icon, BorderLayout.NORTH);
        
        progress = new JProgressBar(0, 100);
        progress.setBorderPainted(false);
        progress.setStringPainted(true);
        progress.setUI(new MotifProgressBarUI());
        progress.setOpaque(true);
        progress.setBackground(Color.WHITE);
        progress.setForeground(Color.decode("#D93B3B"));
        progress.setString("");
        progress.setPreferredSize(new Dimension(getWidth(), 6));
        
        progressText = new JLabel("");
        progressText.setOpaque(true);
        progressText.setBackground(Color.WHITE);
        progressText.setBorder(new EmptyBorder(0, 5, 5, 0));
        
        JPanel info = new JPanel(new BorderLayout());
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
