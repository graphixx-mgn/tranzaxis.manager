package codex.presentation;

import codex.command.EntityCommand;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Панель команд презентаций редактора и селектора сущности.
 */
public final class CommandPanel extends Box {
    
    /**
     * Конструктор панели.
     */
    public CommandPanel() {
        super(BoxLayout.X_AXIS);
        setBorder(new CompoundBorder(
                new EmptyBorder(2, 5, 2, 5),
                new CompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Color.GRAY), 
                        new EmptyBorder(3, 0, 3, 0)
                )
        ));
    }
    
    /**
     * Добавить команды на панель.
     */
    public void addCommands(EntityCommand ... commands) {
        for (EntityCommand command : commands) {
            add((JComponent) command.getButton());
            add(Box.createHorizontalStrut(5));
        }
    }
    
    /**
     * Добавить разделитель.
     */
    public void addSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setMaximumSize(new Dimension(1, 40));
        add(sep, BorderLayout.LINE_START);
        add(Box.createHorizontalStrut(5));
    }
    
}
