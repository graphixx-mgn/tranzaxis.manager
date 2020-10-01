package codex.mask;

import codex.command.ValueProvider;
import codex.supplier.DataSelector;
import codex.type.ArrStr;
import net.jcip.annotations.ThreadSafe;
import java.util.List;

/**
 * Выбор значений списка из результата запроса к внешнему поставщику данных.
 * Значение дожно быть предоставлено в формате строкового представления значения 
 * {@link ArrStr}:
 * <pre>
 *   {size}[{item#1 length}]{item#1}[{item#2 length}]{item#2}...
 * </pre>
 */
@ThreadSafe
public class DataSetMask extends ValueProvider<List<String>> implements IArrMask {
    
    private final String format;

    public DataSetMask(DataSelector<?, List<String>> selector) {
        this(selector, null);
    }

    /**
     * Конструктор маски.
     * @param format Формат отображения значений в GUI.
     * @param selector Реализация селектора из внешнего поставщика данных.
     */
    public DataSetMask(DataSelector<?, List<String>> selector, String format) {
        super(selector);
        this.format = format;
    }

    @Override
    public boolean verify(List<String> value) {
        return true;
    }


    @Override
    public String getFormat() {
        return format;
    }

    
}
