package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.KeyStroke;

/**
 * Команда отката изменений сущности.
 */
public class RollbackEntity extends EntityCommand {
    
    /**
     * Конструктор команды.
     */
    public RollbackEntity() {
        super(
                "rollback", null,
                ImageUtils.resize(ImageUtils.getByPath("/images/undo.png"), 28, 28), 
                Language.get(EditorPresentation.class.getSimpleName(), "command@rollback"),
                (entity) -> {
                    return entity.model.hasChanges();
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK)
        );
    }

    @Override
    public void execute(Entity context) {
        context.stopEditing();
        context.model.rollback();
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        activator.accept(getContext());
    }

}
