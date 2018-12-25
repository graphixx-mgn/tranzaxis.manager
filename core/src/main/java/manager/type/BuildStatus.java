package manager.type;

import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import static codex.editor.IEditor.FONT_VALUE;
import codex.editor.IEditorFactory;
import codex.editor.PlaceHolder;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import manager.commands.offshoot.BuildWC;

public final class BuildStatus extends ArrStr {
    
    private List<String> value = null;
    
    public BuildStatus() {
    }
    
    public BuildStatus(Long revision, Boolean failed) {
        setValue(new StatusHolder(revision, failed));
    }
    
    @Override
    public void setValue(List<String> value) {
        if (value != null) {
            this.value = new StatusHolder(value);
        } else {
            this.value = null;
        }
    }
    
    @Override
    public List<String> getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return merge(value);
    }
    
    @Override
    public String getQualifiedValue(List<String> val) {
        return val == null ? "<NULL>" :  MessageFormat.format("({0})'", val);
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return (PropertyHolder propHolder) -> {
            return new BuildStatusEditor(propHolder);
        };
    }
    
    class StatusHolder extends LinkedList<String> implements Iconified {

        public StatusHolder(Collection<? extends String> c) {
            super(c);
        }

        public StatusHolder(Long revision, Boolean failed) {
            super(Arrays.asList(new String[] {revision.toString(), failed ? "1" : "0"}));
        }
        
        @Override
        public String toString() {
            return get(0).concat(" / ").concat((get(1).equals("1") ? StatusIcon.Fail : StatusIcon.Success).toString());
        }
        
        @Override
        public ImageIcon getIcon() {
            return (get(1).equals("1") ? StatusIcon.Fail : StatusIcon.Success).getIcon();
        }
    }
    
    enum StatusIcon implements Iconified {

        Fail(
            Language.get(BuildWC.class.getSimpleName(), "command@fail"), 
            ImageUtils.getByPath("/images/svn_erroneous.png")
        ),
        Success(
            Language.get(BuildWC.class.getSimpleName(), "command@success"), 
            ImageUtils.getByPath("/images/svn_successful.png"
        ));
        
        private final String    title;
        private final ImageIcon icon;

        private StatusIcon(String title, ImageIcon icon) {
            this.title = title;
            this.icon  = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }
        
        @Override
        public String toString() {
            return title;
        }
        
    }
    
    class BuildStatusEditor extends AbstractEditor {
    
        private JTextField  textField;
        private JLabel      iconLabel;
        private PlaceHolder placeHolder;

        public BuildStatusEditor(PropertyHolder propHolder) {
            super(propHolder);
        }

        @Override
        public Box createEditor() {
            textField = new JTextField();
            textField.setBorder(new EmptyBorder(0, 3, 0, 3));
            textField.setEditable(false);

            placeHolder = new PlaceHolder(IEditor.NOT_DEFINED, textField, PlaceHolder.Show.FOCUS_LOST);
            placeHolder.setBorder(textField.getBorder());
            placeHolder.changeAlpha(100);

            iconLabel = new JLabel();
            iconLabel.setFont(FONT_VALUE);
            textField.add(iconLabel, BorderLayout.WEST);

            Box container = new Box(BoxLayout.X_AXIS);
            container.add(textField);
            return container;
        }

        @Override
        public void setValue(Object value) {
            StatusHolder holder = (StatusHolder) value;
            iconLabel.setVisible(value != null);
            if (value != null) {
                iconLabel.setIcon(ImageUtils.resize(
                        holder.getIcon(),
                        textField.getPreferredSize().height+2, 
                        textField.getPreferredSize().height+2
                ));
                iconLabel.setText(holder.toString());
            }
            placeHolder.setVisible(value == null);
        }

        @Override
        public void setEditable(boolean editable) {
            super.setEditable(false);
        }

    }
    
}
