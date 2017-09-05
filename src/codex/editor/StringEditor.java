package codex.editor;

import codex.property.PropertyHolder;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class StringEditor extends AbstractEditor implements DocumentListener {
    
    protected JTextField textField;

    public StringEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setBorder(new EmptyBorder(0, 5, 0, 5));
        textField.addFocusListener(this);
        textField.getDocument().addDocumentListener(this);

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }
    
    @Override
    public void setEditable(boolean editable) {
        textField.setEditable(editable && !propHolder.isOverridden());
    }

    @Override
    public void setValue(Object value) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                textField.getDocument().removeDocumentListener(StringEditor.this);
                textField.setText(value == null ? "" : value.toString());
                textField.getDocument().addDocumentListener(StringEditor.this);
            }
        });
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
        propHolder.setValue(textField.getText());
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
        propHolder.setValue(textField.getText());
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
        propHolder.setValue(textField.getText());
    }
    
}
