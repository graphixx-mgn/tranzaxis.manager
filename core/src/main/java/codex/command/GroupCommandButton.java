package codex.command;

import codex.model.Entity;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Класс реализует поведение кнопки главной в группе команд {@link EntityCommand}, связанных схожим поведением или
 * назначением. Команды объединяются в двухуровневую иерархию при помощи аннотации {@link EntityCommand.Definition}.
 * Кнопка групповой команды имеет дополнительный элемент для показа выпадающего меню со списком второстепенных команд.
 */
@ThreadSafe
public final class GroupCommandButton extends CommandButton implements MouseListener, PopupMenuListener, IGroupCommandButton {

    private static final ImageIcon ARROW = ImageUtils.getByPath("/images/arrow.png");

    private final JButton popup;
    private final JPopupMenu menu;

    /**
     * Стандартный конструктор кнопки основной команды сущности.
     * @param command Ссылка на вызываемую команду.
     */
    GroupCommandButton(EntityCommand<Entity> command) {
        super(command);
        button.addMouseListener(this);
        button.getModel().removeChangeListener(this);
        addMouseListener(this);

        popup = new JButton(ARROW);
        popup.setFocusPainted(false);
        popup.setOpaque(false);
        popup.setContentAreaFilled(false);
        popup.setBorder(new EmptyBorder(0, 1, 0, 0));
        popup.setMaximumSize(new Dimension(15, 28));
        popup.addMouseListener(this);

        menu = new JPopupMenu();
        menu.setForeground(Color.BLACK);
        menu.addPopupMenuListener(this);

        popup.addActionListener((ActionEvent ev) -> menu.show(
                button,
                button.getBounds().x - 2 ,
                button.getBounds().y  + button.getBounds().height + 1)
        );
        add(popup);
    }

    @Override
    public void addChildCommand(EntityCommand<Entity> command) {
        CommandButton cmdButton = new CommandButton(command, true);
        SwingUtilities.invokeLater(() -> {
            cmdButton.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
            cmdButton.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    menu.setVisible(false);
                }
            });
            menu.add(cmdButton);
        });
    }

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
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        setBorder(PRESS_BORDER);
        setBackground(HOVER_COLOR);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (!(button.getModel().isRollover() || popup.getModel().isRollover())) {
            setBorder(EMPTY_BORDER);
            setBackground(null);
            popup.setBorder(new EmptyBorder(0, 1, 0, 0));
        } else {
            setBorder(HOVER_BORDER);
        }
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
        popupMenuWillBecomeInvisible(e);
    }

}