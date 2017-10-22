package codex.editor;

import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import java.awt.Color;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;

/**
 * Редактор свойств типа {@link Bool}, представляет собой флажек.
 */
public class BoolEditor extends AbstractEditor {
    
    private JCheckBox checkBox;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public BoolEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        checkBox = new JCheckBox();
        checkBox.setFocusPainted(false);
        checkBox.setIcon(ImageUtils.resize(ImageUtils.getByPath("/images/unchecked.png"), 20, 20));
        checkBox.setSelectedIcon(ImageUtils.resize(ImageUtils.getByPath("/images/checked.png"), 20, 20));
        checkBox.setOpaque(true);
        checkBox.setBackground(Color.WHITE);
        checkBox.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        Box container = new Box(BoxLayout.X_AXIS);
        container.add(checkBox);
        
        checkBox.addChangeListener((ChangeEvent e) -> {
            setBorder(checkBox.getModel().isRollover() ? BORDER_ACTIVE : BORDER_NORMAL);
        });
        
        return container;
    }

    @Override
    public void setValue(Object value) {
        checkBox.setSelected(value != null || ((Boolean) value));
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        checkBox.setEnabled(editable);
        checkBox.setOpaque(editable);
    }
    
}