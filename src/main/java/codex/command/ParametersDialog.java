package codex.command;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.model.Access;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.FocusManager;
import javax.swing.JPanel;

/**
 * Реализация поставщика данных - параметров команды. Представляет собой диалог
 * с редакторами свойств, возвращающий данные в команду при закрытии.
 */
public class ParametersDialog extends Dialog {

    private final EntityCommand command;
    private final ParamModel paramModel = new ParamModel();
    private final Supplier<PropertyHolder[]> paramSupplier;
    private ActionEvent exitEvent;

    /**
     * Конструктор поставшика.
     * @param command Ссылка на команду.
     * @param paramSupplier Поставщик, подготавливающий массив свойств для редактора.
     */
    ParametersDialog(EntityCommand command, Supplier<PropertyHolder[]> paramSupplier) {
        super(
                FocusManager.getCurrentManager().getActiveWindow(),
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EntityCommand.class, "params@title"),
                new JPanel(),
                null,
                Dialog.Default.BTN_OK,
                Dialog.Default.BTN_CANCEL
        );
        this.command = command;
        this.paramSupplier = paramSupplier;
        {
            // Перекрытие обработчика кнопок
            Function<DialogButton, ActionListener> defaultHandler = handler;
            handler = (button) -> (event) -> {
                exitEvent = event;
                if (event.getID() != Dialog.OK || paramModel.isValid()) {
                    defaultHandler.apply(button).actionPerformed(event);
                }
            };
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension prefSize = super.getPreferredSize();
        if (Arrays.stream(paramSupplier.get())
                .anyMatch((propHolder) -> !Bool.class.isAssignableFrom(propHolder.getPropValue().getClass()))
        ) {
            return new Dimension(650, prefSize.getSize().height);
        } else {
            return prefSize;
        }
    }

    public List<PropertyHolder> getProperties() throws Canceled {
        Arrays.asList(paramSupplier.get()).forEach(paramModel::addProperty);
        command.preprocessParameters(paramModel);
        setContent(new EditorPage(paramModel));
        setVisible(true);
        if (exitEvent.getID() == Dialog.OK) {
            return paramModel.getProperties(Access.Edit).stream()
                    .map(paramModel::getProperty)
                    .collect(Collectors.toList());
        } else {
            throw new Canceled();
        }
    }

    public final class Canceled extends Exception {}
}
