package codex.launcher;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.model.Entity;
import codex.presentation.SelectorPresentation;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

/**
 * Реализация команды создания новой секции ярлыков.
 */
class CreateSection extends EntityCommand<Entity> {

    /**
     * Конструктор команды.
     */
    CreateSection() {
        super(
                "new_section", null,
                ImageUtils.getByPath("/images/createdir.png"), 
                Language.get("title"),
                null
        );
        activator = entities -> new CommandStatus(true);
    }
    
    /**
     * Вызывается для связывания виджета созданной секции с панелью модуля.
     */
    void boundView(ShortcutSection section) {
        // Do nothing
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        Entity newEntity = Entity.newInstance(ShortcutSection.class, null, null);

        DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
        DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();

        newEntity.getEditorPage().setBorder(new CompoundBorder(
                new EmptyBorder(10, 5, 5, 5),
                new TitledBorder(
                        new LineBorder(Color.LIGHT_GRAY, 1),
                        Language.get(SelectorPresentation.class, "creator@desc")
                )
        ));
        
        Dialog editor = new Dialog(
            FocusManager.getCurrentManager().getActiveWindow(),
            ImageUtils.getByPath("/images/createdir.png"),
            Language.get("title"), newEntity.getEditorPage(),
            (event) -> {
                if (event.getID() == Dialog.OK) {
                    try {
                        newEntity.model.commit(true);
                        boundView((ShortcutSection) newEntity);
                    } catch (Exception e) {}
                }
            },
            confirmBtn, declineBtn
        ) {
            {
                // Перекрытие обработчика кнопок
                Function<DialogButton, ActionListener> defaultHandler = handler;
                handler = (button) -> {
                    return (event) -> {
                        if (event.getID() != Dialog.OK || newEntity.getInvalidProperties().isEmpty()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    };
                };
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(650, super.getPreferredSize().height);
            }
        };
        editor.setResizable(false);
        editor.setVisible(true);
    }
    
}
