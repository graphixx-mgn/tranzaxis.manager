package codex.presentation;

import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;

public class SelectorTableModel extends DefaultTableModel implements /*Reorderable*/ IModelListener {
    
    private final List<Class<? extends IComplexType>> columnClasses = new LinkedList<>();
    
    public SelectorTableModel(Entity entity, final Entity prototype) {
        super(generateData(entity), generateHeader(entity, prototype));
        entity.childrenList().forEach((node) -> {
            ((Entity) node).model.addModelListener(this);
        });
        prototype.model.getProperties(Access.Select).forEach((propName) -> {
            columnClasses.add(prototype.model.getPropertyType(propName));
        });
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnClasses.get(columnIndex);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        int rowCount = getRowCount();
        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
            if (((Entity) getValueAt(rowIdx, 0)).model.equals(model)) {
                final int entityIdx = rowIdx;
                List<String> selectorProps = model.getProperties(Access.Select);
                selectorProps.forEach((propName) -> {
                    if (changes.contains(propName)) {
                        int propIdx = selectorProps.indexOf(propName);
                        setValueAt(model.getValue(propName), entityIdx, propIdx);
                    }
                });
                fireTableRowsUpdated(rowIdx, rowIdx);
                break;
            }
        }
    }
    
    private static Vector generateHeader(Entity entity, final Entity prototype) {
        Vector headerVector = new Vector();
        prototype.model.getProperties(Access.Select).forEach((propName) -> {
            headerVector.add(prototype.model.getPropertyTitle(propName));
        });
        return headerVector;
    }
    
    private static Vector generateData(Entity entity) {
        Vector dataVector = new Vector();
        entity.childrenList().forEach((node) -> {
            Vector rowVector = new Vector<>();
            Entity child = (Entity) node;
            child.model.getProperties(Access.Select).forEach((String propName) -> {
//                rowVector.add(propName.equals(EntityModel.PID) ? child : child.model.getValue(propName));
                rowVector.add(child.model.getValue(propName));
            });
            dataVector.addElement(rowVector);
        });
        return dataVector;
    }
    
}
