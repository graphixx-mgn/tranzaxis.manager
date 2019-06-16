package codex.presentation;

import codex.command.EntityCommand;
import codex.model.Access;
import codex.model.Entity;
import codex.model.OverrideProperty;
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
                ImageUtils.getByPath("/images/undo.png"),
                Language.get(EditorPresentation.class, "command@rollback"),
                (entity) -> entity.model.getChanges().stream()
                        .anyMatch(propName -> (
                                !entity.model.isPropertyExtra(propName) &&
                                !"OVR".equals(propName) &&
                                entity.model.getProperties(Access.Edit).contains(propName)
                            ) || (
                                "OVR".equals(propName) &&
                                OverrideProperty.getOverrideChanges(entity.model).entrySet().stream()
                                        .anyMatch(entry ->
                                            entry.getValue() &&
                                            !entity.model.isPropertyExtra(entry.getKey())
                                        )
                            )
                        ),
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK)
        );
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        context.stopEditing();
        context.model.rollback();
    }

}
