package codex.presentation;

import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.*;
import codex.type.Bool;
import codex.type.IComplexType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class SelectorTableModel extends DefaultTableModel implements IModelListener, ISelectorTableModel {

    private final List<ColumnInfo> columnModel = new LinkedList<>();
    private final Entity rootEntity;
    
    public SelectorTableModel(Entity rootEntity) {
        super();
        this.rootEntity = rootEntity;
        rootEntity.childrenList().forEach((node) -> addEntity((Entity) node));
    }

    @Override
    public void addEntity(Entity entity) {
        if (getColumnCount() == 0) {
            List<String> visibleProps = getVisibleProperties(entity);
            columnModel.addAll(visibleProps.stream()
                    .map(propName -> new ColumnInfo(
                        entity.model.getPropertyType(propName),
                        propName,
                        entity.model.getPropertyTitle(propName)
                    ))
                    .collect(Collectors.toList())
            );
            columnModel.forEach(columnInfo -> addColumn(columnInfo.title));
        }
        addRow(columnModel.stream().map(columnInfo -> entity.model.getValue(columnInfo.name)).toArray());

        EntityModel childModel  = entity.model;
        childModel.addModelListener(this);
        childModel.addChangeListener((name, oldValue, newValue) -> {
            if (childModel.isPropertyDynamic(name) && findColumn(name) > 0) {
                setValueAt(newValue, rootEntity.getIndex(entity), findColumn(name));
            }
        });

        entity.addNodeListener(new INodeListener() {
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
    public int findColumn(String columnName) {
        for (int i = 0; i < getColumnCount(); i++) {
            if (getPropertyForColumn(i).equals(columnName)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> getVisibleProperties(Entity entity) {
        List<String> accessibleProps = entity.model.getProperties(Access.Select);
        return rootEntity.getChildClass().isAnnotationPresent(ClassCatalog.Definition.class) ?
                Stream.concat(
                    Stream.concat(
                            Stream.of(EntityModel.THIS),
                            EntityModel.SYSPROPS.stream()
                    ).filter(accessibleProps::contains),
                    Arrays.stream(rootEntity.getChildClass().getAnnotation(ClassCatalog.Definition.class).selectorProps())
                ).collect(Collectors.toList()) :
                accessibleProps;
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
                changes.forEach(propName -> {
                    if (findColumn(propName) > 0) {
                        setValueAt(model.getValue(propName), entityIdx, findColumn(propName));
                    }
                });
                break;
            }
        }
    }

}
