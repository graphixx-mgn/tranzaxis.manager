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
 * Команда сохранения изменений сущности.
 */
class CommitEntity extends EntityCommand<Entity> {

    /**
     * Конструктор команды.
     */
    CommitEntity() {
        super(
                "commit", null,
                ImageUtils.resize(ImageUtils.getByPath("/images/save.png"), 28, 28), 
                Language.get(EditorPresentation.class.getSimpleName(), "command@commit"),
                (entity) -> entity.model.hasChanges(),
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK)
        );
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        if (context.validate()) {
            try {
                context.model.commit(true);
            } catch (Exception e) {}
        }
    }
    
}
