package codex.editor;

import codex.command.ICommand;
import codex.command.PropertyCommand;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import java.io.File;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class PathEditor extends AbstractEditor {
    
    protected JTextField textField;

    public PathEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {        
        textField = new JTextField();
        textField.setBorder(new EmptyBorder(0, 5, 0, 5));
        textField.setEditable(false);
        propHolder.addCommand(new PathSelector());

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }
    
    @Override
    public void setEditable(boolean editable) {}

    @Override
    public void setValue(Object value) {
        textField.setText((String) value);
    }
    
    private class PathSelector extends PropertyCommand {

        public PathSelector() {
            super(ImageUtils.getByPath("/images/folder.png"));
        }

        @Override
        public void execute(PropertyHolder contex) {
            JFileChooser fileChooser = new JFileChooser(contex.getValue() == null ? "" : contex.getValue().toString());
//            if (propHolder.getMask() != null && propHolder.getMask() instanceof FileMask) {
//                FileNameExtensionFilter filter = ((FileMask) propHolder.getMask()).getFilter();
//                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
//                fileChooser.setFileFilter(filter);
//                fileChooser.setDialogTitle("Select file");
//            } else if (propHolder.getMask() != null && propHolder.getMask() instanceof DirMask) {
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fileChooser.setDialogTitle("Select directory");
//            } else {
//                fileChooser.setDialogTitle("Select file or directory");
//                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
//            }

            fileChooser.setAcceptAllFileFilterUsed(false);
            int returnVal = fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(editor));

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                setValue(file.getPath());
                contex.setValue(file.toPath());
            }
        }
    
    }
    
}
