package codex.component.render;

import codex.presentation.SelectorTableModel;
import java.awt.Dimension;

/**
 * Интерфейс рендерера ячеек {@link SelectorTableModel}.
 * @param <T> Тип внетреннего значения свойста сущности.
 */
interface ICellRenderer<T> {
    
    /**
     * Установить значение рендерера перед отрисовкой.
     */
    void setValue(T value, String placeholder);
    
    Dimension getPreferredSize();
    
}
