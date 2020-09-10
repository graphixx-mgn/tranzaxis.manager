package spacemgr.command.objects;

import codex.component.render.GeneralRenderer;
import codex.component.ui.StripedProgressBarUI;
import codex.database.IDatabaseAccessService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Database;
import spacemgr.TableSpaceManager;
import spacemgr.command.defragment.Defragmentation;
import spacemgr.command.resize.ResizeDataFile;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.sql.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TableSpace extends Catalog {

    private final static ImageIcon IMAGE_TBS = ImageUtils.getByPath("/images/database.png");

    static {
        CommandRegistry.getInstance().registerCommand(Defragmentation.class);
        CommandRegistry.getInstance().registerCommand(ResizeDataFile.class);
    }

    public static TableCellRenderer getSizeRenderer() {
        return new GeneralRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if (value != null) {
                    value = FileUtils.formatFileSize((long) value);
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        };
    }

    public static TableCellRenderer getPctRenderer() {
        return new GeneralRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column);
                if (value != null) {
                    JProgressBar bar = new JProgressBar(0, 100);
                    bar.setValue((int) value);
                    bar.setUI(new StripedProgressBarUI(true));
                    bar.setStringPainted(true);
                    bar.setBackground(c.getBackground());
                    bar.setBorder(new CompoundBorder(
                            new MatteBorder(0, 0, 1, column == table.getColumnCount()-1 ? 0 : 1, Color.LIGHT_GRAY),
                            new EmptyBorder(2, 2, 2, 2)
                    ));
                    return bar;
                }
                return super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column);
            }
        };
    }

    // Selector properties
    private final static String PROP_USED = "used";
    private final static String PROP_FREE = "free";
    private final static String PROP_SIZE = "size";
    private final static String PROP_UPCT = "pct";

    // Editor properties
    private final static String PROP_BLOCKS     = "blocks";
    private final static String PROP_BLOCK_SIZE = "blockSize";

    private Database database;
    private int blockSize = 0;

    public TableSpace(EntityRef owner, String title) {
        super(owner, IMAGE_TBS, title, null);

        // Selector properties
        model.addDynamicProp(PROP_UPCT, new Int(null),    Access.Edit, null);
        model.addDynamicProp(PROP_SIZE, new BigInt(null), Access.Edit, null);
        model.addDynamicProp(PROP_USED, new BigInt(null), Access.Edit, null);
        model.addDynamicProp(PROP_FREE, new BigInt(null), Access.Edit, null);

        // Editor properties
        model.addDynamicProp(PROP_BLOCK_SIZE, new Int(null), Access.Select, () -> blockSize);
        model.addDynamicProp(PROP_BLOCKS,     new BigInt(null), Access.Select, this::getBlocks);
    }

    public final void setDatabase(Database database) {
        if (this.database == null) {
            this.database = database;
        }
    }

    public void updateInfo() {
        loadUsageInfo();
        if (getChildCount() == 0) {
            loadDataFiles();
        }
    }

    private void loadDataFiles() {
        final String query = "SELECT FILE_NAME FROM DBA_DATA_FILES WHERE TABLESPACE_NAME = ?";
        try (final ResultSet resultSet = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class).select(
                database.getConnectionID(false),
                query,
                TableSpace.this.getPID()
        )) {
            while (resultSet.next()) {
                attach(Entity.newInstance(DataFile.class, this.toRef(), resultSet.getString("FILE_NAME")));
            }
        } catch (SQLException e) {
            Logger.getContextLogger(TableSpaceManager.class).error("Database error", e);
        }
    }

    private void loadUsageInfo() {
        final String query = Language.get(TableSpace.class, "query@usage", Language.DEF_LOCALE);
        try (final ResultSet resultSet = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class).select(
                database.getConnectionID(false),
                query,
                TableSpace.this.getPID()
        )) {
            if (resultSet.next()) {
                long used = resultSet.getLong("USED");
                long free = resultSet.getLong("FREE");
                long size = resultSet.getLong("BYTES");
                blockSize = resultSet.getInt( "BLOCK_SIZE");

                TableSpace.this.model.setValue(PROP_SIZE, size);
                TableSpace.this.model.setValue(PROP_USED, used);
                TableSpace.this.model.setValue(PROP_FREE, free);
                TableSpace.this.model.setValue(PROP_UPCT, (int) (100 * used / size));
            }
        } catch (SQLException e) {
            Logger.getContextLogger(TableSpaceManager.class).error("Database error", e);
        }
    }

    public final List<DataFile> getDatafiles() {
        return childrenList().stream()
                .map(iNode -> (DataFile) iNode)
                .sorted(Comparator.comparingInt(DataFile::getFileId))
                .collect(Collectors.toList());
    }

    public final Database getDatabase() {
        return database;
    }

    public final int getConnectionID() {
        return database.getConnectionID(false);
    }

    public final long getBlockSize() {
        return blockSize;
    }

    public final long getBlocks() {
        return getDatafiles().parallelStream().mapToLong(DataFile::getBlocks).sum();
    }

    public final void coalesce() {
        try (final PreparedStatement commandStatement = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class)
                .prepareStatement(
                    getConnectionID(),
                    MessageFormat.format("ALTER TABLESPACE {0} COALESCE", getPID())
                )
        ) {
            try {
                commandStatement.execute();
                commandStatement.getConnection().commit();
            } finally {
                if (commandStatement != null) {
                    commandStatement.getConnection().close();
                }
            }
        } catch (SQLException e) {
            Logger.getContextLogger(TableSpaceManager.class).error("Database error", e);
        }
    }

    @Override
    public void setParent(INode parent) {
        if (parent != null) {
            updateInfo();
        }
        super.setParent(parent);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return DataFile.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public void loadChildren() {}
}
