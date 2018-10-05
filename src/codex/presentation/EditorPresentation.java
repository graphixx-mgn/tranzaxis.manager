package codex.presentation;

import codex.command.EntityCommand;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import java.awt.BorderLayout;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Презентация редактора сущности. Реализует как функциональность редактирования
 * непосредственно свойств сущности при помощи редакторов в составе страницы
 * редактора, так и обеспечивает работу команд сущностей.
 */
public final class EditorPresentation extends JPanel {
    
    private final static EntityCommand CMD_COMMIT   = new CommitEntity();
    private final static EntityCommand CMD_ROLLBACK = new RollbackEntity();
 
    private final CommandPanel        commandPanel = new CommandPanel();
    private final List<EntityCommand> commands = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());
        if (!entity.model.getProperties(Access.Edit).isEmpty()) {
            commands.add(CMD_COMMIT);
            commands.add(CMD_ROLLBACK);
            commandPanel.addCommands(commands.toArray(new EntityCommand[]{}));
        }
        List<EntityCommand> entityCommands = entity.getCommands();
        if (!entityCommands.isEmpty()) {
            if (!commands.isEmpty()) commandPanel.addSeparator();
            commands.addAll(entityCommands);
            commandPanel.addCommands(entityCommands.toArray(new EntityCommand[]{}));
        }
        commands.forEach((command) -> {
            command.setContext(entity);
        });
        if (!entity.model.getProperties(Access.Edit).isEmpty() || !commands.isEmpty()) {
            commandPanel.setVisible(!commands.isEmpty());
            add(commandPanel, BorderLayout.NORTH);
            
            entity.addNodeListener(new INodeListener() {
                
                @Override
                public void childChanged(INode node) {
                    Entity changed = (Entity) node;
                    changed.model.getProperties(Access.Edit).stream().filter((propName) -> {
                        return !changed.model.isPropertyDynamic(propName);
                    }).forEach((propName) -> {
                        ((AbstractEditor) changed.model.getEditor(propName)).setLocked(changed.islocked());
                    });
                    activateCommands();
                }
            });
            add(new EditorPage(entity.model), BorderLayout.CENTER);
        } else {
            setVisible(false);
        }
    }
    
    /**
     * Актуализация состояния доступности команд.
     */
    public void activateCommands() {
        commands.forEach((command) -> {
            command.activate();
        });
    }
    
}
