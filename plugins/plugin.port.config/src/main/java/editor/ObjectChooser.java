package editor;

import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.editor.AbstractEditor;
import codex.editor.IEditorFactory;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public abstract class ObjectChooser extends AbstractEditor<AnyType, Object> implements ActionListener {

    private JComboBox<Object> comboBox;

    public ObjectChooser(String propName) {
        super(new PropertyHolder<>(
                propName,
                new AnyType(),
                false
        ));
        propHolder.setValue(new AnyType() {
            @Override
            public IEditorFactory<AnyType, Object> editorFactory() {
                return propHolder -> ObjectChooser.this;
            }
        });
    }

    public abstract List<Object> getValues();

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        comboBox.setEnabled(editable);
    }

    public PropertyHolder getProperty() {
        return propHolder;
    }

    @Override
    public Box getEditor() {
        comboBox.removeActionListener(this);
        comboBox.removeAllItems();

        comboBox.addItem(new AbstractEditor.Undefined());
        getValues().forEach((item) -> comboBox.addItem(item));
        if (!propHolder.getPropValue().isEmpty()) {
            setValue(propHolder.getPropValue().getValue());
        }

        comboBox.addActionListener(this);
        return super.getEditor();
    }

    @Override
    public Box createEditor() {
        comboBox = new JComboBox<Object>() {
            @Override
            public Color getForeground() {
                return getValue() == null ? Color.GRAY : Color.BLACK;
            }
        };
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ("Panel.background"), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);

        comboBox.addItem(new AbstractEditor.Undefined());
        comboBox.setFont(FONT_VALUE);
        comboBox.setRenderer(new GeneralRenderer<>());

        Object child = comboBox.getAccessibleContext().getAccessibleChild(0);
        BasicComboPopup popup = (BasicComboPopup)child;
        popup.setBorder(IButton.PRESS_BORDER);

        comboBox.addFocusListener(this);
        comboBox.addActionListener(this);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(comboBox);
        return container;
    }

    @Override
    public void setValue(Object value) {
        if (value == null) {
            comboBox.setSelectedItem(comboBox.getItemAt(0));
        } else {
            if (!comboBox.getSelectedItem().equals(value)) {
                if (((DefaultComboBoxModel) comboBox.getModel()).getIndexOf(value) == -1) {
                    comboBox.addItem(value);
                }
                comboBox.setSelectedItem(value);
            }
        }
    }

    @Override
    public Component getFocusTarget() {
        return comboBox;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (comboBox.getSelectedIndex() == 0) {
            propHolder.setValue(null);
        } else {
            if (!comboBox.getSelectedItem().equals(propHolder.getPropValue().getValue())) {
                propHolder.setValue(comboBox.getSelectedItem());
            }
        }
    }
}
