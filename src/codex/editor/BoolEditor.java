package codex.editor;

import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link Bool}, представляет собой флажек.
 */
public class BoolEditor extends AbstractEditor implements ItemListener {
    
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
        checkBox.addFocusListener(this);
        checkBox.addItemListener(this);
        
        Box container = new Box(BoxLayout.X_AXIS);
        container.add(checkBox);
        return container;
    }

    @Override
    public void setValue(Object value) {
        checkBox.setSelected(value != null && ((Boolean) value));
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        checkBox.setEnabled(editable);
        checkBox.setOpaque(editable);
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (!propHolder.getPropValue().getValue().equals(e.getStateChange() == ItemEvent.SELECTED)) {
            propHolder.setValue(e.getStateChange() == ItemEvent.SELECTED);
        }
    }
    
    @Override
    public Component getFocusTarget() {
        return checkBox;
    }
}