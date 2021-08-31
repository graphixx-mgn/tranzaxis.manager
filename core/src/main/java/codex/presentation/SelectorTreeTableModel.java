package codex.presentation;

import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.RowModel;
import javax.swing.tree.TreeModel;
import java.util.List;
import java.util.stream.Collectors;

public class SelectorTreeTableModel extends DefaultOutlineModel implements ISelectorTableModel {

    private final List<String> propList;

    SelectorTreeTableModel(TreeModel treeModel, RowModel rowModel, boolean largeModel, String nodesColumnLabel) {
        super(treeModel, rowModel, largeModel, nodesColumnLabel);
        this.propList = ((Entity) getRoot()).model.getProperties(Access.Select).stream()
                .filter(propName -> !EntityModel.THIS.equals(propName))
                .collect(Collectors.toList());
    }

    @Override
    public Entity getEntityForRow(int row) {
        return (Entity) getValueAt(row, 0);
    }

    @Override
    public String getPropertyForColumn(int column) {
        return column == 0 ? EntityModel.THIS : propList.get(column-1);
    }
}
