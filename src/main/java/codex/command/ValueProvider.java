package codex.command;

import codex.property.PropertyHolder;
import codex.supplier.DataSelector;
import codex.type.IComplexType;
import codex.utils.ImageUtils;

/**
 * Команда редактора свойства, позволяющая выбрать значение из внешнего поставщика.
 */
public class ValueProvider<V> extends EditorCommand {

    private final DataSelector<?, V> selector;
    
    /**
     * Конструктор команды.
     * @param selector Ссылка селектор данных возвращающего строковые значения.
     * Строковое значение затем транслируется с использованием метода 
     * {@link IComplexType#valueOf(java.lang.String)}. 
     */
    public ValueProvider(DataSelector<?, V> selector) {
        super(
            ImageUtils.resize(ImageUtils.getByPath("/images/selector.png"), 18, 18),
            null,
            (holder) -> selector.getSupplier().ready()
        );
        this.selector = selector;
    }

    @Override
    public void execute(PropertyHolder context) {
        V value = selector.select();
        if (value != null) {
            context.setValue(value);
        }
    }
    
}
