package codex.component.button;

import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.ToolTipUtils;

/**
 * Кнопка главной (первой) команды в группе.
 */
public final class GroupButton extends JPanel implements IButton, MouseListener, PopupMenuListener {
    
    private final JButton    button;
    private final JButton    popup;
    private final JPopupMenu menu;
    
    /**
     * Конструктор кнопки
     * @param icon Иконка кнопки. 
     */
    public GroupButton(ImageIcon icon) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setBorder(IButton.EMPTY_BORDER);
        addMouseListener(this);
        
        button = new JButton();
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(5, 8, 5, 0));
        button.addMouseListener(this);
        add(button);
        
        add(Box.createHorizontalStrut(3));
        popup = new JButton(ImageUtils.getByPath("/images/arrow.png"));
        popup.setFocusPainted(false);
        popup.setOpaque(false);
        popup.setContentAreaFilled(false);
        popup.setBorder(new EmptyBorder(0, 1, 0, 0));
        popup.setMaximumSize(new Dimension(15, 28));
        popup.addMouseListener(this);
        add(popup);
        
        menu = new JPopupMenu();
        menu.setForeground(Color.BLACK);
        menu.addPopupMenuListener(this);
        
        popup.addActionListener((ActionEvent ev) -> {
            menu.show(button, button.getBounds().x - 2 , button.getBounds().y  + button.getBounds().height + 1);
        });
        
        setIcon(icon);
    }
    
    /**
     * Создание элемента меню для вторичных команд группы.
     * @param icon Иконка кнопки.
     * @param title Текст подписи кнопки.
     */
    public GroupItemButton createGroupItem(ImageIcon icon, String title) {
        GroupItemButton item = new GroupItemButton(icon, title);
        menu.add(item);
        return item;
    }

    @Override
    public void addActionListener(ActionListener listener) {
        button.addActionListener(listener);
    }

    @Override
    public void setIcon(Icon icon) {
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
        if (text != null) {
            BalloonTip tooltipBalloon = new BalloonTip(
                    this.button, 
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
            ToolTipUtils.balloonToToolTip(tooltipBalloon, 2000, 3000);
        }
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
    public void setInactive(boolean inactive) {
        button.setFocusable(!inactive);
    }

    @Override
    public boolean isInactive() {
        return !button.isFocusable();
    }

    @Override
    public void click() {
        button.doClick();
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {
        if (!menu.isVisible()) {
            setBorder(HOVER_BORDER);
            setBackground(HOVER_COLOR);
            popup.setBorder(new MatteBorder(0, 1, 0, 0, Color.GRAY));
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (!menu.isVisible()) {
            setBorder(EMPTY_BORDER);
            setBackground(null);
            popup.setBorder(new EmptyBorder(0, 1, 0, 0));
        }
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        setBorder(PRESS_BORDER);
        setBackground(HOVER_COLOR);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        setBorder(EMPTY_BORDER);
        setBackground(null);
        popup.setBorder(new EmptyBorder(0, 1, 0, 0));
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {}
    
}
