package codex.mask;

import codex.command.ValueProvider;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import java.util.List;

/**
 * Выбор значений списка из результата запроса к внешнему поставщику данных.
 * Значение дожно быть предоставлено в формате строкового представления значения 
 * {@link ArrStr}:
 * <pre>
 *   {size}[{item#1 length}]{item#1}[{item#2 length}]{item#2}...
 * </pre>
 */
public class DataSetMask extends ValueProvider implements IArrMask {
    
    private final String format;
    
    /**
     * Конструктор маски.
     * @param format Формат отображения значений в GUI.
     * @param dataSupplier Реализация внешнего поставщика данных.
     */
    public DataSetMask(String format, IDataSupplier<String> dataSupplier) {
        super(dataSupplier);
        this.format = format;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public boolean verify(List<String> value) {
        return true;
    }
    
}
