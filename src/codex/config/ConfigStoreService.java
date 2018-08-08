package codex.config;

import codex.log.Logger;
import codex.type.EntityRef;
import codex.type.IComplexType;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.sqlite.JDBC;
import org.sqlite.core.Codes;

/**
 * Реализация интерфейса сервиса загрузки и сохранения данных модели на базе
 * SQLite.
 */
public final class ConfigStoreService implements IConfigStoreService {
    
    private final File configFile;
    private Connection connection;
    private final Map<String, List<String>> storeStructure = new HashMap<>();
    
    Semaphore semaphore = new Semaphore(1, true);

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
            DriverManager.registerDriver(new JDBC());
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
                initClassDef();
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: DB product version: {0} v.{1}",
                        meta.getDatabaseProductName(),
                        meta.getDatabaseProductVersion()
                ));
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to read DB file", e);
        }

    }
    
    private void initClassDef() {
        String createSQL = "CREATE TABLE IF NOT EXISTS CLASSDEF (TABLE_NAME TEXT, TABLE_CLASS TEXT)";
        try (final Statement statement = connection.createStatement()) {
            statement.execute(createSQL);
            connection.commit();
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to create class definition table", e);
        }
    }
    
    private void buildClassCatalog(Class clazz, Map<String, IComplexType> propDefinition) {
        final String className = clazz.getSimpleName().toUpperCase();
        
        List<String> columns = new LinkedList<>();
        if (!storeStructure.containsKey(className)) {
            columns.add("ID  INTEGER PRIMARY KEY AUTOINCREMENT");
            columns.add("PID TEXT NOT NULL");
            columns.add("SEQ INTEGER NOT NULL");
            columns.add("OVR TEXT");
        }
        
        propDefinition.forEach((propName, propVal) -> {
            if (!storeStructure.containsKey(className) || !storeStructure.get(className).contains(propName)) {
                Class refClazz;
                if (propVal instanceof EntityRef && (refClazz = ((EntityRef) propVal).getEntityClass()) != null) {
                    if (!storeStructure.containsKey(refClazz.getSimpleName().toUpperCase())) {
                        buildClassCatalog(refClazz, new HashMap<>());
                    }
                    columns.add(propName
                            .concat(" INTEGER REFERENCES ")
                            .concat(refClazz.getSimpleName().toUpperCase())
                            .concat("(ID)")
                    );
                } else {
                    columns.add(propName.concat(" TEXT"));
                }
            }
        });
        
        if (!storeStructure.containsKey(className)) {
            String createSQL = MessageFormat.format(
                    "CREATE TABLE IF NOT EXISTS {0} ({1}, CONSTRAINT UNIQUE_PID UNIQUE (PID, OWN));",
                    className,
                    String.join(", ", columns)
            );
            try (final Statement create = connection.createStatement()) {
                create.execute(createSQL);
                connection.commit();

                List<String> columnNames = new LinkedList<>(propDefinition.keySet());
                columnNames.addAll(0, Arrays.asList(new String[] {"ID", "PID", "SEQ", "OVR"}));
                storeStructure.put(className, columnNames);
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Created catalog {0}: {1}", className, storeStructure.get(className)
                ));
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to create class catalog", e);
            }
            
            String registerQSL = "INSERT INTO CLASSDEF(TABLE_NAME, TABLE_CLASS) SELECT ?, ? WHERE NOT EXISTS(SELECT 1 FROM CLASSDEF WHERE TABLE_NAME = ?)";
            try (final PreparedStatement register = connection.prepareStatement(registerQSL)) {
                register.setString(1, className);
                register.setString(2, clazz.getCanonicalName());
                register.setString(3, className);
                register.execute();
                connection.commit();
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Registered catalog {0} => {1}", clazz.getCanonicalName(), className
                ));
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to register class catalog", e);
            }
            
        } else {
            try (final Statement alter = connection.createStatement()) {
                for (String column : columns) {
                    String alterSQL = MessageFormat.format("ALTER TABLE {0} ADD COLUMN {1}",
                        className,
                        column
                    );
                    alter.execute(alterSQL);                
                }
                connection.commit();

                List<String> columnNames = new LinkedList<>(propDefinition.keySet());
                columnNames.addAll(0, Arrays.asList(new String[] {"ID", "PID", "SEQ", "OVR"}));
                storeStructure.put(className, columnNames);
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Altered catalog {0}: {1}", className, storeStructure.get(className)
                ));
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to create class catalog", e);
            }
        }
    }

    @Override
    public Map<String, Integer> initClassInstance(Class clazz, String PID, Map<String, IComplexType> propDefinition, Integer ownerId) {
        final String className = clazz.getSimpleName().toUpperCase();
        if (!storeStructure.containsKey(className) || !storeStructure.get(className).containsAll(propDefinition.keySet())) {
            buildClassCatalog(clazz, propDefinition);
        }
        final String selectSQL;
        if (ownerId != null) {
            selectSQL = MessageFormat.format("SELECT ID, SEQ FROM {0} WHERE PID = ? AND OWN = ?", className);
        } else {
            selectSQL = MessageFormat.format("SELECT ID, SEQ FROM {0} WHERE PID = ?", className);
        }
        final String insertSQL = MessageFormat.format(
                "INSERT INTO {0} (SEQ, PID) VALUES ((SELECT IFNULL(MAX(SEQ), 0)+1 FROM {0}), ?)", className
        );
        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setString(1, PID);
            if (ownerId != null) {
                select.setInt(2, ownerId);
            }
            
            try (ResultSet selectRS = select.executeQuery()) {
                if (selectRS.next()) {
                    Map<String, Integer> keys = new HashMap<>();
                    keys.put("ID",  selectRS.getInt(1));
                    keys.put("SEQ", selectRS.getInt(2));
                    return keys;
                } else {
                    try {
                        semaphore.acquire();
                        
                        try (PreparedStatement insert = connection.prepareStatement(insertSQL, new String[] {"ID"})) {
                            insert.setString(1, PID);
                            int affectedRows = insert.executeUpdate();
                            if (affectedRows == 0) {
                                throw new SQLException("No rows affected.");
                            }
                            connection.commit();
                            try (ResultSet updateRS = insert.getGeneratedKeys()) {                        
                                if (updateRS.next()) {
                                    Logger.getLogger().debug(MessageFormat.format(
                                            "CAS: New catalog {0} entry: #{1} '{'PID={2}'}'", className, updateRS.getInt(1), PID
                                    ));
                                    Map<String, Integer> keys = new HashMap<>();
                                    keys.put("ID",  updateRS.getInt(1));

                                    final String readSQL = MessageFormat.format(
                                            "SELECT SEQ FROM {0} WHERE ID = ?", className
                                    );
                                    try (PreparedStatement read = connection.prepareStatement(readSQL)) {
                                        read.setInt(1, updateRS.getInt(1));
                                        try (ResultSet readRS = read.executeQuery()) {
                                            if (readRS.next()) {
                                                keys.put("SEQ", readRS.getInt(1));
                                            }
                                        } catch (SQLException e) {
                                            Logger.getLogger().error("Unable to init instance", e);
                                        }
                                    }
                                    return keys;
                                }
                            }
                        } catch (SQLException e) {
                            Logger.getLogger().error("Unable to init instance", e);
                        }
                    } catch (InterruptedException e) {
                        return null;
                    } finally {
                        semaphore.release();
                    }
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to init instance", e);
        }
        return null;
    }
    
    @Override
    public int updateClassInstance(Class clazz, Integer ID, Map<String, String> properties) {
        if (!properties.isEmpty()) {
            final String className = clazz.getSimpleName().toUpperCase();
            final String[] parts   = properties.keySet().toArray(new String[]{});
            final String updateSQL = "UPDATE "+className+" SET "+String.join(" = ?, ", parts)+" = ? WHERE ID = ?";
            try {
                semaphore.acquire();
            
                try (PreparedStatement update = connection.prepareStatement(updateSQL)) {
                    List keys = new ArrayList(properties.keySet());
                    properties.forEach((key, value) -> {
                        try {
                            update.setString(keys.indexOf(key)+1, value.isEmpty() ? null : value);
                        } catch (SQLException e) {
                            Logger.getLogger().error("Unable to update instance", e);
                        }
                    });
                    update.setInt(properties.size()+1, ID);
                    update.executeUpdate();
                    connection.commit();
                    Logger.getLogger().debug(MessageFormat.format(
                            "CAS: Altered catalog {0} entry: #{1} {2}", className, ID, properties
                    ));
                    return RC_SUCCESS;
                } catch (SQLException e) {
                    Logger.getLogger().error("Unable to update instance", e);
                    return RC_ERROR;
                }
            } catch (InterruptedException e) {
                return RC_SUCCESS;
            } finally {
                semaphore.release();
            }
        }
        return RC_ERROR;
    }
    
    @Override
    public Map<String, String> readClassInstance(Class clazz, String PID, Integer ownerId) {
        Map<String, String> rowData = new HashMap<>();
        final String className = clazz.getSimpleName().toUpperCase();
        if (storeStructure.containsKey(className)) {
            final String selectSQL;
            if (ownerId != null) {
                selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE PID = ? AND OWN = ?", className);
            } else {
                selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE PID = ?", className);
            }
            try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                select.setString(1, PID);
                if (ownerId != null) {
                    select.setInt(2, ownerId);
                }
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
    
    @Override
    public Map<Integer, String> readCatalogEntries(Integer ownerId, Class clazz) {
        Map rows = new LinkedHashMap();
        final String className = clazz.getSimpleName().toUpperCase();
        if (storeStructure.containsKey(className)) {
            final String selectSQL;
            if (ownerId != null) {
                selectSQL = MessageFormat.format("SELECT ID, PID FROM {0} WHERE (OWN = ?) ORDER BY SEQ", className);
            } else {
                selectSQL = MessageFormat.format("SELECT ID, PID FROM {0} ORDER BY SEQ", className);
            }
            try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                select.setFetchSize(10);
                if (ownerId != null) {
                    select.setInt(1, ownerId);
                }
                try (ResultSet selectRS = select.executeQuery()) {
                    while (selectRS.next()) {
                        rows.put(selectRS.getInt(1), selectRS.getString(2));
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
    public int removeClassInstance(Class clazz, Integer ID) {
        final String className = clazz.getSimpleName().toUpperCase();
        final String deleteSQL = MessageFormat.format("DELETE FROM {0} WHERE ID = ?", className);
        
        try {
            semaphore.acquire();
            
            try (PreparedStatement delete = connection.prepareStatement(deleteSQL)) {
                delete.setInt(1, ID);
                delete.executeUpdate();
                connection.commit();
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Deleted catalog {0} entry: {1}", className, ID
                ));
                return RC_SUCCESS;
            } catch (SQLException e) {
                if (e.getErrorCode() == Codes.SQLITE_CONSTRAINT) {
                    return RC_DEL_CONSTRAINT;
                } else {
                    Logger.getLogger().error("Unable to delete instance", e);
                    return RC_ERROR;
                }
            }
        } catch (InterruptedException e) {
            return RC_SUCCESS;
        } finally {
            semaphore.release();
        }
    }
    
    @Override
    public List<ForeignLink> findReferencedEntries(Class clazz, Integer ID) {
        List<ForeignLink> links = new LinkedList<>();
        final String className = clazz.getSimpleName().toUpperCase();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet foreignKeys = metaData.getExportedKeys(null, null, className);
            while (foreignKeys.next()) {
                String fkTableName  = foreignKeys.getString("FKTABLE_NAME");
                String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                String pkTableName  = foreignKeys.getString("PKTABLE_NAME");
                String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");
                Logger.getLogger().debug(MessageFormat.format(
                    "CAS: Found declared reference: {0}.{1} -> {2}.{3}",
                    fkTableName.toUpperCase(), fkColumnName.toUpperCase(), 
                    pkTableName.toUpperCase(), pkColumnName.toUpperCase()
                ));
                String selectSQL = MessageFormat.format(
                        "SELECT TABLE_CLASS, ID, PID FROM {0}, CLASSDEF WHERE {1} = ? AND TABLE_NAME = ?",
                        fkTableName, fkColumnName
                );
                try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                    select.setInt(1, ID);
                    select.setString(2, fkTableName.toUpperCase());
                    try (ResultSet selectRS = select.executeQuery()) {
                        while (selectRS.next()) {
                            Logger.getLogger().debug(MessageFormat.format(
                                "CAS: Found existing reference: {0}/{1}-{2}",
                                selectRS.getString(1), selectRS.getInt(2), selectRS.getString(3)
                            ));
                            links.add(new ForeignLink(selectRS.getString(1), selectRS.getInt(2)));
                        }
                    }
                }
            }
        } catch (SQLException e) {}
        return links;
    }

}
