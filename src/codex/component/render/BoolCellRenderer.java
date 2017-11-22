package codex.component.render;

import codex.utils.ImageUtils;
import java.awt.Color;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Реализация рендерера ячеек {@link SelectorTableModel} для типа {@link Bool} в
 * виде флажка в ячейке.
 */
class BoolCellRenderer extends CellRenderer<Boolean> {
    
    private final static BoolCellRenderer INSTANCE = new BoolCellRenderer();
    
    public final static BoolCellRenderer getInstance() {
        return INSTANCE;
    }
        
    private final JCheckBox checkBox = new JCheckBox();

    private BoolCellRenderer() {
        super(BoxLayout.PAGE_AXIS);
        Box container = new Box(BoxLayout.X_AXIS);
        container.setBorder(new LineBorder(Color.GRAY, 1));

        checkBox.setOpaque(true);
        checkBox.setBackground(Color.WHITE);
        checkBox.setBorder(new EmptyBorder(0, 1, 1, 1));
        checkBox.setIcon(ImageUtils.resize(ImageUtils.getByPath("/images/unchecked.png"), 19, 19));
        checkBox.setSelectedIcon(ImageUtils.resize(ImageUtils.getByPath("/images/checked.png"), 19, 19));

        add(container);
        container.add(checkBox);
    }

    @Override
    public void setValue(Boolean value) {
        checkBox.setSelected(value != null && value);
    }

}
