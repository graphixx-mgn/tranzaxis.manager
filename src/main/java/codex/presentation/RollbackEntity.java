package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import javax.swing.KeyStroke;

/**
 * Команда отката изменений сущности.
 */
class RollbackEntity extends EntityCommand<Entity> {
    
    /**
     * Конструктор команды.
     */
    RollbackEntity() {
        super(
                "rollback", null,
                ImageUtils.resize(ImageUtils.getByPath("/images/undo.png"), 28, 28), 
                Language.get(EditorPresentation.class, "command@rollback"),
                (entity) -> entity.model.hasChanges(),
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK)
        );
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        context.stopEditing();
        context.model.rollback();
    }

}
