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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link FilePath}, представляет собой нередактируемое поле 
 * ввода содержащее путь к файлу/папке. Редактирование осуществляется 
 * в вызываемом командой диалоге выбора объекта файловой системы.
 */
public class FilePathEditor extends AbstractEditor {
    
    protected JTextField  textField;
    private EditorCommand pathSelector;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public FilePathEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);
        
        PlaceHolder placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField, PlaceHolder.Show.ALWAYS);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);
        
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
    public void setValue(Object value) {
        textField.setText(value == null ? "" : value.toString());
    }
    
    private class PathSelector extends EditorCommand {

        public PathSelector() {
            super(ImageUtils.resize(ImageUtils.getByPath("/images/folder.png"), 18, 18), Language.get("title"));
        }

        @Override
        public void execute(PropertyHolder contex) {
            JFileChooser fileChooser = new JFileChooser(contex.getPropValue() == null ? "" : contex.toString());
            fileChooser.setDialogTitle(Language.get("title"));
            IPathMask mask = (IPathMask) contex.getPropValue().getMask();
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
                setValue(file.getPath());
                contex.setValue(file.toPath());
            }
        }
    
    }
    
}
