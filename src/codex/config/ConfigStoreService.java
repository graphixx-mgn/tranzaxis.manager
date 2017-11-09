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
                DatabaseMetaData meta = connection.getMetaData();
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
        String className = clazz.getSimpleName().toUpperCase();
        if (!storeStructure.containsKey(className)) {
            String createSQL = MessageFormat.format("CREATE TABLE IF NOT EXISTS {0} ("
                            //+ "SEQ   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                            + "PID TEXT NOT NULL,"
                            + "CONSTRAINT CST_PID UNIQUE (PID)"
                    + ");", 
                    className
            );
            try (Statement statement = connection.createStatement()) {
                statement.execute(createSQL);
                storeStructure.put(className, new ArrayList<>());
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to create class catalog", e);
            }
        }
    }
    
    @Override
    public void addClassProperty(Class clazz, String propName) {
        String className = clazz.getSimpleName().toUpperCase();
        propName = propName.toUpperCase();
        if (!storeStructure.containsKey(className)) {
            createClassCatalog(clazz);
        }
        if (!storeStructure.get(className).contains(propName)) {
            String alterSQL = MessageFormat.format("ALTER TABLE {0} ADD COLUMN {1} {2}", 
                    className,
                    propName,
                    "TEXT"
            );
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(alterSQL);
                storeStructure.get(className).add(propName);
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to append class property", e);
            }
        }
    }
    
    @Override
    public void initClassInstance(Class clazz, String PID) {
        String className = clazz.getSimpleName().toUpperCase();
        String selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE PID = ?", className);
        String insertSQL = MessageFormat.format("INSERT INTO {0} (PID) VALUES (?);", className);
        
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
    public void readClassProperty(Class clazz, String PID, String propName, IComplexType propValue) {
        String className = clazz.getSimpleName().toUpperCase();
        propName = propName.toUpperCase();
        String selectSQL = MessageFormat.format("SELECT {0} FROM {1} WHERE PID = ?", propName, className);
     
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
        String className = clazz.getSimpleName().toUpperCase();
        String[] parts   = new String[properties.size()];
        properties.forEach((propHolder) -> {
            parts[properties.indexOf(propHolder)] = propHolder.getName().toUpperCase()+" = ?";
        });
        String updateSQL = "UPDATE "+className+" SET "+String.join(", ", parts)+" WHERE PID = ?";
        
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
    public String getTitle() {
        return "Configuration Manager";
    }
    
}
