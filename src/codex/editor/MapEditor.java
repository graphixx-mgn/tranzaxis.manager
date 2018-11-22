package codex.editor;

import codex.component.render.GeneralRenderer;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.Map;
import codex.type.IComplexType;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.ItemSelectable;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.ParameterizedType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import javax.swing.AbstractCellEditor;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;


public class MapEditor extends AbstractEditor {
    
    private JTable table;

    public MapEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(Color.GREEN);
        
        Class kClass = ((Map) propHolder.getPropValue()).getKeyClass();
        Class vClass = ((Map) propHolder.getPropValue()).getValClass();

        DefaultTableModel tableModel = new DefaultTableModel(ArrStr.parse(propHolder.getPlaceholder()).toArray(), 0) {
            private final Class keyInternalType = (Class) ((ParameterizedType) kClass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
            private final Class valInternalType = (Class) ((ParameterizedType) vClass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                Class  c = columnIndex == 0 ? kClass : vClass;
                return c == Bool.class ? Bool.class : IComplexType.class;
            }
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex == 1;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                if (
                    (table.getModel().getValueAt(row, 1) == null && value == null) || 
                    (table.getModel().getValueAt(row, 1) != null && table.getModel().getValueAt(row, 1).equals(value))
                ) {
                    return;
                }
                super.setValueAt(value, row, column);
                
                java.util.Map<IComplexType, IComplexType> newMap = new LinkedHashMap();
                int rows = table.getModel().getRowCount();
                for (int i=0; i<rows; i++) {
                    Object primitiveKey = table.getModel().getValueAt(i, 0);
                    Object primitiveVal = table.getModel().getValueAt(i, 1);
                    
                    try {
                        IComplexType complexKey = (IComplexType) kClass.getConstructor(new Class[] {keyInternalType}).newInstance(new Object[] {primitiveKey});
                        IComplexType complexVal = (IComplexType) vClass.getConstructor(new Class[] {valInternalType}).newInstance(new Object[] {primitiveVal});
                        
                        newMap.put(complexKey, complexVal);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                propHolder.setValue(new Map<>(kClass, vClass, newMap));
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0,0));
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                table.getSelectionModel().setSelectionInterval(-1, -1);
            }
        });

        GeneralRenderer renderer = new GeneralRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, 
                        value, 
                        false,
                        hasFocus, 
                        row, 
                        column
                );
                return c;
            }
            
        };
        table.setDefaultRenderer(Bool.class,         renderer);
        table.setDefaultRenderer(IComplexType.class, renderer);
        table.getTableHeader().setDefaultRenderer(renderer);
        
        table.getColumnModel().getColumn(1).setCellEditor(new CellEditor(vClass));
        
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(table);
        scrollPane.setBorder(new MatteBorder(1, 1, 0, 1, Color.GRAY));
        
        container.add(scrollPane);
        return container;
    }

    @Override
    public void setValue(Object value) {
        int rowCount = table.getModel().getRowCount();
        for (int i=0; i<rowCount;i++) {
            ((DefaultTableModel) table.getModel()).removeRow(0);
        }
        if (value != null) {
            ((java.util.Map<IComplexType, IComplexType>) value).entrySet().forEach((entry) -> {
                Object[] row = new Object[] {entry.getKey().getValue(), entry.getValue().getValue()};
                ((DefaultTableModel) table.getModel()).addRow(row);
            });
        }
        table.setPreferredScrollableViewportSize(table.getPreferredSize());
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        table.setEnabled(editable);
    }
    
    private class CellEditor extends AbstractCellEditor implements TableCellEditor {

        private final Class valueComplexType;
        private final Class valueInternalType;
        private final java.util.Map<Integer, AbstractEditor> propEditors = new HashMap<>();
        
        CellEditor(Class valueComplexType) {            
            this.valueComplexType = valueComplexType;
            valueInternalType = (Class) ((ParameterizedType) valueComplexType.getGenericInterfaces()[0]).getActualTypeArguments()[0];
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            try {
                if (!propEditors.containsKey(row)) {
                    IComplexType complexVal;
                    if (valueComplexType == codex.type.Enum.class) {
                        complexVal = (IComplexType) valueComplexType.getConstructor(new Class[] {valueInternalType}).newInstance(
                                new Object[] {(Enum) EnumSet.allOf((Class<Enum>) value.getClass()).iterator().next()}
                        );
                    } else {
                        complexVal = (IComplexType) valueComplexType.getConstructor(new Class[] {valueInternalType}).newInstance(new Object[] {null});
                    }
                    propEditors.put(
                            row, 
                            (AbstractEditor) complexVal.editorFactory().newInstance(new PropertyHolder(
                                "row#"+row,
                                complexVal,
                                true
                            ))
                    );
                    if (ItemSelectable.class.isAssignableFrom(propEditors.get(row).getFocusTarget().getClass())) {
                        propEditors.get(row).propHolder.addChangeListener((name, oldValue, newValue) -> {
                            fireEditingStopped();
                        });
                    }
                }
                propEditors.get(row).propHolder.setValue(value);
                
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        propEditors.get(row).getFocusTarget().requestFocus();
                    }
                });
                
                if (valueComplexType == Bool.class) {
                    JComponent renderedComp = (JComponent) table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, true, true, row, column);

                    JPanel container = new JPanel();
                    container.setOpaque(true);
                    container.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 2));
                    container.setBackground(renderedComp.getBackground());
                    container.setBorder(renderedComp.getBorder());
                    container.add(propEditors.get(row).getEditor());
                    return container;
                } else {
                    return propEditors.get(row).getEditor();
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        
        @Override
        public Object getCellEditorValue() {
            propEditors.get(table.getEditingRow()).stopEditing();
            return propEditors.get(table.getEditingRow()).propHolder.getPropValue().getValue();
        }
    
    }
    
}
