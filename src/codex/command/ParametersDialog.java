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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
    public ParametersDialog(EntityCommand command, Supplier<PropertyHolder[]> paramProps) {
        dialog = new Dialog(
                SwingUtilities.getWindowAncestor((JComponent)command.getButton()), 
                ImageUtils.getByPath("/images/param.png"), 
                Language.get(EntityCommand.class.getSimpleName(), "params@title"), 
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
            handler = (button) -> {
                return (event) -> {
                    if (event.getID() != Dialog.OK || paramModel.isValid()) {
                        defaultHandler.apply(button).actionPerformed(event);
                    }
                };
            };
        }
            @Override
            public void setLocationRelativeTo(Component c) {
                super.setLocationRelativeTo(FocusManager.getCurrentManager().getActiveWindow());
            }
            
            @Override
            public void setVisible(boolean visible) {
                PropertyHolder[] propHolders = paramProps.get();
                if (propHolders.length != 0) {
                    for (PropertyHolder propHolder : propHolders) {
                        paramModel.addProperty(propHolder);
                    }
                    command.preprocessParameters(command.getContext()[0], paramModel);
                    dialog.setContent(new EditorPage(paramModel));
                    if (Arrays.asList(paramProps.get()).stream().anyMatch((propHolder) -> {
                        return !Bool.class.isAssignableFrom(propHolder.getPropValue().getClass());
                    })) {
                        dialog.setPreferredSize(new Dimension(550, dialog.getPreferredSize().height));
                    }
                    super.setVisible(visible);
                } else {
                    ParametersDialog.this.data = new LinkedHashMap<>();
                }
            }
        };
    }

    /**
     * Делает запрос к поставщику и возвращает параметры в виде карты имя-значение.
     */
    @Override
    public Map<String, IComplexType> call() throws Exception {
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return data;
    }
    
}
