package codex.editor;

import codex.property.PropertyHolder;
import java.nio.file.Path;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

public final class EditorFactory {
    
    public static IEditor createEditor(PropertyHolder propHolder) {
        Class type = propHolder.getType();
        if (type.equals(String.class)) {
            return new StringEditor(propHolder);
        } else if (type.isEnum()) {
            return new EnumEditor(propHolder);
        } else if (type.equals(Path.class)) {
            return new PathEditor(propHolder);
        } else {
            return new AbstractEditor(propHolder) {
                @Override
                public Box createEditor() {
                    JLabel label = new JLabel("<No editor for '"+type.getCanonicalName()+"'>");
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                    
                    Box container = new Box(BoxLayout.X_AXIS);
                    container.add(label);
                    return container;
                }

                @Override
                public void setValue(Object value) {}
            };
        }
    }
    
}
