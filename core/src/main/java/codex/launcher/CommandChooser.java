package codex.launcher;

import codex.command.EntityCommand;
import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.editor.AbstractEditor;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Редактор ссылки на команду сущности.
 */
class CommandChooser extends AbstractEditor implements ActionListener {
    
    private JComboBox<Object> comboBox;

    /**
     * Конструктор редактора.
     * @param propHolder Свойство для хранения имени команды.
     */
    CommandChooser(PropertyHolder<AnyType, Object> propHolder) {
        super(propHolder);
    }
    
    /**
     * Обновление ссылки на сущность с пересчетом допустимых значений редактора.
     */
    public final void setEntity(Entity entity) {
        comboBox.removeActionListener(this);
        comboBox.removeAllItems();
        comboBox.addItem(new Undefined());
        if (entity != null) {
            entity.getCommands().stream()
                .filter((command) -> (
                    command.getKind() == EntityCommand.Kind.Action
                )).forEachOrdered((command) -> {
                    comboBox.addItem(command);
                });
        }
        comboBox.addActionListener(this);
        comboBox.setSelectedIndex(0);
    }

    @Override
    public Box createEditor() {
        comboBox = new JComboBox<Object>() {
            @Override
            public Color getForeground() {
                return getValue() == null ? Color.GRAY : Color.BLACK;
            }
        };
        comboBox.addItem(new Undefined());
        
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ("Panel.background"), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);
        
        comboBox.setFont(FONT_VALUE);
        comboBox.setRenderer(new GeneralRenderer<>());
        comboBox.addFocusListener(this);
        comboBox.addActionListener(this);
        
        Object child = comboBox.getAccessibleContext().getAccessibleChild(0);
        BasicComboPopup popup = (BasicComboPopup)child;
        popup.setBorder(IButton.PRESS_BORDER);
        
        Box container = new Box(BoxLayout.X_AXIS);
        container.add(comboBox);
        return container;
    }

    @Override
    public void setValue(Object value) {
        if (value == null) {
            comboBox.setSelectedIndex(0);
        } else {
            if (!comboBox.getSelectedItem().equals(value)) {
                comboBox.setSelectedItem(value);
            }
        }
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        comboBox.setEnabled(editable);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (comboBox.getSelectedIndex() == 0) {
            propHolder.setValue(null);
        } else {
            if (!comboBox.getSelectedItem().equals(propHolder.getPropValue().getValue())) {
                propHolder.setValue(((EntityCommand) comboBox.getSelectedItem()).getName());
            }
        }
    }
    
    @Override
    public Component getFocusTarget() {
        return comboBox;
    }
    
}
