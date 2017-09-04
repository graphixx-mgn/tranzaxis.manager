package codex.editor;

import codex.property.PropertyHolder;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

public abstract class AbstractEditor implements IEditor, FocusListener {
    
    private final JLabel label;
    protected Box editor;
    
    final PropertyHolder propHolder;

    public AbstractEditor(PropertyHolder propHolder) {
        this.propHolder = propHolder;
        this.label  = new JLabel(propHolder.getTitle());
        this.editor = createEditor();
        
        setToolTipRecursively(editor, propHolder.getDescriprion());
        setValue(propHolder.getValue());
    }

    @Override
    public final JLabel getLabel() {
        return label;
    }

    @Override
    public final Box getEditor() {
        setBorder(IEditor.BORDER_NORMAL);
        return editor;
    }

    @Override
    public void setBorder(Border border) {
        editor.setBorder(border);
    };
    
    private static void setToolTipRecursively(JComponent component, String text) {
        component.setToolTipText(text);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent) {
                setToolTipRecursively((JComponent) child, text);
            }
        }
    }

    @Override
    public void focusGained(FocusEvent fe) {
        setBorder(BORDER_ACTIVE);
    }

    @Override
    public void focusLost(FocusEvent fe) {
        setBorder(BORDER_NORMAL);
    }
}
