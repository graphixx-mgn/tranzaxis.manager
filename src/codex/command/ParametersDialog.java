package codex.command;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import codex.provider.IDataSupplier;

/**
 * Реализация поставщика данных - параметров команды. Представляет собой диалог
 * с редакторами свойств, возвращающий данные в команду при закрытии.
 */
public class ParametersDialog implements IDataSupplier<Map<String, IComplexType>> {
    
    private final Semaphore  lock = new Semaphore(1);
    private final Dialog     dialog;
    private final ParamModel paramModel = new ParamModel();
    
    private Map<String, IComplexType> data;

    /**
     * Конструктор поставшика.
     * @param command Ссылка на команду.
     * @param paramProps Поставщик, подготавливающий массив свойств для редактора.
     */
    public ParametersDialog(ICommand<Entity> command, Supplier<PropertyHolder[]> paramProps) {
        dialog = new Dialog(
                SwingUtilities.getWindowAncestor((JComponent)command.getButton()), 
                ImageUtils.getByPath("/images/param.png"), 
                Language.get(EntityCommand.class.getSimpleName(), "params@title"), 
                new JPanel(), 
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        if (event.getID() == Dialog.OK) {
                            ParametersDialog.this.data = paramModel.getParameters();
                        }
                        ParametersDialog.this.lock.release();
                    }
                }, 
                Dialog.Default.BTN_OK,
                Dialog.Default.BTN_CANCEL
        ) {{
            // Перекрытие обработчика кнопок
            Function<DialogButton, AbstractAction> defaultHandler = handler;
            handler = (button) -> {
                return new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        if (button.getID() != Dialog.OK || paramModel.isValid()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    }
                }; 
            };}
        
            @Override
            public void setVisible(boolean visible) {
                try {
                    PropertyHolder[] propHolders = paramProps.get();
                    if (propHolders.length != 0) {
                        lock.acquire();
                        for (PropertyHolder propHolder : propHolders) {
                            paramModel.addProperty(propHolder);
                        }
                        dialog.setContent(new EditorPage(paramModel));
                        dialog.setPreferredSize(new Dimension(550, dialog.getPreferredSize().height));
                        super.setVisible(visible);
                    } else {
                        ParametersDialog.this.data = new LinkedHashMap<>();
                    }
                } catch (InterruptedException e) {}
            }
        };
    }

    @Override
    public Map<String, IComplexType> call() throws Exception {
        dialog.setVisible(true);
        lock.acquire();
        return data;
    }
    
}
