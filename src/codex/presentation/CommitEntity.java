package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import javax.swing.KeyStroke;

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
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK)
        );
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        if (context.validate()) {
            context.model.commit();
        }
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        activator.accept(getContext());
    }

    @Override
    public void modelRestored(EntityModel model, List<String> changes) {
        activator.accept(getContext());
    }
    
}
