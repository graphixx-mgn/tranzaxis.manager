package codex.command;

import codex.property.PropertyHolder;
import codex.supplier.IDataSupplier;
import codex.type.IComplexType;
import codex.utils.ImageUtils;

/**
 * Команда редактора свойства, позволяющая выбрать значение из внешнего поставщика.
 */
public class ValueProvider extends EditorCommand {

    private final IDataSupplier<String> supplier;
    
    /**
     * Конструктор команды.
     * @param supplier Ссылка на поставшика данных, возвращающего строковые значения.
     * Строковое значение затем транслируется с использованием метода 
     * {@link IComplexType#valueOf(java.lang.String)}. 
     */
    public ValueProvider(IDataSupplier<String> supplier) {
        super(
            ImageUtils.resize(ImageUtils.getByPath("/images/selector.png"), 18, 18), 
            null
        );
        this.supplier = supplier;
    }

    @Override
    public void execute(PropertyHolder context) {
        try {
            String value = supplier.call();
            if (value != null) {
                // Рассчет значения из строкового представления без вызова событий
                Object prevValue = context.getPropValue().getValue();
                context.getPropValue().valueOf(value);
                // Откат значения без вызова событий
                Object nextValue = context.getPropValue().getValue();
                context.getPropValue().setValue(prevValue);
                // Установка значения с вызовом событий
                context.setValue(nextValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
