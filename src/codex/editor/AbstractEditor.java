package codex.editor;

import codex.property.PropertyHolder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;

public abstract class AbstractEditor implements IEditor, FocusListener {
    
    private final static Font DEFAULT = UIManager.getDefaults().getFont("Label.font");
    private final static Font BOLD    = DEFAULT.deriveFont(Font.BOLD);
    
    private final JLabel label;
    protected Box editor;
    
    final PropertyHolder propHolder;

    public AbstractEditor(PropertyHolder propHolder) {
        this.propHolder = propHolder;
        this.label  = new JLabel(propHolder.getTitle());
        this.editor = createEditor();
        
        setToolTipRecursively(editor, propHolder.getDescriprion());
        setValue(propHolder.getValue());
        refresh();
        
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> {
            setValue(propHolder.getValue());
            refresh();
        });
        propHolder.addChangeListener("override", (String name, Object oldValue, Object newValue) -> {
            setValue(propHolder.getValue());
            refresh();
        });
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
    public void focusGained(FocusEvent event) {
        setBorder(BORDER_ACTIVE);
    }

    @Override
    public void focusLost(FocusEvent event) {
        setBorder(BORDER_NORMAL);
    }

    @Override
    public void setEnabled(boolean enabled) {
        setEnabled(editor, enabled);
    }
    
    @Override
    public void setEditable(boolean editable) {
        // Do nothing
    }
    
    private void refresh() {
        setEditable(!propHolder.isInherited());
        label.setFont((propHolder.isValid() ? DEFAULT : BOLD));
    }
    
    void setEnabled(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setEnabled(child, enabled);
            }
        }
    }

}
