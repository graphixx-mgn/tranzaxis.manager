package codex.component.render;

import codex.presentation.SelectorTableModel;
import codex.type.Bool;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Реализация рендерера ячеек {@link SelectorTableModel} для типа {@link Bool} в
 * виде флажка в ячейке.
 */
final class BoolCellRenderer extends CellRenderer<Boolean> {
    
    private final static BoolCellRenderer INSTANCE = new BoolCellRenderer();
    
    public final static BoolCellRenderer getInstance() {
        return INSTANCE;
    }
        
    final JCheckBox checkBox = new JCheckBox() {{
        setOpaque(false);
        setBorderPainted(true);
        setBorder(new CompoundBorder(
                new LineBorder(Color.GRAY, 1),
                new EmptyBorder(1, 1, 1, 1)
        ));
        setIcon(ImageUtils.resize(ImageUtils.getByPath("/images/unchecked.png"), 19, 19));
        setSelectedIcon(ImageUtils.resize(ImageUtils.getByPath("/images/checked.png"), 19, 19));
    }};

    private BoolCellRenderer() {
        super();
        setOpaque(true);
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 2));
        container.add(checkBox);
        add(container);
    }

    @Override
    public void setValue(Boolean value, String placeholder) {
        checkBox.setSelected(Boolean.TRUE.equals(value));
    }

    @Override
    public Dimension getPreferredSize() {
        return checkBox.getPreferredSize();
    }

}
