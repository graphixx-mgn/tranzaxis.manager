package codex.presentation;

import codex.component.editor.GeneralEditor;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.Language;
import org.netbeans.swing.outline.Outline;
import org.netbeans.swing.outline.RowModel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class SelectorTreeTable<T extends Entity> extends Outline implements IEditableTable {

    private final static Icon ICON_COLLAPSE = UIManager.getIcon("Tree.collapsedIcon");
    private final static Icon ICON_EXPAND   = UIManager.getIcon("Tree.expandedIcon");

    final List<ISelectorTableModel.ColumnInfo> columnModel = new LinkedList<>();
    private final List<String> editableProps = new LinkedList<>();

    public SelectorTreeTable(T rootEntity, Class<T> entityClass) {
        final NodeTreeModel treeModel = new NodeTreeModel(rootEntity);
        columnModel.addAll(rootEntity.model.getProperties(Access.Select).stream()
                .filter(propName -> !EntityModel.THIS.equals(propName))
                .map(propName -> new ISelectorTableModel.ColumnInfo(
                        rootEntity.model.getPropertyType(propName),
                        propName,
                        rootEntity.model.getPropertyTitle(propName)
                ))
                .collect(Collectors.toList())
        );

        final SelectorTreeTableModel treeTableModel = new SelectorTreeTableModel(
                treeModel,
                new TableRowModel(),
                true,
                Language.get(Entity.class, EntityModel.THIS+PropertyHolder.PROP_NAME_SUFFIX)
        );

        unsetQuickFilter();
        setRowSorter(null);
        setColumnHidingAllowed(false);
        setRowHeight(IEditor.FONT_VALUE.getSize() * 2);
        setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0,0));

        GeneralRenderer renderer = new GeneralRenderer<Entity>() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JComponent component = (JComponent) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (column == 0 && row != TableModelEvent.HEADER_ROW && value instanceof INode) {
                    INode    node = (INode) value;
                    TreePath path = new TreePath(treeModel.getPathToRoot(node));
                    boolean  expanded = treeTableModel.getTreePathSupport().isExpanded(path);

                    JPanel wrapper = new JPanel(new BorderLayout()) {{
                        setOpaque(true);
                        setBackground(component.getBackground());
                        add(new Box(BoxLayout.LINE_AXIS) {{
                            add(Box.createHorizontalStrut(
                                    IEditor.FONT_VALUE.getSize() * (path.getPathCount() - (isRootVisible() ? 1 : 2)))
                            );
                            if (!node.isLeaf()) {
                                add(new JLabel(expanded ? ICON_EXPAND : ICON_COLLAPSE) {{
                                    setBorder(new EmptyBorder(0, 0, 0, getRowHeight() / 6));
                                }});
                            } else {
                                add(Box.createHorizontalStrut(ICON_EXPAND.getIconWidth() + (getRowHeight() / 6)));
                            }
                            add(component);
                        }});
                    }};
                    wrapper.setBorder(component.getBorder());
                    component.setBorder(null);
                    return wrapper;
                }
                return component;
            }
        };
        setDefaultRenderer(Object.class, renderer);
        setDefaultRenderer(Bool.class, renderer);
        setDefaultRenderer(IComplexType.class, renderer);
        getTableHeader().setDefaultRenderer(renderer);

        GeneralEditor editor = new GeneralEditor();
        setDefaultEditor(Bool.class, editor);
        setDefaultEditor(IComplexType.class, editor);

        setModel(treeTableModel);
    }

    @Override
    public void setPropertiesEditable(String... propNames) {
        if (propNames != null) {
            editableProps.addAll(Arrays.asList(propNames));
        }
    }

    @Override
    public void setPropertyEditable(String propName) {
        editableProps.add(propName);
    }

    private class TableRowModel implements RowModel {

        @Override
        public Class getColumnClass(int column) {
            return columnModel.get(column).type == Bool.class ? Bool.class : IComplexType.class;
        }
        @Override
        public int getColumnCount() {
            return columnModel.size();
        }

        @Override
        public String getColumnName(int column) {
            return columnModel.get(column).title;
        }

        @Override
        public Object getValueFor(Object node, int column) {
            Entity entity = (Entity) node;
            String propName = columnModel.get(column).name;
            return entity.model.getUnsavedValue(propName);
        }

        @Override
        public boolean isCellEditable(Object node, int column) {
            return editableProps.contains(((ISelectorTableModel) getModel()).getPropertyForColumn(column+1));
        }

        @Override
        public void setValueFor(Object node, int column, Object value) {
            Entity entity = (Entity) node;
            String propName = columnModel.get(column).name;
            entity.model.setValue(propName, value);
        }
    }

}
