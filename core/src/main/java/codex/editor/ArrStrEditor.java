package codex.editor;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.component.list.EditableList;
import codex.mask.IArrMask;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Редактор свойств типа {@link ArrStr}, представляет собой нередактируемое 
 * поле ввода содержащее строковое представление списка. Редактирование осуществляется 
 * в вызываемом командой диалоге со списком строк, которые можно редактировать, 
 * добавлять и удалять.
 */
@ThreadSafe
public class ArrStrEditor extends AbstractEditor<ArrStr, List<String>> {
    
    private static final ImageIcon EDIT_ICON = ImageUtils.getByPath("/images/edit.png");
    private static final ImageIcon VIEW_ICON = ImageUtils.getByPath("/images/view.png");
    private static final Dimension SIZE = new Dimension(350, 400);
    
    private JTextField   textField;
    private final JLabel signDelete;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public ArrStrEditor(PropertyHolder<ArrStr, List<String>> propHolder) {
        super(propHolder);
        
        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"), 
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));
        signDelete.setBorder(new EmptyBorder(0, 3, 0, 0));
        signDelete.setCursor(Cursor.getDefaultCursor());
        
        signDelete.setVisible(!propHolder.isEmpty() && !propHolder.isInherited() && isEditable() && textField.isFocusOwner());
        textField.add(signDelete, BorderLayout.EAST);
        
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                IArrMask mask = propHolder.getPropValue().getMask();
                if (mask != null) {
                    propHolder.setValue(mask.getCleanValue());
                } else {
                    propHolder.setValue(null);
                }
            }
        });

        final IArrMask mask = propHolder.getPropValue().getMask();
        final EditorCommand defCommand = mask instanceof EditorCommand ? (EditorCommand) mask : new ArrStrEditor.ListEditor();
        addCommand(defCommand);

        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    defCommand.execute(propHolder);
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
        container.add(textField);
        return container;
    }
    
    @Override
    protected void updateEditable(boolean editable) {
        textField.setForeground(editable && !propHolder.isInherited() ? COLOR_NORMAL : COLOR_DISABLED);
        textField.setBackground(editable && !propHolder.isInherited() ? Color.WHITE  : null);
        getEditor().setBackground(editable && !propHolder.isInherited() ? Color.WHITE  : null);
    }

    @Override
    protected void updateValue(List<String> value) {
        final IArrMask mask = propHolder.getPropValue().getMask();
        if (mask != null && mask.getFormat() != null && value != null && !value.isEmpty()) {
            textField.setText(
                    MessageFormat.format(
                            mask.getFormat(),
                            value.stream().map((item) -> item == null ? "" : item).toArray()
                    ).replaceAll("\\{\\d+\\}", "")
            );
            textField.setCaretPosition(0);
        } else {
            textField.setText(IComplexType.coalesce(value, "").toString());
            textField.setCaretPosition(0);
        }
        if (signDelete!= null) {
            signDelete.setVisible(!propHolder.isEmpty() && !propHolder.isInherited() && isEditable() && textField.isFocusOwner());
        }
    }

    @Override
    public void focusGained(FocusEvent event) {
        super.focusGained(event);
        signDelete.setVisible(!propHolder.isEmpty() && !propHolder.isInherited() && isEditable());
    }
    
    @Override
    public void focusLost(FocusEvent event) {
        super.focusLost(event);
        signDelete.setVisible(false);
    }

    @Override
    public Component getFocusTarget() {
        return textField;
    }


    private class ListEditor extends EditorCommand<ArrStr, List<String>> {

        private ListEditor() {
            super(EDIT_ICON, Language.get("title"));
            activator = holder -> new CommandStatus(
                    ArrStrEditor.this.isEditable() || !holder.isEmpty(),
                    ArrStrEditor.this.isEditable() && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON
            );
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public void execute(PropertyHolder<ArrStr, List<String>> context) {
            SwingUtilities.invokeLater(() -> {
                List<String> propVal = context.getPropValue().getValue();

                List<String> values = propVal == null ? new ArrayList<>() : new ArrayList<>(propVal);
                EditableList list = new EditableList(values);
                list.setBorder(new EmptyBorder(5, 5, 5, 5));
                list.setEditable(ArrStrEditor.this.isEditable() && !propHolder.isInherited());

                PushButton clean  = new PushButton(ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 26, 26), null);
                clean.setEnabled(ArrStrEditor.this.isEditable() && !propHolder.isInherited() && values.size() > 0);
                clean.addActionListener((event) -> {
                    while (values.size() > 0) {
                        list.deleteItem(0);
                    }
                    clean.setEnabled(false);
                });

                PushButton insert = new PushButton(ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 26, 26), null);
                insert.setEnabled(ArrStrEditor.this.isEditable() && !propHolder.isInherited());
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
                    delete.setEnabled(event.getFirstIndex() != -1 && ArrStrEditor.this.isEditable() && !propHolder.isInherited());
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
                        ArrStrEditor.this.isEditable() && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON,
                        Language.get("title"),
                        content,
                        (event) -> {
                            if (event.getID() == Dialog.OK && (
                                    (propVal == null && !values.isEmpty()) ||
                                            (propVal != null && !values.equals(propVal))
                            )
                            ) {
                                list.stopEditing();
                                propHolder.setValue(values);
                            }
                        },
                        confirmBtn,
                        declineBtn
                );
                dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                dialog.setMinimumSize(SIZE);
                dialog.setPreferredSize(SIZE);
                dialog.setVisible(true);
            });
        }
    }
    
}
