package codex.mask;

import codex.command.ICommand;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import java.util.List;

/**
 * Базовый интерфейс маски поля типа {@link ArrStr}. Также реализует интерфейс 
 * команды редактора. При назначении маски стандартный диалог редактора поля 
 * заменяется на предоставляемый конкретной реализацией маски.
 */
public interface IArrMask extends IMask<List<String>>, ICommand<PropertyHolder, PropertyHolder> {
    
    /**
     * Формат отображения элементов списка в редакторе и селекторе. Указывается 
     * произвольная строка с точками вставки элементов списка в виде '{n}', где
     * n - индекс значения в списке. Если значений больше чем мест вставки - 
     * лишние значени не будут отображаться.
     */
    default String getFormat() {
        return null;
    }
    
    /**
     * Возвращает пустое значение с точки зрения маски. Поскольку маска {@link ArrStr}
     * является и редактором, пустое значени может интерпретироваться по-разному.
     */
    default List<String> getCleanValue() {
        return null;
    }
    
}
