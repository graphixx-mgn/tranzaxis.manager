package codex.command;

import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.supplier.DataSelector;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;

/**
 * Команда редактора свойства, позволяющая выбрать значение из внешнего поставщика.
 */
@ThreadSafe
public class ValueProvider<V> extends EditorCommand<IComplexType<V, IMask<V>>, V> {

    private final DataSelector<?, V> selector;
    
    /**
     * Конструктор команды.
     * @param selector Ссылка селектор данных возвращающего строковые значения.
     * Строковое значение затем транслируется с использованием метода 
     * {@link IComplexType#valueOf(java.lang.String)}. 
     */
    public ValueProvider(DataSelector<?, V> selector) {
        super(
            ImageUtils.getByPath("/images/selector.png"),
            null,
            (holder) -> selector.getSupplier().ready()
        );
        this.selector = selector;
    }

    public void setValue(V value) {
        if (value != null) {
            context.setValue(value);
        }
    }

    @Override
    public void execute(PropertyHolder<IComplexType<V, IMask<V>>, V> context) {
        setValue(selector.select(context.getPropValue().getValue()));
    }

    @Override
    public Direction commandDirection() {
        return Direction.Supplier;
    }
}
