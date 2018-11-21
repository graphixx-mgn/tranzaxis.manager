package codex.component.render;

import codex.presentation.SelectorTableModel;
import codex.type.Bool;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
        setOpaque(true);
        setBorderPainted(false);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        setBackground(Color.WHITE);
        setIcon(ImageUtils.resize(ImageUtils.getByPath("/images/unchecked.png"), 19, 19));
        setSelectedIcon(ImageUtils.resize(ImageUtils.getByPath("/images/checked.png"), 19, 19));
    }};

    private BoolCellRenderer() {
        super();
        setOpaque(true);
        
        Box wrapper2 = new Box(BoxLayout.X_AXIS);
        wrapper2.setBorder(new CompoundBorder(
            new EmptyBorder(1, 1, 1, 1),
            new LineBorder(Color.LIGHT_GRAY, 1)
        ));
        wrapper2.add(checkBox);
        
        JPanel wrapper1 = new JPanel();
        wrapper1.setOpaque(false);
        wrapper1.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 2));
        wrapper1.add(wrapper2);
        add(wrapper1);
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
