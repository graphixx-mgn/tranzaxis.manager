package codex.explorer.browser;

import codex.command.EntityCommand;
import codex.component.button.IButton;
import codex.component.render.DefaultRenderer;
import codex.editor.AbstractEditor;
import static codex.editor.IEditor.FONT_VALUE;
import codex.model.Entity;
import codex.property.PropertyHolder;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;

/**
 * Редактор ссылки на команду сущности.
 */
class CommandChooser extends AbstractEditor implements ActionListener {
    
    private JComboBox comboBox;
    private Entity    entity;

    /**
     * Конструктор редактора.
     * @param propHolder Свойство для хранения имени команды.
     * @param entity Сущность, в контексте которой имеется команда.
     */
    CommandChooser(PropertyHolder propHolder, Entity entity) {
        super(propHolder);
        this.entity = entity;
    }
    
    /**
     * Обновление ссылки на сущность с пересчетом допустимых значений редактора.
     */
    public final void setEntity(Entity entity) {
        this.entity = entity;
        comboBox.removeAllItems();
        if (entity != null) {
            entity.getCommands().stream().filter((command) -> (!command.getButton().isInactive())).forEachOrdered((command) -> {
                comboBox.addItem(command);
            });
        }
        comboBox.addItem(new NullValue());
        comboBox.setSelectedItem(comboBox.getItemAt(comboBox.getItemCount()-1));
    }

    @Override
    public Box createEditor() {
        comboBox = new JComboBox();
        if (entity != null) {
            entity.getCommands().stream().filter((command) -> (!command.getButton().isInactive())).forEachOrdered((command) -> {
                comboBox.addItem(command);
            });
        }
        comboBox.addItem(new NullValue());
        
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ("Panel.background"), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);
        
        comboBox.setFont(FONT_VALUE);
        comboBox.setRenderer(new DefaultRenderer());
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
            comboBox.setSelectedItem(comboBox.getItemAt(comboBox.getItemCount()-1));
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
        if (comboBox.getSelectedIndex() == comboBox.getItemCount()-1) {
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
