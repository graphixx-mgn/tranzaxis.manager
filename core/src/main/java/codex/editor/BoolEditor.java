package codex.editor;

import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link Bool}, представляет собой флажек.
 */
@ThreadSafe
public class BoolEditor extends AbstractEditor<Bool, Boolean> implements ItemListener {
    
    private JCheckBox checkBox;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public BoolEditor(PropertyHolder<Bool, Boolean> propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        checkBox = new JCheckBox();
        checkBox.setFocusPainted(false);
        checkBox.setIcon(ImageUtils.resize(ImageUtils.getByPath("/images/unchecked.png"), 19, 19));
        checkBox.setSelectedIcon(ImageUtils.resize(ImageUtils.getByPath("/images/checked.png"), 19, 19));
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
    protected void updateEditable(boolean editable) {
        checkBox.setEnabled(editable);
        checkBox.setOpaque(editable);
    }

    @Override
    protected void updateValue(Boolean value) {
        checkBox.setSelected(value != null && value);
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Boolean newValue = e.getStateChange() == ItemEvent.SELECTED;
        if (!newValue.equals(propHolder.getPropValue().getValue())) {
            propHolder.setValue(e.getStateChange() == ItemEvent.SELECTED);
        }
    }
    
    @Override
    public Component getFocusTarget() {
        return checkBox;
    }
}