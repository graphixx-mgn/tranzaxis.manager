package codex.presentation;

import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.type.Bool;
import codex.type.IComplexType;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class SelectorTableModel extends DefaultTableModel implements IModelListener, ISelectorTableModel {

    private final List<Class<? extends IComplexType>> columnClasses = new LinkedList<>();
    private final Entity entity;
    private final Entity prototype;
    
    public SelectorTableModel(Entity entity, final Entity prototype) {
        super(generateData(entity), generateHeader(entity, prototype));
        entity.childrenList().forEach((node) -> {
            Entity      childEntity = (Entity) node;
            EntityModel childModel  = childEntity.model;
            
            childEntity.addNodeListener(new INodeListener() {
                @Override
                public void childChanged(INode node) {
                    int rowCount = getRowCount();
                    for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                        if (getEntityForRow(rowIdx).model.equals(childModel)) {
                            fireTableRowsUpdated(rowIdx, rowIdx);
                            break;
                        }
                    }
                }
            });
            childModel.addModelListener(this);
            childModel.addChangeListener((name, oldValue, newValue) -> {
                if (childModel.isPropertyDynamic(name)) {
                    final int entityIdx = entity.getIndex(node);
                    if (childModel.getProperties(Access.Select).contains(name)) {
                        setValueAt(newValue, entityIdx, childModel.getProperties(Access.Select).indexOf(name));
                    }
                }
            });
        });
        prototype.model.getProperties(Access.Select).forEach((propName) -> {
            columnClasses.add(prototype.model.getPropertyType(propName));
        });
        this.entity    = entity;
        this.prototype = prototype;
    }

    @Override
    public Entity getEntityForRow(int row) {
        return (Entity) entity.childrenList().get(row);
    }

    @Override
    public String getPropertyForColumn(int column) {
        return prototype.model.getProperties(Access.Select).get(column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnClasses.get(columnIndex) == Bool.class ? Bool.class : IComplexType.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public void moveRow(int start, int end, int to) {
        super.moveRow(start, end, to);
        entity.move(getEntityForRow(start), to);
        
        SwingUtilities.invokeLater(() -> {
            List<Integer> sequences = entity.childrenList().stream()
                .map((childNode) -> {
                    return ((Entity) childNode).getSEQ();
                })
                .collect(Collectors.toList());
            Collections.sort(sequences);
            Iterator<Integer> seqIterator = sequences.iterator();
            
            entity.childrenList().forEach((childNode) -> {
                Entity childEntity = (Entity) childNode;
                childEntity.setSEQ(seqIterator.next());
                if (!childEntity.model.getChanges().isEmpty()) {
                    try {
                        childEntity.model.commit(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        });
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        int rowCount = getRowCount();
        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
            if (getEntityForRow(rowIdx).model.equals(model)) {
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
