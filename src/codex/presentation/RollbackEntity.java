package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

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
                }
        );
    }

    @Override
    public void execute(Entity context) {
        context.stopEditing();
        context.model.rollback();
    }
    
    @Override
    public void modelSaved() {
        activator.accept(getContext());
    }

}
