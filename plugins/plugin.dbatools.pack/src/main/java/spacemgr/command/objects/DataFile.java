package spacemgr.command.objects;

import codex.database.IDatabaseAccessService;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Catalog;
import codex.service.ServiceRegistry;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Int;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.TableSpaceManager;
import javax.swing.*;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Locale;

public class DataFile extends Catalog {

    private final static ImageIcon IMAGE_DBF = ImageUtils.getByPath("/images/datafile.png");
    private final static String PROP_ID  = "fileID";
    private final static String PROP_EXT = "autoExt";

    public static Integer truncateSize(long bytes) {
        return (int) (bytes / 1024 / 1024 + 1);
    }

    private Long blocks = 0L;

    public DataFile(EntityRef owner, String path) {
        super(owner, IMAGE_DBF, path, null);
        setTitle(new File(getPID()).getName());

        model.addDynamicProp(PROP_ID,  new Int(null),  Access.Any, null);
        model.addDynamicProp(PROP_EXT, new Bool(null), Access.Edit, null);

        updateInfo();
    }

    private void updateInfo() {
        final String query = "SELECT FILE_ID, AUTOEXTENSIBLE, BLOCKS FROM DBA_DATA_FILES WHERE FILE_NAME = ?";
        try (final ResultSet resultSet = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class)
                .select(
                    getTablespace().getDatabase().getConnectionID(false),
                    query, getPID()
                )
        ) {
            if (resultSet.next()) {
                this.blocks = resultSet.getLong("BLOCKS");
                model.setValue(PROP_ID,  resultSet.getInt("FILE_ID"));
                model.setValue(PROP_EXT, "YES".equals(resultSet.getString("AUTOEXTENSIBLE")));
            }
        } catch (SQLException e) {
            Logger.getContextLogger(TableSpaceManager.class).error("Database error", e);
        }
    }

    TableSpace getTablespace() {
        return (TableSpace) getOwner();
    }

    final Integer getFileId() {
        return (Integer) model.getValue(PROP_ID);
    }

    public final String getFileName() {
        return getPID();
    }

    public long getBlocks() {
        return blocks;
    }

    public final Boolean isAutoExtensible() {
        return model.getValue(PROP_EXT) == Boolean.TRUE;
    }

    public long getMinimalSize() {
        long freeBlocks = 0;
        final String query = Language.get(DataFile.class, "query@minimum", Language.DEF_LOCALE);
        try (final ResultSet resultSet = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class)
                .select(
                        getTablespace().getConnectionID(),
                        query, getFileId()
                )
        ) {
            long prevChunk  = 0;
            while (resultSet.next()) {
                int chunkStart  = resultSet.getInt("FIRST");
                int chunkFinish = resultSet.getInt("LAST");
                int chunkLength = resultSet.getInt("BLOCKS");

                if (freeBlocks == 0) {
                    if (chunkFinish == getBlocks()) {
                        prevChunk = chunkFinish;
                    } else {
                        break;
                    }
                }
                if (chunkFinish == prevChunk) {
                    freeBlocks += chunkLength;
                    prevChunk  = chunkStart;
                } else {
                    resultSet.getStatement().close();
                    break;
                }
            }
        } catch (SQLException ignore) {}

        return (getBlocks() - freeBlocks) * getTablespace().getBlockSize();
    }

    public void resize(Integer sizeInMB) throws SQLException {
        final String query = MessageFormat.format(
                "ALTER DATABASE DATAFILE ''{0}'' RESIZE {1}M",
                getFileName(), String.valueOf(sizeInMB)
        );
        ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class).update(
                getTablespace().getConnectionID(),
                query
        );
        updateInfo();
    }
}
