package codex.component.render;

import codex.editor.IEditor;
import codex.presentation.SelectorTableModel;
import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.LayoutManager;
import javax.swing.Box;
import javax.swing.ImageIcon;
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
    
    private boolean disabled = false;
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (getIcon() != null && disabled) {
            setIcon(ImageUtils.grayscale((ImageIcon) getIcon()));
        }
        setBackground(disabled ? Color.decode("#E5E5E5") : new Color(0, 0, 0, 0));
        setForeground(disabled ? IEditor.COLOR_DISABLED : IEditor.COLOR_NORMAL);
    }

    public boolean isDisabled() {
        return this.disabled;
    }
}
