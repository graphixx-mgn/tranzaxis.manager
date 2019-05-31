package codex.presentation;

import codex.command.EntityCommand;
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
 * Команда сохранения изменений сущности.
 */
class CommitEntity extends EntityCommand<Entity> {

    /**
     * Конструктор команды.
     */
    CommitEntity() {
        super(
                "commit", null,
                ImageUtils.getByPath("/images/save.png"),
                Language.get(EditorPresentation.class, "command@commit"),
                (entity) -> entity.model.getChanges().stream()
                        .anyMatch(propName -> (
                                !entity.model.isPropertyExtra(propName) &&
                                !"OVR".equals(propName)
                            ) || (
                                "OVR".equals(propName) &&
                                OverrideProperty.getOverrideChanges(entity.model).entrySet().stream()
                                        .anyMatch(entry ->
                                            entry.getValue() &&
                                            !entity.model.isPropertyExtra(entry.getKey())
                                        )
                            )
                        ),
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK)
        );
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        if (context.validate()) {
            try {
                context.model.commit(true);
            } catch (Exception e) {
                //
            }
        }
    }
    
}
