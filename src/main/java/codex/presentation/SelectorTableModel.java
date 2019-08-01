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
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class SelectorTableModel extends DefaultTableModel implements IModelListener, ISelectorTableModel {

    private final List<ColumnInfo> columnModel = new LinkedList<>();
    private final Entity rootEntity;
    
    public SelectorTableModel(Entity rootEntity) {
        super();
        this.rootEntity = rootEntity;
        rootEntity.childrenList().forEach((node) -> {
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
                    final int entityIdx = rootEntity.getIndex(node);
                    if (childModel.getProperties(Access.Select).contains(name)) {
                        setValueAt(newValue, entityIdx, childModel.getProperties(Access.Select).indexOf(name));
                    }
                }
            });
            addEntity(childEntity);
        });
    }

    @Override
    public void addEntity(Entity entity) {
        if (getColumnCount() == 0) {
            columnModel.addAll(entity.model.getProperties(Access.Select).stream()
                    .map(propName -> new ColumnInfo(
                        entity.model.getPropertyType(propName),
                        propName,
                        entity.model.getPropertyTitle(propName)
                    ))
                    .collect(Collectors.toList())
            );
            columnModel.forEach(columnInfo -> addColumn(columnInfo.title));
        }
        addRow(entity.model.getProperties(Access.Select).stream().map(entity.model::getValue).toArray());
    }

    @Override
    public Entity getEntityForRow(int row) {
        return (Entity) rootEntity.childrenList().get(row);
    }

    @Override
    public String getPropertyForColumn(int column) {
        return columnModel.get(column).name;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columnModel.get(column).type == Bool.class ? Bool.class : IComplexType.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public void moveRow(int start, int end, int to) {
        super.moveRow(start, end, to);
        rootEntity.move(getEntityForRow(start), to);
        
        SwingUtilities.invokeLater(() -> {
            List<Integer> sequences = rootEntity.childrenList().stream()
                .map((childNode) -> ((Entity) childNode).getSEQ())
                .collect(Collectors.toList());
            Collections.sort(sequences);
            Iterator<Integer> seqIterator = sequences.iterator();

            rootEntity.childrenList().forEach((childNode) -> {
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

}
