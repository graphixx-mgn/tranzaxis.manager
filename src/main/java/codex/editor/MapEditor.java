package codex.editor;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.mask.IMask;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.AbstractMap;
import java.util.LinkedHashMap;

public class MapEditor<K, V> extends AbstractEditor {

    private static final ImageIcon  EDIT_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 18, 18);
    private static final ImageIcon  VIEW_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"), 18, 18);
    private static final ImageIcon   ADD_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 26, 26);
    private static final ImageIcon   DEL_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 26, 26);
    private static final ImageIcon CLEAR_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 26, 26);
    private static final Dimension SIZE = new Dimension(450, 400);

    private JTextField textField;
    private EditMode mode = EditMode.ModifyAllowed;
    private java.util.Map<ISerializableType<K, ? extends IMask<K>>, ISerializableType<V, ? extends IMask<V>>> internalValue;

    public MapEditor(PropertyHolder propHolder) {
        super(propHolder);

        EditorCommand defCommand = new TableEditor();
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

    public void setMode(EditMode mode) {
        this.mode = mode;
    }

    @Override
    public Box createEditor() {
        internalValue = new LinkedHashMap<>();

        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);
        textField.setBackground(Color.WHITE);
        textField.addFocusListener(this);

        PlaceHolder placeHolder = new PlaceHolder(IEditor.NOT_DEFINED, textField, PlaceHolder.Show.ALWAYS);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }

    @Override
    public void setValue(Object value) {
        internalValue.clear();

        Map<K, V> map = (Map<K, V>) propHolder.getOwnPropValue();
        map.getValue().forEach((k, v) -> {
            java.util.Map.Entry<ISerializableType<K, ? extends IMask<K>>, ISerializableType<V, ? extends IMask<V>>> entry = map.getEntry();
            entry.getKey().setValue(k);
            entry.getValue().setValue(v);
            internalValue.put(entry.getKey(), entry.getValue());
        });
        if (internalValue.isEmpty()) {
            textField.setText("");
        } else {
            textField.setText(Language.get(Map.class, "defined"));
        }
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        textField.setFocusable(false);
        textField.setForeground(editable && !propHolder.isInherited() ? Color.decode("#3399FF") : COLOR_DISABLED);
        textField.setOpaque(editable && !propHolder.isInherited());
    }

    public java.util.Map.Entry<PropertyHolder, PropertyHolder> createHolderEntry() {
        Map<K, V> map = (Map<K, V>) propHolder.getOwnPropValue();
        java.util.Map.Entry<ISerializableType<K, ? extends IMask<K>>, ISerializableType<V, ? extends IMask<V>>> entry = map.getEntry();
        return new AbstractMap.SimpleEntry<>(
                new PropertyHolder<>("key", entry.getKey(), true),
                new PropertyHolder<>("val", entry.getValue(), true)
        );
    }


    public enum EditMode {
        ModifyPermitted, ModifyAllowed
    }


    private class TableEditor extends EditorCommand {

        TableEditor() {
            super(EDIT_ICON, Language.get(MapEditor.class, "title"));
            activator = holder -> new CommandStatus(
                    true,
                    MapEditor.this.isEditable() && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON
            );
        }

        @Override
        public void execute(PropertyHolder context) {
            Class kClass = ((Map) propHolder.getPropValue()).getKeyClass();
            Class vClass = ((Map) propHolder.getPropValue()).getValClass();

            DefaultTableModel tableModel = new DefaultTableModel(ArrStr.parse(propHolder.getPlaceholder()).toArray(), 0) {
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    Class  c = columnIndex == 0 ? kClass : vClass;
                    return c == Bool.class ? Bool.class : IComplexType.class;
                }
                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return columnIndex == 1 && MapEditor.this.isEditable() && !propHolder.isInherited();
                }
            };

            JTable table = new JTable(tableModel);
            table.setRowHeight((IEditor.FONT_VALUE.getSize() * 2));
            table.setShowVerticalLines(false);
            table.setIntercellSpacing(new Dimension(0,0));
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            internalValue.entrySet().stream().forEachOrdered(entry -> {
                Object[] row = new Object[] {entry.getKey(), entry.getValue()};
                ((DefaultTableModel) table.getModel()).addRow(row);
            });

            GeneralRenderer renderer = new GeneralRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(
                            table,
                            ISerializableType.class.isAssignableFrom(value.getClass()) ?
                                    ((ISerializableType) value).getValue() : value,
                            isSelected,
                            hasFocus,
                            row,
                            column
                    );
                    if (ISerializableType.class.isAssignableFrom(value.getClass())) {
                        if (((ISerializableType) value).isEmpty()) {
                            c.setForeground(Color.decode("#999999"));
                        }
                    }
                    return c;
                }
            };
            table.setDefaultRenderer(Bool.class,         renderer);
            table.setDefaultRenderer(IComplexType.class, renderer);
            table.getTableHeader().setDefaultRenderer(renderer);
            table.getColumnModel().getColumn(1).setCellEditor(new CellEditor());

            final JScrollPane scrollPane = new JScrollPane();
            scrollPane.getViewport().setBackground(Color.WHITE);
            scrollPane.setViewportView(table);
            scrollPane.setBorder(new CompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    new MatteBorder(1, 1, 1, 1, Color.GRAY)
            ));

            JPanel content = new JPanel(new BorderLayout());
            content.add(scrollPane, BorderLayout.CENTER);

            if (mode.equals(EditMode.ModifyAllowed)) {
               PushButton clean  = new PushButton(CLEAR_ICON, null);
                clean.setEnabled(MapEditor.this.isEditable() && !propHolder.isInherited() && internalValue.size() > 0);
                clean.addActionListener((event) -> {
                    internalValue.clear();
                    while (tableModel.getRowCount() > 0) {
                        tableModel.removeRow(0);
                    }
                    table.getSelectionModel().clearSelection();
                });
                table.getModel().addTableModelListener(event -> {
                    if (event.getType() == TableModelEvent.INSERT || event.getType() == TableModelEvent.DELETE) {
                        clean.setEnabled(table.getRowCount() > 0);
                    }
                });

                PushButton insert = new PushButton(ADD_ICON, null);
                insert.setEnabled(MapEditor.this.isEditable() && !propHolder.isInherited());
                insert.addActionListener((e) -> {
                    java.util.Map.Entry<PropertyHolder, PropertyHolder>  entry = createHolderEntry();

                    ParamModel model = new ParamModel();
                    model.addProperty(entry.getKey());
                    model.addProperty(entry.getValue());
                    DialogButton btnConfirm = codex.component.dialog.Dialog.Default.BTN_OK.newInstance(Language.get(MapEditor.class, "confirm@title"));
                    DialogButton btnDecline = codex.component.dialog.Dialog.Default.BTN_CANCEL.newInstance();

                    new codex.component.dialog.Dialog(
                            FocusManager.getCurrentManager().getActiveWindow(),
                            ADD_ICON,
                            Language.get(MapEditor.class, "add@title"),
                            new EditorPage(model),
                            event -> {
                                if (event.getID() == codex.component.dialog.Dialog.OK) {
                                    tableModel.addRow(new Object[]{
                                            (ISerializableType) entry.getKey().getPropValue(),
                                            (ISerializableType) entry.getValue().getPropValue()
                                    });
                                    internalValue.put(
                                            (ISerializableType) entry.getKey().getPropValue(),
                                            (ISerializableType) entry.getValue().getPropValue()
                                    );
                                }
                            },
                            btnConfirm,
                            btnDecline
                    ) {
                        @Override
                        public Dimension getPreferredSize() {
                            return new Dimension(550, super.getPreferredSize().height);
                        }
                    }.setVisible(true);
                });

                PushButton delete = new PushButton(DEL_ICON, null);
                delete.setEnabled(false);
                delete.addActionListener((event) -> {
                    int rowIdx = table.getSelectedRow();
                    internalValue.remove(table.getModel().getValueAt(rowIdx, 0));
                    tableModel.removeRow(rowIdx);
                    if (rowIdx < tableModel.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(rowIdx, rowIdx);
                    } else if (rowIdx == tableModel.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(rowIdx - 1, rowIdx - 1);
                    } else {
                        table.getSelectionModel().clearSelection();
                    }
                });
                table.getSelectionModel().addListSelectionListener(event -> {
                    delete.setEnabled(table.getSelectedRow() >= 0);
                });

                Box controls = new Box(BoxLayout.Y_AXIS);
                controls.setBorder(new EmptyBorder(5, 0, 0, 5));
                controls.add(insert);
                controls.add(Box.createVerticalStrut(10));
                controls.add(delete);
                controls.add(Box.createVerticalStrut(10));
                controls.add(clean);

                content.add(controls, BorderLayout.EAST);
            }

            DialogButton confirmBtn = codex.component.dialog.Dialog.Default.BTN_OK.newInstance();
            confirmBtn.setEnabled(isEditable() && !propHolder.isInherited());
            DialogButton declineBtn = codex.component.dialog.Dialog.Default.BTN_CANCEL.newInstance();

            Dialog dialog = new codex.component.dialog.Dialog(
                    SwingUtilities.getWindowAncestor(editor),
                    MapEditor.this.isEditable() && !propHolder.isInherited() ? EDIT_ICON : VIEW_ICON,
                    Language.get(MapEditor.class, "title"),
                    content,
                    event -> {
                        if (event.getID() == Dialog.OK) {
                            java.util.Map<K, V> newValue = new LinkedHashMap<>();
                            internalValue.forEach((key, value) -> newValue.put(key.getValue(), value.getValue()));
                            propHolder.setValue(newValue);
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
    }


    private class CellEditor extends AbstractCellEditor implements TableCellEditor {

        private IEditor editor;

        CellEditor() {}

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            ISerializableType complexVal = (ISerializableType) table.getModel().getValueAt(row, column);
            editor = complexVal.editorFactory().newInstance(new PropertyHolder<>(
                    "row#"+row,
                    complexVal,
                    true
            ));
            EventQueue.invokeLater(() -> editor.getFocusTarget().requestFocus());

            if (value.getClass() == Bool.class) {
                JComponent renderedComp = (JComponent) table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, true, true, row, column);
                JPanel container = new JPanel();
                container.setOpaque(true);
                container.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 2));
                container.setBackground(renderedComp.getBackground());
                container.setBorder(renderedComp.getBorder());
                container.add(editor.getEditor());
                return container;
            } else {
                return editor.getEditor();
            }
        }

        @Override
        public Object getCellEditorValue() {
            editor.stopEditing();
            return ((AbstractEditor) editor).propHolder.getOwnPropValue();
        }

    }
}
