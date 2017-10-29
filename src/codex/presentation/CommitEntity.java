package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;

/**
 * Команда сохранения изменений сущности.
 */
public class CommitEntity extends EntityCommand {

    /**
     * Конструктор команды.
     */
    public CommitEntity() {
        super(
                "commit", null,
                ImageUtils.resize(ImageUtils.getByPath("/images/save.png"), 28, 28), 
                Language.get(EditorPresentation.class.getSimpleName(), "command@commit"),
                (entity) -> {
                    return entity.model.hasChanges();
                }
        );
    }

    @Override
    public void execute(Entity context) {
        if (context.validate()) {
            context.model.commit();
        }
    }
    
    @Override
    public void modelSaved() {
        activator.accept(getContext());
    }

    @Override
    public void modelRestored() {
        activator.accept(getContext());
    }
    
}
