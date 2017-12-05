package codex.editor;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.component.list.EditableList;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.type.StringList;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Редактор свойств типа {@link StringList}, представляет собой нередактируемое 
 * поле ввода содержащее строковое представление списка. Редактирование осуществляется 
 * в вызываемом командой диалоге со списком строк, которые можно редактировать, 
 * добавлять и удалять.
 */
public class StringListEditor extends AbstractEditor {
    
    private static final ImageIcon EDIT_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 18, 18);
    private static final ImageIcon VIEW_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"), 18, 18);
    private static final Dimension SIZE = new Dimension(350, 400);
    
    protected JTextField textField;
    private ListEditor   listEditor;    

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public StringListEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);
        textField.setHighlighter(null);
        
        PlaceHolder placeHolder = new PlaceHolder(IEditor.NOT_DEFINED, textField, PlaceHolder.Show.ALWAYS);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);
        
        listEditor = new StringListEditor.ListEditor();
        addCommand(listEditor);
        
        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    listEditor.execute(propHolder);
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
        if (editable != isEditable()) {
            super.setEditable(editable);
            textField.setForeground(editable && !propHolder.isInherited() ? COLOR_INACTIVE : COLOR_DISABLED);
            listEditor.getButton().setIcon(editable && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON);
        }
    }

    @Override
    public void setValue(Object value) {
        textField.setText(IComplexType.coalesce(value, "").toString());
        //String.join(", ", ((List<String>) value))
    }
    
    private class ListEditor extends EditorCommand {

        public ListEditor() {
            super(EDIT_ICON, Language.get("title"));
        }

        @Override
        public void execute(PropertyHolder contex) {
            List<String> propVal = ((IComplexType<List>) contex.getPropValue()).getValue();
            List<String> values = propVal == null ? new ArrayList() : new ArrayList(propVal);
            EditableList list = new EditableList(values);
            list.setBorder(new EmptyBorder(5, 5, 5, 5));
            list.setEditable(StringListEditor.this.isEditable() && !propHolder.isInherited());
            
            PushButton clean  = new PushButton(ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 26, 26), null);
            clean.setEnabled(StringListEditor.this.isEditable() && !propHolder.isInherited());
            clean.addActionListener((event) -> {
                while (values.size() > 0) {
                    list.deleteItem(0);
                }
                clean.setEnabled(false);
            });
            
            PushButton insert = new PushButton(ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 26, 26), null);
            insert.setEnabled(StringListEditor.this.isEditable() && !propHolder.isInherited());
            insert.addActionListener((event) -> {
                list.insertItem(null);
                clean.setEnabled(values.size() > 0);
            });
            
            PushButton delete = new PushButton(ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 26, 26), null);
            delete.setEnabled(false);
            delete.addActionListener((event) -> {
                list.deleteSelectedItem();
                clean.setEnabled(values.size() > 0);
            });
            list.addSelectionListener((event) -> {
                delete.setEnabled(event.getFirstIndex() != -1 && StringListEditor.this.isEditable() && !propHolder.isInherited());
            });
            
            Box controls = new Box(BoxLayout.Y_AXIS);
            controls.setBorder(new EmptyBorder(5, 0, 0, 5));
            controls.add(insert);
            controls.add(Box.createVerticalStrut(10));
            controls.add(delete);
            controls.add(Box.createVerticalStrut(10));
            controls.add(clean);

            JPanel content = new JPanel(new BorderLayout());
            content.add(list, BorderLayout.CENTER);
            content.add(controls, BorderLayout.EAST);
            
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            confirmBtn.setEnabled(isEditable() && !propHolder.isInherited());
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();

            Dialog dialog = new Dialog(
                    SwingUtilities.getWindowAncestor(editor), 
                    StringListEditor.this.isEditable() && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON, 
                    Language.get("title"), 
                    content,
                    (event) -> {
                        if (event.getID() == Dialog.OK && (
                                (propVal == null && !values.isEmpty()) ||
                                (!values.equals(propVal))
                            )
                        ) {
                            list.stopEditing();
                            propHolder.setValue(new StringList(values));
                        }
                    },
                    confirmBtn,
                    declineBtn
            );
            dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            dialog.setMinimumSize(SIZE);
            dialog.setPreferredSize(SIZE);
            dialog.setVisible(true);
        }
        
        @Override
        public boolean disableWithContext() {
            return false;
        }
    
    }
    
}
