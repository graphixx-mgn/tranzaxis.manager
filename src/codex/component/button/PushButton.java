package codex.component.button;

import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.ToolTipUtils;

/**
 * Простая реализация-декоратор кнопки. Используется для упраления в диалоговых окнах.
 */
public class PushButton extends JPanel implements IButton, ChangeListener {
    
    protected final JButton button;
    
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
    }
    
    @Override
    public void addActionListener(ActionListener listener) {
        button.addActionListener((event) -> {
            listener.actionPerformed(new ActionEvent(PushButton.this, event.getID(), event.getActionCommand()));
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
        button.setIcon(icon);
        button.setDisabledIcon(ImageUtils.grayscale((ImageIcon) icon));
    }

    @Override
    public final void setHint(String text) {
//        button.setToolTipText(text);
        BalloonTip tooltipBalloon = new BalloonTip(
                (JComponent) this.button, 
                new JLabel(
                        text,
                        ImageUtils.resize(
                            ImageUtils.getByPath("/images/event.png"), 
                            16, 16
                        ), 
                        SwingConstants.LEADING), 
                new EdgedBalloonStyle(Color.WHITE, Color.GRAY), 
                false
        );
        ToolTipUtils.balloonToToolTip(tooltipBalloon, 1000, 3000);
    }
    
    @Override
    public final void setText(String text) {
        button.setText(text);
    }
    
    @Override
    public final void setEnabled(boolean enabled) {
        button.setEnabled(enabled);
    }
    
    @Override
    public final boolean isEnabled() {
        return button.isEnabled();
    }

    @Override
    public void setFocusable(boolean focusable) {
        button.setFocusable(focusable);
    }
}
