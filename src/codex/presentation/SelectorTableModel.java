package codex.presentation;

import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class SelectorTableModel extends DefaultTableModel implements IModelListener {
    
    private final List<Class<? extends IComplexType>> columnClasses = new LinkedList<>();
    private final Entity entity;
    
    public SelectorTableModel(Entity entity, final Entity prototype) {
        super(generateData(entity), generateHeader(entity, prototype));
        entity.childrenList().forEach((node) -> {
            EntityModel childModel = ((Entity) node).model;
            childModel.addModelListener(this);
            childModel.addChangeListener((name, oldValue, newValue) -> {
                if (childModel.isPropertyDynamic(name)) {
                    final int entityIdx = entity.getIndex(node);
                    int propIdx = childModel.getProperties(Access.Select).indexOf(name);
                    setValueAt(newValue, entityIdx, propIdx);
                }
            });
        });
        prototype.model.getProperties(Access.Select).forEach((propName) -> {
            columnClasses.add(prototype.model.getPropertyType(propName));
        });
        this.entity = entity;
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnClasses.get(columnIndex);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
    
    public final Entity getEntityAt(int row) {
        return (Entity) entity.childrenList().get(row);
    }

    @Override
    public void moveRow(int start, int end, int to) {
        super.moveRow(start, end, to);
        entity.move(getEntityAt(start), to);
        SwingUtilities.invokeLater(() -> {
            entity.childrenList().forEach((node) -> {
                ((Entity) node).model.setValue(EntityModel.SEQ, (entity.childrenList().indexOf(node)+1));
                ((Entity) node).model.saveValue(EntityModel.SEQ);
            });
        });
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        int rowCount = getRowCount();
        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
            if (getEntityAt(rowIdx).model.equals(model)) {
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
                rowVector.add(child.model.getValue(propName));
            });
            dataVector.addElement(rowVector);
        });
        return dataVector;
    }
    
}
