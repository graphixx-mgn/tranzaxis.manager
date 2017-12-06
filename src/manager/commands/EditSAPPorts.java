package manager.commands;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.model.Entity;
import codex.presentation.SelectorTable;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import javax.swing.FocusManager;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import manager.nodes.Database;
import static manager.nodes.Database.DAS;


public class EditSAPPorts extends EntityCommand {
    
    private final Dialog dialog;
    
    public EditSAPPorts() {
        super(
                "remap", Language.get(Database.class.getSimpleName(), "command@remap"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/saps.png"), 28, 28), 
                Language.get(Database.class.getSimpleName(), "command@remap"), 
                (context) -> {
                    return 
                            context.model.getValue("instanceId") != null && 
                            ((Database) context).getConnectionID() != null;
                }
        );
        dialog = new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                getIcon(), 
                toString(),
                new JPanel(),
                (event) -> {},
                Dialog.Default.BTN_OK,
                Dialog.Default.BTN_CANCEL
        );
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> params) {
        Integer connId = ((Database) entity).getConnectionID();
        if (connId != null && entity.model.getValue("instanceId") != null) {
            try {
                ResultSet rset = DAS.select(
                    connId, 
                    "SELECT U.ID, U.TITLE, S.ADDRESS FROM RDX_UNIT U JOIN RDX_SAP S ON S.SYSTEMUNITID = U.ID WHERE U.INSTANCEID = ?",
                    ((List<String>) entity.model.getUnsavedValue("instanceId")).get(0)
                );
                ResultSetMetaData meta = rset.getMetaData();
                int colomnCount = meta.getColumnCount();
                Vector<String> columns = new Vector<>();
                for (int colIdx = 1; colIdx <= colomnCount; colIdx++) {
                    columns.add(meta.getColumnName(colIdx));
                }
                DefaultTableModel tableModel = new DefaultTableModel(null, columns) {
                    @Override
                    public Class<?> getColumnClass(int columnIndex) {
                        return String.class;
                    }

                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return column == 2;
                    }
                };
                
                JTable table = new SelectorTable(tableModel);

                table.setDefaultRenderer(String.class, new GeneralRenderer());
                table.getTableHeader().setDefaultRenderer(new GeneralRenderer());

                final JScrollPane scrollPane = new JScrollPane();
                scrollPane.getViewport().setBackground(Color.WHITE);
                scrollPane.setViewportView(table);
                scrollPane.setBorder(new CompoundBorder(
                        new EmptyBorder(5, 5, 5, 5), 
                        new MatteBorder(1, 1, 1, 1, Color.GRAY)
                ));
                
                JPanel content = new JPanel(new BorderLayout());
                content.add(scrollPane, BorderLayout.CENTER);
                
                while (rset.next()) {
                    Vector<String> row = new Vector<>();
                    for (int colIdx = 1; colIdx <= colomnCount; colIdx++) {
                        row.add(rset.getString(colIdx));
                    }
                    tableModel.addRow(row);
                }
                dialog.setContent(content);
                dialog.setMinimumSize(new Dimension(
                        table.getColumnCount() * 150,
                        200
                ));
                dialog.setPreferredSize(new Dimension(
                        table.getColumnCount() * 200, 
                        400
                ));
                dialog.setVisible(true);
            } catch (SQLException e) {}
        }
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }
    
}
