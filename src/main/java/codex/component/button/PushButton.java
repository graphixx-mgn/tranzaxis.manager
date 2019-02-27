package codex.component.button;

import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Event;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.TimingUtils;

/**
 * Простая реализация-декоратор кнопки. Используется для упраления в диалоговых окнах.
 */
public class PushButton extends JPanel implements IButton, ChangeListener {
    
    protected final JButton button;
    protected       String  hint;
    
    /**
     * Конструктор экземпляра кнопки.
     * @param icon Иконка устанавливаемая на кнопку, может быть NULL, если требуется 
     * создать кнопку только с текстом.
     * @param title Поддпись кнопки, может быть NULL, если требуется создать кнопку 
     * только с иконкой.
     */
    public PushButton(ImageIcon icon, String title) {
        super();
        
        if (icon == null && title == null) {
            throw new IllegalStateException("It is not possible don't specify 'icon' nor 'title'");
        }
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setBorder(IButton.EMPTY_BORDER);
        
        button = new JButton(title);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(5, 8, 5, 8));
        button.getModel().addChangeListener(this);
        setIcon(icon);
        add(button);
        
        button.addMouseListener(new MouseAdapter() {
            private BalloonTip tooltipBalloon;
            private final Timer delayTimer = new Timer(1000, (ActionEvent event) -> {
                if (hint != null && tooltipBalloon == null) {
                    tooltipBalloon = new BalloonTip(
                            button, 
                            new JLabel(
                                    hint, 
                                    ImageUtils.resize(
                                        ImageUtils.getByPath("/images/event.png"), 
                                        16, 16
                                    ), 
                                    SwingConstants.LEADING
                            ), 
                            new EdgedBalloonStyle(Color.WHITE, Color.GRAY), 
                            false
                    );
                    TimingUtils.showTimedBalloon(tooltipBalloon, 4000);
                } 
            }) {{
                setRepeats(false);
            }};
            
            @Override
            public void mouseEntered(MouseEvent e) {
                delayTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (delayTimer.isRunning()) {
                    delayTimer.stop();
                }
                if (tooltipBalloon != null) {
                    tooltipBalloon.closeBalloon();
                    tooltipBalloon = null;
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (delayTimer.isRunning()) {
                    delayTimer.stop();
                }
                if (tooltipBalloon != null) {
                    tooltipBalloon.closeBalloon();
                    tooltipBalloon = null;
                }
            }
        });
    }
    
    @Override
    public void addActionListener(ActionListener listener) {
        button.addActionListener((event) -> {
            //if (!isInactive()) {
                listener.actionPerformed(new ActionEvent(PushButton.this, event.getID(), event.getActionCommand()));
            //}
        });
    }

    /**
     * Слушатель внешних событий для измения внешнего вида кнопки.
     * @param event Экземляр внешнего события.
     */
    @Override
    public void stateChanged(ChangeEvent event) {
        redraw();
    }
    
    /**
     * Медод перерисовки кнопки при воздействии внешних событий, таких как наведение
     * и нажатие мыши.
     */
    protected void redraw() {
        if (button.getModel().isPressed() && button.isFocusable()) {
            setBorder(PRESS_BORDER);
            setBackground(PRESS_COLOR);
        } else if (button.getModel().isRollover() && button.isFocusable()) {
            setBorder(HOVER_BORDER);
            setBackground(HOVER_COLOR);
        } else {
            setBorder(EMPTY_BORDER);
            setBackground(null);
        }
    }

    @Override
    public final void setIcon(Icon icon) {
        if (!icon.equals(button.getIcon())) {
            button.setIcon(icon);
            button.setDisabledIcon(ImageUtils.grayscale((ImageIcon) icon));
        }
    }
    
    @Override
    public Icon getIcon() {
        return button.getIcon();
    }

    @Override
    public void setHint(String text) {
        hint = text;
    }
    
    @Override
    public void setText(String text) {
        button.setText(text);
    }
    
    @Override
    public String getText() {
        return button.getText();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        button.setEnabled(enabled);
    }
    
    @Override
    public boolean isEnabled() {
        return button.isEnabled();
    }

    @Override
    public void setVisible(boolean visible) {
        button.setVisible(visible);
    }

    @Override
    public void click() {
        for (ActionListener listener : button.getActionListeners()) {
            listener.actionPerformed(new ActionEvent(this, Event.ACTION_EVENT, this.getText()));
        }
    }

}
