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
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapEditor<K, V> extends AbstractEditor<Map<K, V>, java.util.Map<K, V>> {

    private static final ImageIcon  EDIT_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 18, 18);
    private static final ImageIcon  VIEW_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"), 18, 18);
    private static final ImageIcon   ADD_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 26, 26);
    private static final ImageIcon   DEL_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 26, 26);
    private static final ImageIcon CLEAR_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 26, 26);
    private static final ImageIcon  WARN_ICON = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), 26, 26);

    private JTextField textField;
    private EditMode mode = EditMode.ModifyAllowed;
    private java.util.Map<K, V> internalValue;
    private final String[] placeholder;

    public MapEditor(PropertyHolder<Map<K, V>, java.util.Map<K, V>> propHolder) {
        super(propHolder);

        placeholder = ArrStr.parse(propHolder.getPlaceholder()).toArray(new String[]{});

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

    public enum EditMode {
        ModifyPermitted, ModifyAllowed
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
        placeHolder.setForeground(COLOR_DISABLED);
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
            textField.setText(MessageFormat.format(
                    Language.get(codex.type.Map.class, "defined"),
                    Language.getPlural().npl(internalValue.size(), " "+Language.get(codex.type.Map.class, "item"))
            ));
        }
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        textField.setFocusable(false);
        textField.setForeground(editable && !propHolder.isInherited() ? Color.decode("#3399FF") : COLOR_INACTIVE);
        textField.setOpaque(editable && !propHolder.isInherited());
    }

    private java.util.Map.Entry<PropertyHolder<ISerializableType<K, ? extends IMask<K>>, K>,PropertyHolder<ISerializableType<V, ? extends IMask<V>>, V>> createHolderEntry() {
        Map<K, V> map = propHolder.getOwnPropValue();
        java.util.Map.Entry<? extends ISerializableType<K, ? extends IMask<K>>, ? extends ISerializableType<V, ? extends IMask<V>>> entry = map.newEntry();

        return new AbstractMap.SimpleEntry<>(
                new PropertyHolder<>("key", placeholder[0], null, entry.getKey(), true),
                new PropertyHolder<>("val", placeholder[1], null, entry.getValue(), true)
        );
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
        public void execute(PropertyHolder<Map<K, V>, java.util.Map<K, V>> context) {
            Class kClass = ((Map) propHolder.getPropValue()).getKeyClass();
            Class vClass = ((Map) propHolder.getPropValue()).getValClass();

            DefaultTableModel tableModel = new DefaultTableModel(placeholder, 0) {
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
                    boolean isSerializable = value != null && ISerializableType.class.isAssignableFrom(value.getClass());
                    boolean isEmpty = value == null || (isSerializable && ((ISerializableType) value).isEmpty());
                    Object  shownValue = value == null ? null : (isSerializable ? ((ISerializableType) value).getValue() : value);

                    Component c = super.getTableCellRendererComponent(
                            table,
                            shownValue,
                            isSelected,
                            hasFocus,
                            row,
                            column
                    );
                    if (isEmpty) {
                        c.setForeground(Color.decode("#999999"));
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
                PushButton insert = new PushButton(ADD_ICON, null);
                insert.setEnabled(MapEditor.this.isEditable() && !propHolder.isInherited());
                insert.addActionListener(new InsertAction(tableModel));

                PushButton delete = new PushButton(DEL_ICON, null);
                delete.setEnabled(false);
                delete.addActionListener(new DeleteAction(tableModel, table));
                table.getSelectionModel().addListSelectionListener(event -> delete.setEnabled(
                        MapEditor.this.isEditable() &&
                        !propHolder.isInherited() &&
                        table.getSelectedRow() >= 0
                ));

                PushButton clear = new PushButton(CLEAR_ICON, null);
                clear.setEnabled(MapEditor.this.isEditable() && !propHolder.isInherited() && internalValue.size() > 0);
                clear.addActionListener(new ClearAction(tableModel, table));
                table.getModel().addTableModelListener(event -> {
                    if (event.getType() == TableModelEvent.INSERT || event.getType() == TableModelEvent.DELETE) {
                        clear.setEnabled(table.getRowCount() > 0);
                    }
                });

                Box controls = new Box(BoxLayout.Y_AXIS);
                controls.setBorder(new EmptyBorder(5, 0, 0, 5));
                controls.add(insert);
                controls.add(Box.createVerticalStrut(10));
                controls.add(delete);
                controls.add(Box.createVerticalStrut(10));
                controls.add(clear);

                content.add(controls, BorderLayout.EAST);
            }

            DialogButton confirmBtn = codex.component.dialog.Dialog.Default.BTN_OK.newInstance();
            confirmBtn.setEnabled(isEditable() && !propHolder.isInherited());
            DialogButton declineBtn = codex.component.dialog.Dialog.Default.BTN_CANCEL.newInstance();

            codex.component.dialog.Dialog dialog = new codex.component.dialog.Dialog(
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


    private class InsertAction implements ActionListener {

        private final DefaultTableModel tableModel;

        InsertAction(DefaultTableModel tableModel) {
            this.tableModel = tableModel;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            java.util.Map.Entry<PropertyHolder<ISerializableType<K, ? extends IMask<K>>, K>, PropertyHolder<ISerializableType<V, ? extends IMask<V>>, V>> entry = createHolderEntry();

            ParamModel model = new ParamModel();
            model.addProperty(entry.getKey());
            model.addProperty(entry.getValue());

            JPanel duplicateWarn = new JPanel(new BorderLayout()) {{
                JLabel label = new JLabel(Language.get(MapEditor.class, "warn@duplicate"), WARN_ICON, SwingConstants.LEFT);
                label.setOpaque(true);
                label.setBackground(new Color(0x33DE5347, true));
                label.setForeground(IEditor.COLOR_INVALID);
                label.setBorder(new CompoundBorder(
                        new LineBorder(Color.decode("#DE5347"), 1),
                        new EmptyBorder(5, 10, 5, 10)
                ));
                setBorder(new EmptyBorder(5, 10, 5, 10));
                add(label, BorderLayout.CENTER);
            }};

            DialogButton btnConfirm = codex.component.dialog.Dialog.Default.BTN_OK.newInstance(Language.get(MapEditor.class, "confirm@title"));
            DialogButton btnDecline = codex.component.dialog.Dialog.Default.BTN_CANCEL.newInstance();

            Dialog insert = new Dialog(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    ADD_ICON,
                    Language.get(MapEditor.class, "add@title"),
                    new JPanel(new BorderLayout()) {{
                        add(new EditorPage(model), BorderLayout.CENTER);
                        add(duplicateWarn, BorderLayout.SOUTH);
                    }},
                    event -> {
                        if (event.getID() == codex.component.dialog.Dialog.OK) {
                            K key = entry.getKey().getPropValue().getValue();
                            V val = entry.getValue().getPropValue().getValue();

                            if (!internalValue.containsKey(key)) {
                                tableModel.addRow(new Object[] {key, val});
                            } else {
                                for (int row = 0;  row < tableModel.getRowCount(); row++) {
                                    if (tableModel.getValueAt(row, 0).equals(key)) {
                                        tableModel.setValueAt(val, row, 1);
                                    }
                                }
                            }
                            internalValue.put(key, val);
                        }
                    },
                    btnConfirm,
                    btnDecline
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, ActionListener> defaultHandler = handler;
                    handler = (button) -> {
                        return (event) -> {
                            boolean keyValid = model.getEditor(entry.getKey().getName()).stopEditing() && entry.getKey().isValid();
                            boolean valValid = model.getEditor(entry.getValue().getName()).stopEditing() && entry.getValue().isValid();

                            if (event.getID() != Dialog.OK || (keyValid && valValid)) {
                                defaultHandler.apply(button).actionPerformed(event);
                            }
                        };
                    };
                }
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(550, super.getPreferredSize().height);
                }
            };

            Consumer<K> keyExistCheck = k -> {
                duplicateWarn.setVisible(internalValue.containsKey(k));
                btnConfirm.setEnabled(!internalValue.containsKey(k));
                insert.pack();
            };

            keyExistCheck.accept(entry.getKey().getPropValue().getValue());
            //noinspection unchecked
            entry.getKey().addChangeListener((name, oldValue, newValue) -> keyExistCheck.accept((K) newValue));

            insert.setVisible(true);
        }
    }

    private class DeleteAction implements ActionListener {

        private final DefaultTableModel tableModel;
        private final JTable table;

        DeleteAction(DefaultTableModel tableModel, JTable table) {
            this.tableModel = tableModel;
            this.table = table;
        }

        @Override
        @SuppressWarnings("SuspiciousMethodCalls")
        public void actionPerformed(ActionEvent e) {
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
        }
    }

    private class ClearAction implements ActionListener {

        private final DefaultTableModel tableModel;
        private final JTable table;

        ClearAction(DefaultTableModel tableModel, JTable table) {
            this.tableModel = tableModel;
            this.table = table;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            internalValue.clear();
            while (tableModel.getRowCount() > 0) {
                tableModel.removeRow(0);
            }
            table.getSelectionModel().clearSelection();
        }
    }


    private class CellEditor extends AbstractCellEditor implements TableCellEditor {

        private java.util.Map.Entry<? extends ISerializableType<K, ? extends IMask<K>>, ? extends ISerializableType<V, ? extends IMask<V>>> entry;
        private IEditor<? extends ISerializableType<V, ? extends IMask<V>>, V> editor;

        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            entry = propHolder.getPropValue().newEntry();

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
                JComponent renderedComp = (JComponent) table.getCellRenderer(row, column).getTableCellRendererComponent(
                        table, value, true, true, row, column
                );
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
