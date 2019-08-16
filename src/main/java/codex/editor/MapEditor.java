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
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.LinkedHashMap;

public class MapEditor<K, V> extends AbstractEditor<Map<K, V>, java.util.Map<K, V>> {

    private static final ImageIcon  EDIT_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 18, 18);
    private static final ImageIcon  VIEW_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"), 18, 18);
    private static final ImageIcon   ADD_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 26, 26);
    private static final ImageIcon   DEL_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 26, 26);
    private static final ImageIcon CLEAR_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 26, 26);

    private JTextField textField;
    private EditMode mode = EditMode.ModifyAllowed;
    private java.util.Map<K, V> internalValue;

    public MapEditor(PropertyHolder<Map<K, V>, java.util.Map<K, V>> propHolder) {
        super(propHolder);

        TableEditor defCommand = new TableEditor();
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
    public void setValue(java.util.Map<K, V> value) {
        internalValue.clear();
        internalValue.putAll(value);
        if (internalValue.isEmpty()) {
            textField.setText("");
        } else {
            textField.setText(MessageFormat.format(Language.get(Map.class, "defined"), internalValue.size()));
        }
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        textField.setFocusable(false);
        textField.setForeground(editable && !propHolder.isInherited() ? Color.decode("#3399FF") : COLOR_DISABLED);
        textField.setOpaque(editable && !propHolder.isInherited());
    }

    private java.util.Map.Entry<
            PropertyHolder<ISerializableType<K, ? extends IMask<K>>, K>,
            PropertyHolder<ISerializableType<V, ? extends IMask<V>>, V>
    > createHolderEntry() {
        Map<K, V> map = propHolder.getOwnPropValue();
        java.util.Map.Entry<? extends ISerializableType<K, ? extends IMask<K>>, ? extends ISerializableType<V, ? extends IMask<V>>> entry = map.getEntry();

        return new AbstractMap.SimpleEntry<>(
                new PropertyHolder<>("key", entry.getKey(), true),
                new PropertyHolder<>("val", entry.getValue(), true)
        );
    }


    public enum EditMode {
        ModifyPermitted, ModifyAllowed
    }


    private class TableEditor extends EditorCommand<Map<K, V>, java.util.Map<K, V>> {

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
                    java.util.Map.Entry<
                            PropertyHolder<ISerializableType<K, ? extends IMask<K>>, K>,
                            PropertyHolder<ISerializableType<V, ? extends IMask<V>>, V>
                    >  entry = createHolderEntry();

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
                                    tableModel.addRow(new Object[] {
                                            entry.getKey().getPropValue(),
                                            entry.getValue().getPropValue()
                                    });
                                    internalValue.put(
                                            entry.getKey().getPropValue().getValue(),
                                            entry.getValue().getPropValue().getValue()
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
                    propHolder.getTitle(),
                    content,
                    event -> {
                        if (event.getID() == Dialog.OK) {
                            propHolder.setValue(new LinkedHashMap<>(internalValue));
                        } else {
                            setValue(new LinkedHashMap<>(propHolder.getPropValue().getValue()));
                        }
                    },
                    confirmBtn,
                    declineBtn
            );
            dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            dialog.setVisible(true);
        }
    }


    private class CellEditor extends AbstractCellEditor implements TableCellEditor {

        private java.util.Map.Entry<? extends ISerializableType<K, ? extends IMask<K>>, ? extends ISerializableType<V, ? extends IMask<V>>> entry;
        private IEditor<? extends ISerializableType<V, ? extends IMask<V>>, V> editor;

        CellEditor() {}

        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            entry = propHolder.getPropValue().getEntry();
            entry.getKey().setValue((K) table.getValueAt(row, 0));
            entry.getValue().setValue((V) value);

            PropertyHolder propertyHolder = new PropertyHolder<>(
                    "row#"+row,
                    entry.getValue(),
                    true
            );

            editor = entry.getValue().editorFactory().newInstance(propertyHolder);
            EventQueue.invokeLater(() -> editor.getFocusTarget().requestFocus());

            if (entry.getValue().getClass() == Bool.class) {
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
            V value = editor.getValue();
            internalValue.put(entry.getKey().getValue(), entry.getValue().getValue());
            return value;
        }

    }
}
