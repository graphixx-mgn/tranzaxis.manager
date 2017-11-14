package codex.presentation;

import codex.command.EntityCommand;
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
 
    private final CommandPanel        commandPanel = new CommandPanel();
    private final List<EntityCommand> commands = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());
        
        if (!entity.model.getProperties(Access.Edit).isEmpty()) {
            commands.add(new CommitEntity());
            commands.add(new RollbackEntity());
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
        commandPanel.setVisible(!commands.isEmpty());  
        add(commandPanel, BorderLayout.NORTH);
        add(new EditorPage(entity, EditorPage.Mode.Edit), BorderLayout.CENTER);
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
