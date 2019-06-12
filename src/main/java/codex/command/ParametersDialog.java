package codex.command;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.supplier.IDataSupplier;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.FocusManager;
import javax.swing.JPanel;

/**
 * Реализация поставщика данных - параметров команды. Представляет собой диалог
 * с редакторами свойств, возвращающий данные в команду при закрытии.
 */
public class ParametersDialog implements IDataSupplier<Map<String, IComplexType>> {
    
    private final Dialog     dialog;
    private final ParamModel paramModel = new ParamModel();
    
    private Map<String, IComplexType> data;

    /**
     * Конструктор поставшика.
     * @param command Ссылка на команду.
     * @param paramProps Поставщик, подготавливающий массив свойств для редактора.
     */
    ParametersDialog(EntityCommand command, Supplier<PropertyHolder[]> paramProps) {
        dialog = new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EntityCommand.class, "params@title"),
                new JPanel(),
                (event) -> {
                    if (event.getID() == Dialog.OK) {
                        ParametersDialog.this.data = paramModel.getParameters();
                    }
                },
                Dialog.Default.BTN_OK,
                Dialog.Default.BTN_CANCEL
        ) {{
            // Перекрытие обработчика кнопок
            Function<DialogButton, ActionListener> defaultHandler = handler;
            handler = (button) -> (event) -> {
                if (event.getID() != Dialog.OK || paramModel.isValid()) {
                    defaultHandler.apply(button).actionPerformed(event);
                }
            };
        }

            @Override
            public Dimension getPreferredSize() {
                Dimension prefSize = super.getPreferredSize();
                if (Arrays.stream(paramProps.get())
                        .anyMatch((propHolder) -> !Bool.class.isAssignableFrom(propHolder.getPropValue().getClass()))
                ) {
                    return new Dimension(650, prefSize.getSize().height);
                } else {
                    return prefSize;
                }
            }

            @Override
            public void setVisible(boolean visible) {
                if (visible) {
                    PropertyHolder[] propHolders = paramProps.get();
                    for (PropertyHolder propHolder : propHolders) {
                        paramModel.addProperty(propHolder);
                    }
                    command.preprocessParameters(paramModel);
                    dialog.setContent(new JPanel(new BorderLayout()){{
                        add(new EditorPage(paramModel), BorderLayout.CENTER);
                    }});
                    dialog.pack();
                }
                super.setVisible(visible);
            }
        };
    }

    /**
     * Делает запрос к поставщику и возвращает параметры в виде карты имя-значение.
     */
    @Override
    public Map<String, IComplexType> call() throws Exception {
        dialog.setVisible(true);
        return new LinkedHashMap<>(data);
    }
    
}
