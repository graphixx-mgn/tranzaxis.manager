package codex.presentation;

import codex.command.CommandButton;
import codex.command.EntityCommand;
import codex.command.IGroupCommandButton;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public CommandPanel(List<EntityCommand> systemCommands) {
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

    void setContextCommands(List<EntityCommand> commands) {
        setCommands(contextCommandsPanel, commands);
        separator.setVisible(systemCommandsPanel.getComponentCount() > 0 && !commands.isEmpty());
    }

    private void setCommands(Box commandPanel, List<EntityCommand> commands) {
        while (commandPanel.getComponentCount() > 0) {
            Component comp = commandPanel.getComponent(0);
            commandPanel.remove(comp);
        }

        Map<Class<? extends EntityCommand>, List<EntityCommand>> map = commands.stream()
                .collect(Collectors.groupingBy(command -> command.getClass().getAnnotation(EntityCommand.Definition.class).parentCommand()));

        if (map.containsKey(EntityCommand.class)) {
            map.get(EntityCommand.class).forEach(command -> {
                if (map.containsKey(command.getClass())) {
                    IGroupCommandButton groupButton = command.groupButtonFactory().newInstance(command);
                    commandPanel.add((JComponent) groupButton);
                    map.get(command.getClass()).forEach(groupButton::addChildCommand);
                } else {
                    CommandButton button = new CommandButton(command);
                    commandPanel.add(button);
                }
                commandPanel.add(Box.createRigidArea(new Dimension(5, 0)));
            });
        }
        commandPanel.revalidate();
        commandPanel.repaint();
    }

}
