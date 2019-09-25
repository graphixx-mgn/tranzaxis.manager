package codex.presentation;

import codex.model.Entity;
import codex.type.IComplexType;

public interface ISelectorTableModel {

    void   addEntity(Entity entity);
    Entity getEntityForRow(int row);
    String getPropertyForColumn(int column);

    class ColumnInfo {
        String name;
        String title;
        Class<? extends IComplexType> type;

        ColumnInfo(Class<? extends IComplexType> type, String name, String title) {
            this.type  = type;
            this.name  = name;
            this.title = title;
        }
    }
}
