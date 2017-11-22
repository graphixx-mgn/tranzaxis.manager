package codex.component.render;

import javax.swing.Box;

/**
 * Абстрактная реализация рендерера ячеек {@link SelectorTableModel}. Суть его
 * назначения - наследуемые классы являются экземплярами {@link Box}.
 * @param <T> Тип внетреннего значения свойста сущности.
 */
abstract class CellRenderer<T> extends Box implements ICellRenderer<T> {
    
    public CellRenderer(int axis) {
        super(axis);
        setOpaque(true);
    }

}
