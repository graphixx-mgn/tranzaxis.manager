package codex.presentation;

import codex.command.EntityCommand;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import codex.model.PolyMorph;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Презентация редактора сущности. Реализует как функциональность редактирования
 * непосредственно свойств сущности при помощи редакторов в составе страницы
 * редактора, так и обеспечивает работу команд сущностей.
 */
public final class EditorPresentation extends JPanel {

    private final Class        entityClass;
    private final CommandPanel commandPanel;
    private final Supplier<? extends Entity> context;
    private final List<EntityCommand<Entity>> systemCommands  = new LinkedList<>();
    private final List<EntityCommand<Entity>> contextCommands = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());
        entityClass = entity.getClass();
        context = () -> entity;

        commandPanel = new CommandPanel(Collections.emptyList());
        add(commandPanel, BorderLayout.NORTH);

        entity.addNodeListener(new INodeListener() {
            @Override
            public void childChanged(INode node) {
                Entity changed = (Entity) node;
                changed.model.getProperties(Access.Edit).stream()
                        .filter((propName) -> !changed.model.isPropertyDynamic(propName))
                        .forEach((propName) -> ((AbstractEditor) changed.model.getEditor(propName)).setLocked(changed.islocked()));
                activateCommands();
            }
        });
    }

    /**
     * Обновление презентации и панели команд.
     */
    public final void refresh() {
        add(context.get().getEditorPage(), BorderLayout.CENTER);
        updateCommands();
        activateCommands();
    }

    public Class getEntityClass() {
        return entityClass;
    }

    private void updateCommands() {
        systemCommands.clear();
        systemCommands.addAll(getSystemCommands());
        commandPanel.setSystemCommands(systemCommands);

        contextCommands.clear();
        contextCommands.addAll(getContextCommands().stream()
                .filter(command -> command.getKind() != EntityCommand.Kind.System)
                .collect(Collectors.toList())
        );
        commandPanel.setContextCommands(contextCommands);
    }
    
    /**
     * Актуализация состояния доступности команд.
     */
    private void activateCommands() {
        systemCommands.forEach(sysCommand -> sysCommand.setContext(context.get()));
        contextCommands.forEach(command -> {
            command.setContext(context.get());
        });
    }

    private List<EntityCommand<Entity>> getSystemCommands() {
        final List<EntityCommand<Entity>> commands = new LinkedList<>();
        if (isEditable()) {
            final EntityCommand<Entity> commitCmd = findCommand(systemCommands, CommitEntity.class, new CommitEntity());
            commands.add(commitCmd);

            final EntityCommand<Entity> rollbackCmd = findCommand(systemCommands, RollbackEntity.class, new RollbackEntity());
            commands.add(rollbackCmd);
        }
        if (context.get().model.hasExtraProps()) {
            final EntityCommand<Entity> showExtraCmd = findCommand(systemCommands, ShowExtraProps.class, new ShowExtraProps());
            commands.add(showExtraCmd);
        }
        getContextCommands().stream()
                .filter(command -> command.getKind() == EntityCommand.Kind.System)
                .forEach(commands::add);
        return commands;
    }

    private List<EntityCommand<Entity>> getContextCommands() {
        return context.get().getCommands();
    }

    private EntityCommand<Entity> findCommand(
            Collection<EntityCommand<Entity>> commands,
            Class<? extends EntityCommand<Entity>> commandClass,
            EntityCommand<Entity> defCommand
    ) {
        return commands.stream().filter(command -> command.getClass().equals(commandClass)).findFirst().orElse(defCommand);
    }

    private boolean isEditable() {
        return context.get().model.getProperties(Access.Edit).stream()
               .anyMatch(propName -> !context.get().model.isPropertyDynamic(propName));
    }
}
