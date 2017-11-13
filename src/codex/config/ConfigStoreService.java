package codex.config;

import codex.log.Logger;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
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
            if (connection != null) {
                final DatabaseMetaData meta = connection.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, "%", "%")) {
                    while (rs.next()) {
                        if (!storeStructure.containsKey(rs.getString(3))) {
                            storeStructure.put(rs.getString(3), new ArrayList<>());
                        }
                        storeStructure.get(rs.getString(3)).add(rs.getString(4));
                    }
                    connection.setAutoCommit(true);
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
                            //+ "SEQ   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                            + "PID TEXT NOT NULL,"
                            + "CONSTRAINT CST_PID UNIQUE (PID)"
                    + ");",
                    className
            );
            try (final Statement statement = connection.createStatement()) {
                statement.execute(createSQL);
                storeStructure.put(className, new ArrayList<>());
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to create class catalog", e);
            }
        }
    }

    @Override
    public void addClassProperty(Class clazz, String propName) {
        final String className = clazz.getSimpleName().toUpperCase();
        propName = propName.toUpperCase();
        if (!storeStructure.containsKey(className)) {
            createClassCatalog(clazz);
        }
        if (!storeStructure.get(className).contains(propName)) {
            final String alterSQL = MessageFormat.format("ALTER TABLE {0} ADD COLUMN {1} {2}",
                    className,
                    propName,
                    "TEXT"
            );
            try (final Statement stmt = connection.createStatement()) {
                stmt.execute(alterSQL);
                storeStructure.get(className).add(propName);
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to append class property", e);
            }
        }
    }

    @Override
    public void initClassInstance(Class clazz, String PID) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE PID = ?", className);
        final String insertSQL = MessageFormat.format("INSERT INTO {0} (PID) VALUES (?);", className);
        
        if (!storeStructure.containsKey(className)) {
            createClassCatalog(clazz);
        }

        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setString(1, PID);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement insert = connection.prepareStatement(insertSQL)) {
                        insert.setString(1, PID);
                        insert.executeUpdate();
                    } catch (SQLException e) {
                        throw e;
                    }
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to init instance", e);
        }
    }
    
    @Override
    public boolean removeClassInstance(Class clazz, String PID) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String deleteSQL = MessageFormat.format("DELETE FROM {0} WHERE PID = ?", className);
        
        try (PreparedStatement delete = connection.prepareStatement(deleteSQL)) {
            delete.setString(1, PID);
            delete.executeUpdate();
            return true;
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to init instance", e);
            return false;
        }
    }

    @Override
    public void readClassProperty(Class clazz, String PID, String propName, IComplexType propValue) {
        final String className = clazz.getSimpleName().toUpperCase();
        propName = propName.toUpperCase();
        final String selectSQL = MessageFormat.format("SELECT {0} FROM {1} WHERE PID = ?", propName, className);

        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setString(1, PID);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) {
                    propValue.valueOf(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to init instance", e);
        }
    }

    @Override
    public boolean updateClassInstance(Class clazz, String PID, List<PropertyHolder> properties) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String[] parts   = new String[properties.size()];
        properties.forEach((propHolder) -> {
            parts[properties.indexOf(propHolder)] = propHolder.getName().toUpperCase()+" = ?";
        });
        final String updateSQL = "UPDATE "+className+" SET "+String.join(", ", parts)+" WHERE PID = ?";

        try (PreparedStatement select = connection.prepareStatement(updateSQL)) {
            for (PropertyHolder propHolder : properties) {
                select.setObject(properties.indexOf(propHolder)+1, propHolder.getPropValue().toString());
            }
            select.setString(properties.size()+1, PID);
            select.executeUpdate();
            return true;
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to update instance", e);
            return false;
        }
    }

    @Override
    public List<String> readCatalogEntries(Class clazz) {
        final List<String> PIDs = new LinkedList<>();
        final String className  = clazz.getSimpleName().toUpperCase();
        
        if (storeStructure.containsKey(className)) {
            final String selectSQL  = MessageFormat.format("SELECT PID FROM {0} ORDER BY ROWID", className);

            try (Statement select = connection.createStatement()) {
                try (ResultSet rs = select.executeQuery(selectSQL)) {
                    while (rs.next()) {
                        PIDs.add(rs.getString(1));
                    }
                }
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to init instance", e);
            }
        }
        return PIDs;
    };

    @Override
    public String getTitle() {
        return "Configuration Manager";
    }

}
