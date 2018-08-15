package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.GroupButton;
import codex.component.button.GroupItemButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
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
    
    private final Map<String, GroupButton> groupButtons = new HashMap<>();
    
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
            if (command.getGroupId() != null) {
                if (!groupButtons.containsKey(command.getGroupId())) {
                    GroupButton groupButton = new GroupButton(command.getIcon());
                    groupButton.setHint(command.toString());
                    groupButton.addActionListener(command);
                    try {
                        Field button = EntityCommand.class.getDeclaredField("button");
                        button.setAccessible(true);
                        button.set(command, groupButton);
                    } catch (NoSuchFieldException | IllegalAccessException e) {}
                    
                    groupButtons.put(command.getGroupId(), groupButton);
                    add((JComponent) groupButton);
                    add(Box.createHorizontalStrut(5));
                } else {
                    GroupItemButton groupItem = groupButtons.get(command.getGroupId()).createGroupItem(command.getIcon(), command.toString());
                    groupItem.addActionListener(command);
                    try {
                        Field button = EntityCommand.class.getDeclaredField("button");
                        button.setAccessible(true);
                        button.set(command, groupItem);
                    } catch (NoSuchFieldException | IllegalAccessException e) {}
                }
            } else {
                add((JComponent) command.getButton());
                add(Box.createHorizontalStrut(5));
            }
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
