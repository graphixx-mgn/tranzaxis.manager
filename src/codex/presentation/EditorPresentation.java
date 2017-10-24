package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Access;
import codex.model.Entity;
import java.awt.BorderLayout;
import java.util.Arrays;
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
    private final List<EntityCommand> commands = new LinkedList<>(Arrays.asList(
                                                        new CommitEntity(), 
                                                        new RollbackEntity()
                                                ));
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());
        
        commandPanel.setVisible(!entity.model.getProperties(Access.Edit).isEmpty());
        commands.forEach((command) -> {
            command.setContext(entity);
        });
        commandPanel.addCommands(commands.toArray(new EntityCommand[]{}));
        add(commandPanel, BorderLayout.NORTH);
        
        add(new EditorPage(entity), BorderLayout.CENTER);
    }
    
}
