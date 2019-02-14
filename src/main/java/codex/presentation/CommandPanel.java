package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.GroupButton;
import codex.component.button.GroupItemButton;
import codex.model.Entity;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Панель команд презентаций редактора и селектора сущности.
 */
public final class CommandPanel extends Box {

    private final JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
    private final Box systemCommandsPanel  = new Box(BoxLayout.X_AXIS);
    private final Box contextCommandsPanel = new Box(BoxLayout.X_AXIS);
    
    /**
     * Конструктор панели.
     */
    public CommandPanel(List<EntityCommand<Entity>> systemCommands) {
        super(BoxLayout.LINE_AXIS);
        setBorder(new CompoundBorder(
                new EmptyBorder(2, 5, 2, 5),
                new CompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Color.GRAY), 
                        new EmptyBorder(3, 0, 3, 0)
                )
        ));
        separator.setMaximumSize( new Dimension(1, Integer.MAX_VALUE) );
        separator.setVisible(false);

        add(systemCommandsPanel);
        add(separator);
        add(Box.createRigidArea(new Dimension(5, 0)));
        add(contextCommandsPanel);

        setCommands(systemCommandsPanel, systemCommands);
    }

    void setContextCommands(List<EntityCommand<Entity>> commands) {
        setCommands(contextCommandsPanel, commands);
        separator.setVisible(systemCommandsPanel.getComponentCount() > 0 && !commands.isEmpty());
    }

    private void setCommands(Box commandPanel, List<EntityCommand<Entity>> commands) {
        commandPanel.removeAll();
        final Map<String, GroupButton> groupButtons = new HashMap<>();

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
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        //
                    }
                    groupButtons.put(command.getGroupId(), groupButton);
                    commandPanel.add(groupButton);
                    commandPanel.add(Box.createRigidArea(new Dimension(5, 0)));
                } else {
                    GroupItemButton groupItem = groupButtons.get(command.getGroupId()).createGroupItem(command.getIcon(), command.toString());
                    groupItem.addActionListener(command);
                    try {
                        Field button = EntityCommand.class.getDeclaredField("button");
                        button.setAccessible(true);
                        button.set(command, groupItem);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        //
                    }
                }
            } else {
                commandPanel.add((JComponent) command.getButton());
                commandPanel.add(Box.createRigidArea(new Dimension(5, 0)));
            }
        }

        commandPanel.revalidate();
        commandPanel.repaint();
    }

}
