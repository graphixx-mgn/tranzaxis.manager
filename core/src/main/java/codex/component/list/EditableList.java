package codex.component.list;

import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.editor.ArrStrEditor;
import codex.type.IComplexType;
import net.jcip.annotations.ThreadSafe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;

/**
 * Редактируемый список строк, используется в качестве элемента редактора
 * {@link ArrStrEditor}.
 */
@ThreadSafe
public final class EditableList extends JPanel {
    
    private final JTable            table;
    private final Consumer<String>  insert;
    private final Consumer<Integer> delete;
    private final JTextField        cellEditor;
    private boolean                 editable = true;
    private final DefaultTableModel model = new DefaultTableModel() {
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return editable;
        }
        
    };
    
    /**
     * Конструктор экземпляра виджета.
     * @param values Список строк для редактирования в виджете.
     */
    public EditableList(List<String> values) {
        super(new BorderLayout());
        
        if (values == null) {
            throw new IllegalStateException("Invalid value: NULL value is not supported");
        } 
        setBorder(BorderFactory.createEmptyBorder());
        
        Vector dataVector = new Vector();
        values.forEach((String item) -> {
            Vector v = new Vector<>();
            v.add(item);
            dataVector.addElement(v);
        });

        model.setDataVector(dataVector, new Vector<>(Collections.singletonList("")));
        model.addTableModelListener((TableModelEvent event) -> {
            if (event.getFirstRow() != TableModelEvent.HEADER_ROW) {
                switch (event.getType()) {
                    case TableModelEvent.UPDATE:
                        values.set(event.getFirstRow(), (String) model.getValueAt(event.getFirstRow(), event.getColumn()));
                        break;
                    case TableModelEvent.INSERT:
                        values.add("");
                        break;
                    case TableModelEvent.DELETE:
                        values.remove(event.getFirstRow());
                }
            }
        });
        
        cellEditor = new JTextField();
        cellEditor.setFont(IEditor.FONT_VALUE);
        cellEditor.setBorder(new CompoundBorder(
                IEditor.BORDER_ACTIVE, 
                new EmptyBorder(4, 4, 4, 4)
        ));
        
        table = new JTable(model);
        table.setTableHeader(null);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0,0));
        table.setRowHeight(cellEditor.getPreferredSize().height + 2);
        table.setDefaultRenderer(String.class, new GeneralRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultEditor(String.class, new DefaultCellEditor(cellEditor));
        table.putClientProperty("terminateEditOnFocusLost", true);
        
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        table.getActionMap().put("Enter", null);
        
        JScrollPane scrollPanel = new JScrollPane(table);
        scrollPanel.getViewport().setBackground(Color.WHITE);
        scrollPanel.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        scrollPanel.setColumnHeader(null);
        add(scrollPanel);
        
        insert = (value) -> {
            synchronized (model) {
                model.addRow(new String[]{IComplexType.coalesce(value, "")});
            }
        };
        delete = (index) -> {
            if (index == TableModelEvent.HEADER_ROW) return;
            synchronized (model) {
                model.removeRow(index);
                if (index > 0) {
                    table.getSelectionModel().setLeadSelectionIndex(index - 1);
                } else if (table.getRowCount() > 0) {
                    table.getSelectionModel().setLeadSelectionIndex(0);
                }
            }
        };
    }

    /**
     * Получить индекс выделенного элемента списка. Если ни одного элемента не 
     * выделено вернется значение "-1".
     * @return Индекс строки в списке.
     */
    public int getSelectedItem() {
        return table.getSelectedRow();
    }
    
    /**
     * Вставить строку в конец списка.
     * @param value Добавляемая строка.
     */
    public void insertItem(String value) {
        insert.accept(value);
    }
    
    /**
     * Удалить строку из списка по её индеку.
     * @param index Индекс строки в списке. 
     */
    public void deleteItem(int index) {
        delete.accept(index);
    }
    
    /**
     * Удалить выделенный элемент списка. Если ни одного элемента не выделено,
     * список останется без изменений.
     */
    public void deleteSelectedItem() {
        delete.accept(getSelectedItem());
    }

    /**
     * Добавить слушателя на событие выделения элемента списка.
     * @param listener Слушатель.
     */
    public void addSelectionListener(ListSelectionListener listener) {
        // ListSelectionEvent возвращает странные значения индекса выделенного элемента.
        table.getSelectionModel().addListSelectionListener((ListSelectionEvent event) -> {
            if (!event.getValueIsAdjusting()) {
                int selected = table.getSelectedRow();
                ListSelectionEvent newEvent = new ListSelectionEvent(
                        event.getSource(), selected, selected, event.getValueIsAdjusting()
                );
                listener.valueChanged(newEvent);
            }
        });
    }
    
    /**
     * Переключения возможности редактирования списка пользователем.
     * @param editable TRUE - если редактирование разрешено, инае - запрещено.
     */
    public void setEditable(boolean editable) {
        SwingUtilities.invokeLater(() -> this.editable = editable);
    }
    
    /**
     * Возвращает признак того что список в данный момент редактируется.
     */
    public boolean isEditing() {
        return table.isEditing();
    }
    
    /**
     * Прерывание редактирования списка.
     */
    public void stopEditing() {
        if (table.isEditing()) {
            CellEditor editor = table.getCellEditor();
            if (editor != null) {
                SwingUtilities.invokeLater(() -> {
                    if (editor.getCellEditorValue() != null) {
                        editor.stopCellEditing();
                    } else {
                        editor.cancelCellEditing();
                    }
                });
            }
        }
    } 
}
