package codex.presentation;

import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.*;
import codex.property.IPropertyChangeListener;
import codex.type.BigInt;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.type.Int;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.table.DefaultTableModel;

public abstract class SelectorTableModel extends DefaultTableModel implements INodeListener, IModelListener, ISelectorTableModel {

    private final List<ColumnInfo> columnModel = new LinkedList<>();
    private final Entity rootEntity;
    
    public SelectorTableModel(Entity rootEntity) {
        super();
        this.rootEntity = rootEntity;
        this.rootEntity.addNodeListener(this);
        synchronized (rootEntity) {
            rootEntity.childrenList().forEach((node) -> {
                childInserted(rootEntity, node);
            });
        }
    }

    @Override
    public void childInserted(INode parentNode, INode childNode) {
        synchronized (rootEntity) {
            Entity entity = (Entity) childNode;
            if (getColumnCount() == 0) {
                final List<String> visibleProps = getVisibleProperties(entity);
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
            new UpdateController(rootEntity, entity);
        }
    }

    @Override
    public void childMoved(INode parentNode, INode childNode, int from, int to) {
        synchronized (rootEntity) {
            moveRow(from, from, to);
        }
    }

    @Override
    public void childReplaced(INode prevChild, INode nextChild) {
        UpdateController controller = new UpdateController(rootEntity, (Entity) nextChild);
        EntityModel      childModel = ((Entity) nextChild).model;
        childModel.getProperties(Access.Any).forEach(propName -> controller.propertyChange(propName, null, childModel.getValue(propName)));
    }

    @Override
    public void childDeleted(INode parentNode, INode childNode, int index) {
        synchronized (rootEntity) {
            removeRow(index);
        }
    }

    protected abstract int getRowForIndex(int index);
    private  int getRowForNode(INode node) {
        synchronized (rootEntity) {
            final int idx = rootEntity.getIndex(node);
            return idx < 0 ? -1 : getRowForIndex(idx);
        }
    }

    @Override
    public final Entity getEntityForRow(int row) {
        return (Entity) rootEntity.childrenList().get(row);
    }

    @Override
    public final String getPropertyForColumn(int column) {
        return columnModel.get(column).name;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        if (columnModel.get(column).type == Bool.class) {
            return Bool.class;
        } else if (columnModel.get(column).type == Int.class || columnModel.get(column).type == BigInt.class) {
            return Long.class;
        } else {
            return IComplexType.class;
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public final int findColumn(String columnName) {
        for (int i = 0; i < getColumnCount(); i++) {
            if (getPropertyForColumn(i).equals(columnName)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> getVisibleProperties(Entity entity) {
        final List<String> accessibleProps = entity.model.getProperties(Access.Select);
        return  PolyMorph.class.isAssignableFrom(rootEntity.getChildClass()) ?
                Stream.concat(
                        Stream.concat(
                                Stream.of(EntityModel.THIS),
                                EntityModel.SYSPROPS.stream()
                        ).filter(accessibleProps::contains),
                        PolyMorph.getDatabaseProps(entity.model).stream().filter(accessibleProps::contains)
                ).collect(Collectors.toList()) :
                accessibleProps;
    }

    @Override
    public void moveRow(int start, int end, int to) {
        synchronized (rootEntity) {
            super.moveRow(start, end, to);
            if (rootEntity.allowModifyChild()) {
                final Iterator<Integer> generator = Stream
                        .iterate(1, n -> n + 1)
                        .limit(rootEntity.getChildCount())
                        .collect(Collectors.toList())
                        .iterator();
                rootEntity.childrenList().forEach((childNode) -> {
                    final Entity childEntity = (Entity) childNode;
                    childEntity.setSEQ(generator.next());
                    if (!childEntity.model.getChanges().isEmpty()) {
                        try {
                            childEntity.model.commit(true);
                        } catch (Exception ignore) {}
                    }
                });
            }
        }
    }


    private class UpdateController implements IPropertyChangeListener, INodeListener, IModelListener {

        private final Entity entity;

        private UpdateController(Entity parentEntity, Entity childEntity) {
            this.entity  = childEntity;

            this.entity.addNodeListener(this);
            this.entity.model.addChangeListener(this);
            this.entity.model.addModelListener(this);

            parentEntity.addNodeListener(this);
        }

        @Override
        public void propertyChange(String name, Object oldValue, Object newValue) {
            synchronized (rootEntity) {
                if (entity.model.isPropertyDynamic(name) && findColumn(name) >= 0 && entity.getParent() != null) {
                    setValueAt(newValue, rootEntity.getIndex(entity), findColumn(name));
                }
            }
        }

        @Override
        public void modelSaved(EntityModel model, List<String> changes) {
            synchronized (rootEntity) {
                changes.forEach(name -> {
                    if (findColumn(name) >= 0 && entity.getParent() != null) {
                        setValueAt(model.getValue(name), rootEntity.getIndex(entity), findColumn(name));
                    }
                });
            }
        }

        @Override
        public void childDeleted(INode parentNode, INode childNode, int index) {
            synchronized (rootEntity) {
                if (childNode == entity) {
                    this.entity.removeNodeListener(this);
                    this.entity.model.removeChangeListener(this);
                    this.entity.model.removeModelListener(this);

                    parentNode.removeNodeListener(this);
                }
            }
        }

        @Override
        public void childChanged(INode node) {
            if (node == entity ) {
                synchronized (rootEntity) {
                    final int row = getRowForNode(node);
                    if (row >= 0) {
                        fireTableRowsUpdated(row, row);
                    }
                }
            }
        }
    }
}
