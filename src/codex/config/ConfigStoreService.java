package codex.config;

import codex.log.Logger;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Реализация интерфейса сервиса загрузки и сохранения данных модели на базе
 * SQLite.
 */
public final class ConfigStoreService implements IConfigStoreService {
    
    private final File configFile;
    private Connection connection;
    private final Map<String, List<String>> storeStructure = new HashMap<>();

    /**
     * Конструктор сервиса.
     * @param configFile Путь к файлу базы данных.
     */
    public ConfigStoreService(File configFile) {
        this.configFile = configFile;

        if (!this.configFile.exists()) {
            this.configFile.getParentFile().mkdirs();
        }

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:"+configFile.getPath());
            connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");            
            if (connection != null) {
                final DatabaseMetaData meta = connection.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, "%", "%")) {
                    while (rs.next()) {
                        if (!storeStructure.containsKey(rs.getString(3))) {
                            storeStructure.put(rs.getString(3), new ArrayList<>());
                        }
                        storeStructure.get(rs.getString(3)).add(rs.getString(4));
                    }
                    connection.setAutoCommit(false);
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to read DB file", e);
        }

    }

    @Override
    public void createClassCatalog(Class clazz) {
        final String className = clazz.getSimpleName().toUpperCase();
        if (!storeStructure.containsKey(className)) {
            String createSQL = MessageFormat.format("CREATE TABLE IF NOT EXISTS {0} ("
                            + "ID  INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "SEQ INTEGER NOT NULL,"
                            + "PID TEXT    NOT NULL,"
                            + "CONSTRAINT UNIQUE_PID UNIQUE (PID)"
                    + ");",
                    className
            );
            try (final Statement statement = connection.createStatement()) {
                statement.execute(createSQL);
                connection.commit();
                storeStructure.put(className, new ArrayList<>(Arrays.asList(new String[] {"ID", "SEQ", "PID"})));
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to create class catalog", e);
            }
        }
    }

    @Override
    public void addClassProperty(Class clazz, String propName, Class refClazz) {
        final String className = clazz.getSimpleName().toUpperCase();
        if (!storeStructure.containsKey(className)) {
            createClassCatalog(clazz);
        }
        if (!storeStructure.get(className).contains(propName)) {
            String alterSQL;
            if (refClazz == null) {
                alterSQL = MessageFormat.format("ALTER TABLE {0} ADD COLUMN {1} {2}",
                        className,
                        propName,
                        "TEXT"
                );
            } else {
                alterSQL = MessageFormat.format("ALTER TABLE {0} ADD COLUMN {1} {2} REFERENCES {3}(ID)",
                        className,
                        propName,
                        "INTEGER",
                        refClazz.getSimpleName().toUpperCase()
                );
            }
            try (final Statement stmt = connection.createStatement()) {
                stmt.execute(alterSQL);
                connection.commit();
                storeStructure.get(className).add(propName);
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to append class property", e);
            }
        }
    }

    @Override
    public Integer initClassInstance(Class clazz, String PID) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String selectSQL = MessageFormat.format(
                "SELECT ID FROM {0} WHERE PID = ?", className
        );
        final String insertSQL = MessageFormat.format(
                "INSERT INTO {0} (SEQ, PID) VALUES ((SELECT IFNULL(MAX(SEQ), 0)+1 FROM {0}), ?)", className
        );
        if (!storeStructure.containsKey(className)) {
            createClassCatalog(clazz);
        }
        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setString(1, PID);
            try (ResultSet selectRS = select.executeQuery()) {
                if (selectRS.next()) {
                    return selectRS.getInt(1);
                } else {
                    try (PreparedStatement insert = connection.prepareStatement(insertSQL, new String[] {"ID"})) {
                        insert.setString(1, PID);
                        int affectedRows = insert.executeUpdate();
                        if (affectedRows == 0) {
                            throw new SQLException("No rows affected.");
                        }
                        connection.commit();
                        try (ResultSet updateRS = insert.getGeneratedKeys()) {
                            if (updateRS.next()) {
                                return updateRS.getInt(1);
                            }
                        }
                    } catch (SQLException e) {
                        Logger.getLogger().error("Unable to init instance", e);
                    }
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to init instance", e);
        }
        return null;
    }
    
    @Override
    public boolean removeClassInstance(Class clazz, Integer ID) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String deleteSQL = MessageFormat.format("DELETE FROM {0} WHERE ID = ?", className);

        try (PreparedStatement delete = connection.prepareStatement(deleteSQL)) {
            delete.setInt(1, ID);
            int affectedRows = delete.executeUpdate();
            connection.commit();
            return affectedRows == 1;
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to delete instance", e);
            return false;
        }
    }

    @Override
    public String readClassProperty(Class clazz, Integer ID, String propName) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String selectSQL = MessageFormat.format("SELECT {0} FROM {1} WHERE ID = ?", propName, className);
        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setInt(1, ID);
            try (ResultSet selectRS = select.executeQuery()) {
                if (selectRS.next()) {
                    return selectRS.getString(1);
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to init instance", e);
        }
        return null;
    }

    @Override
    public boolean updateClassInstance(Class clazz, Integer ID, Map<String, String> properties) {
        if (!properties.isEmpty()) {
            final String className = clazz.getSimpleName().toUpperCase();
            final String[] parts   = properties.keySet().toArray(new String[]{});
            final String updateSQL = "UPDATE "+className+" SET "+String.join(" = ?, ", parts)+" = ? WHERE ID = ?";
            try (PreparedStatement update = connection.prepareStatement(updateSQL)) {
                List keys = new ArrayList(properties.keySet());
                properties.forEach((key, value) -> {
                    try {
                        update.setString(keys.indexOf(key)+1, value);
                    } catch (SQLException e) {
                        Logger.getLogger().error("Unable to update instance", e);
                    }
                });
                update.setInt(properties.size()+1, ID);
                int affectedRows = update.executeUpdate();
                connection.commit();
                return affectedRows == 1;
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to update instance", e);
                return false;
            }
        }
        return false;
    }

    @Override
    public List<Map<String, String>> readCatalogEntries(Class clazz) {
        List rows = new LinkedList();
        final String className = clazz.getSimpleName().toUpperCase();
        if (storeStructure.containsKey(className)) {
            final String selectSQL = MessageFormat.format("SELECT * FROM {0} ORDER BY SEQ", className);
            try (Statement select = connection.createStatement()) {
                select.setFetchSize(10);
                try (ResultSet selectRS = select.executeQuery(selectSQL)) {
                    ResultSetMetaData meta = selectRS.getMetaData();
                    while (selectRS.next()) {
                        String val;
                        Map<String, String> rowData = new HashMap<>();
                        for (int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++) {
                            val = selectRS.getString(colIdx);
                            if (val != null) {
                                rowData.put(meta.getColumnName(colIdx), selectRS.getString(colIdx));
                            }
                        }
                        rows.add(rowData);
                    }
                } catch (SQLException e) {
                    Logger.getLogger().error("Unable to read catalog", e);
                }
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to read catalog", e);
            }
        }
        return rows;
    };

    @Override
    public Map<String, String> readClassInstance(Class clazz, Integer ID) {
        Map<String, String> rowData = new HashMap<>();
        final String className = clazz.getSimpleName().toUpperCase();
        if (storeStructure.containsKey(className)) {
            final String selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE ID = ?", className);
            try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                select.setInt(1, ID);
                try (ResultSet selectRS = select.executeQuery()) {
                    ResultSetMetaData meta = selectRS.getMetaData();
                    if (selectRS.next()) {
                        String val;
                        for (int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++) {
                            val = selectRS.getString(colIdx);
                            if (val != null) {
                                rowData.put(meta.getColumnName(colIdx), selectRS.getString(colIdx));
                            }
                        }
                    }
                } catch (SQLException e) {
                    Logger.getLogger().error("Unable to read instance", e);
                }
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to read instance", e);
            }
        }
        return rowData;
    }

}
