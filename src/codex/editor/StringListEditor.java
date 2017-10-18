package codex.editor;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.component.list.EditableList;
import codex.type.IComplexType;
import codex.type.StringList;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
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
    
    private static final ImageIcon EDIT_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 20, 20);
    private static final ImageIcon VIEW_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"), 20, 20);
    private static final Dimension SIZE = new Dimension(300, 350);
    
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
        textField.setBorder(new EmptyBorder(0, 5, 0, 5));
        textField.setEditable(false);
        
        listEditor = new StringListEditor.ListEditor();
        addCommand(listEditor);

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        textField.setForeground(editable && !propHolder.isInherited() ? COLOR_NORMAL : COLOR_DISABLED);
        listEditor.getButton().setIcon(editable && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON);
    }

    @Override
    public void setValue(Object value) {
        textField.setText(String.join(", ", ((List<String>) value)));
    }
    
    private class ListEditor extends EditorCommand {

        public ListEditor() {
            super(EDIT_ICON, Language.get("title"));
        }

        @Override
        public void execute(PropertyHolder contex) {
            List<String> values = new ArrayList<>(((IComplexType<List>) contex.getPropValue()).getValue());
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
                    new AbstractAction() {
                        
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            if (event.getID() == Dialog.OK) {
                                propHolder.setValue(new StringList(values));
                            }
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
        public boolean getAllowsDisable() {
            return false;
        }
    
    }
    
}
