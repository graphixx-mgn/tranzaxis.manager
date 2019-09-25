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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Презентация редактора сущности. Реализует как функциональность редактирования
 * непосредственно свойств сущности при помощи редакторов в составе страницы
 * редактора, так и обеспечивает работу команд сущностей.
 */
public final class EditorPresentation extends JPanel {

    private final Class        entityClass;
    private final CommandPanel commandPanel;
    private final Supplier<? extends Entity> context;
    private final List<EntityCommand> systemCommands  = new LinkedList<>();
    private final List<EntityCommand> contextCommands = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());
        entityClass = entity.getClass();
        context = () -> entity;

        boolean editable = entity.model.getProperties(Access.Edit).stream()
                .anyMatch(propName -> !entity.model.isPropertyDynamic(propName));

        if (editable) {
            systemCommands.add(new CommitEntity());
            systemCommands.add(new RollbackEntity());
        }
        if (entity.model.hasExtraProps()) {
            systemCommands.add(new ShowExtraProps());
        }

        contextCommands.addAll(
                entity.getCommands().stream()
                        .filter(command -> command.getKind() != EntityCommand.Kind.System)
                        .collect(Collectors.toList())
        );

        commandPanel = new CommandPanel(systemCommands);
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

        boolean hasCommands   = Stream.concat(systemCommands.stream(), getContextCommands().stream()).findAny().isPresent();
        boolean hasProperties = !context.get().model.getProperties(Access.Edit).isEmpty();

        commandPanel.setVisible(hasCommands);
        setVisible(hasCommands || hasProperties);

        if (hasCommands) {
            updateCommands();
            activateCommands();
        }
    }

    public Class getEntityClass() {
        return entityClass;
    }

    private List<EntityCommand> getContextCommands() {
        return new LinkedList<>(contextCommands);
    }

    private void updateCommands() {
        commandPanel.setContextCommands(getContextCommands());
    }
    
    /**
     * Актуализация состояния доступности команд.
     */
    private void activateCommands() {
        Stream.concat(
                systemCommands.stream(),
                getContextCommands().stream()
        ).forEach(command -> ((EntityCommand<Entity>) command).setContext(context.get()));
    }
    
}
