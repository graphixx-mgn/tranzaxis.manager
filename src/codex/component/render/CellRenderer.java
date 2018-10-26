package codex.component.render;

import codex.presentation.SelectorTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.LayoutManager;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Абстрактная реализация рендерера ячеек {@link SelectorTableModel}. Суть его
 * назначения - наследуемые классы являются экземплярами {@link Box}.
 * @param <T> Тип внетреннего значения свойста сущности.
 */
abstract class CellRenderer<T> extends JLabel implements ICellRenderer<T> {
    
    final JLabel state = new JLabel();
    
    CellRenderer() {
        super();
        setLayout(new BorderLayout());
        state.setVerticalAlignment(SwingConstants.TOP);
        state.setVerticalTextPosition(SwingConstants.TOP);
        add(state, BorderLayout.EAST);
    }

    @Override
    public final void add(Component comp, Object constraints) {
        super.add(comp, constraints);
    }

    @Override
    public final void setLayout(LayoutManager mgr) {
        super.setLayout(mgr);
    }
}
