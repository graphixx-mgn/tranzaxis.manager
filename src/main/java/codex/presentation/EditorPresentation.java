package codex.presentation;

import codex.command.EntityCommand;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Презентация редактора сущности. Реализует как функциональность редактирования
 * непосредственно свойств сущности при помощи редакторов в составе страницы
 * редактора, так и обеспечивает работу команд сущностей.
 */
public final class EditorPresentation extends JPanel {

    private final CommandPanel commandPanel;
    private final List<EntityCommand<Entity>> systemCommands  = new LinkedList<>();
    private final List<EntityCommand<Entity>> contextCommands = new LinkedList<>();
    private final Supplier<Stream<EntityCommand<Entity>>> commands = () -> Stream.concat(systemCommands.stream(), contextCommands.stream());
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());

        boolean editable = entity.model.getProperties(Access.Edit).stream()
                .anyMatch(propName -> !entity.model.isPropertyDynamic(propName));

        if (editable) {
            systemCommands.add(new CommitEntity());
            systemCommands.add(new RollbackEntity());
        }
        contextCommands.addAll(entity.getCommands());

        commandPanel = new CommandPanel(systemCommands.toArray(new EntityCommand[]{}));
        commandPanel.setContextCommands(contextCommands.toArray(new EntityCommand[]{}));

        commands.get().forEach(command -> command.setContext(entity));
        activateCommands();

        boolean hasCommands = commands.get().findFirst().isPresent();

        if (!entity.model.getProperties(Access.Edit).isEmpty() || hasCommands) {
            commandPanel.setVisible(hasCommands);
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
            add(entity.getEditorPage(), BorderLayout.CENTER);
        } else {
            setVisible(false);
        }
    }

    public final void updateCommands() {
        commandPanel.setContextCommands(contextCommands.toArray(new EntityCommand[]{}));
        activateCommands();
    }
    
    /**
     * Актуализация состояния доступности команд.
     */
    private void activateCommands() {
        commands.get().forEach(EntityCommand::activate);
    }
    
}
