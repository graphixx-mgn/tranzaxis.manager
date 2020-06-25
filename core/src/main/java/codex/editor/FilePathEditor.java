package codex.editor;

import codex.command.EditorCommand;
import codex.mask.IPathMask;
import codex.property.PropertyHolder;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link FilePath}, представляет собой нередактируемое поле 
 * ввода содержащее путь к файлу/папке. Редактирование осуществляется 
 * в вызываемом командой диалоге выбора объекта файловой системы.
 */
public class FilePathEditor extends AbstractEditor<FilePath, Path> {

    private static final ImageIcon ICON = ImageUtils.getByPath("/images/folder.png");
    
    private JTextField   textField;
    private PathSelector pathSelector;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public FilePathEditor(PropertyHolder<FilePath, Path> propHolder) {
        super(propHolder);

        pathSelector = new PathSelector();
        addCommand(pathSelector);
        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    pathSelector.execute(propHolder);
                }
            }
        });
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);
        textField.addFocusListener(this);
        
        PlaceHolder placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField, PlaceHolder.Show.ALWAYS);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        textField.setForeground(editable && !propHolder.isInherited() ? COLOR_INACTIVE : COLOR_DISABLED);
    }

    @Override
    public void setValue(Path path) {
        textField.setText(path == null ? "" : path.toString());
    }
    
    private class PathSelector extends EditorCommand<FilePath, Path> {

        private PathSelector() {
            super(ICON, Language.get("title"));
        }

        @Override
        public void execute(PropertyHolder<FilePath, Path> context) {
            JFileChooser fileChooser = new JFileChooser(context.getPropValue() == null ? "" : context.toString()) {
                @Override
                protected javax.swing.JDialog createDialog(java.awt.Component parent) throws java.awt.HeadlessException {
                    javax.swing.JDialog dialog = super.createDialog(parent);
                    dialog.setIconImage(ICON.getImage());
                    return dialog;
                }
            };
            fileChooser.setDialogTitle(Language.get("title"));

            IPathMask mask = context.getPropValue().getMask();
            if (mask != null) {
                fileChooser.setFileSelectionMode(mask.getSelectionMode());
                if (mask.getFilter() != null) {
                    fileChooser.setFileFilter(mask.getFilter());
                }
            } else {
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            }
            fileChooser.setAcceptAllFileFilterUsed(false);
            int returnVal = fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(editor));

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                setValue(file.toPath());
                context.setValue(file.toPath());
            }
        }
    }
    
}
