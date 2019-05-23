package codex.presentation;

import codex.model.Entity;

public interface ISelectorTableModel {

    Entity getEntityForRow(int row);
    String getPropertyForColumn(int column);

}
