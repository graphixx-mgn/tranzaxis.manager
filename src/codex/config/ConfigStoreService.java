package codex.config;

import codex.database.IDatabaseAccessService;
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
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
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
            if (propDefinition.isEmpty()) {
                columns.add("OWN TEXT");
            }
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
        }
    }
    
    @Override
    public void maintainClassCatalog(Class clazz, List<String> unusedProperties, Map<String, IComplexType> newProperties) {
        final String className = clazz.getSimpleName().toUpperCase();
        try {
            semaphore.acquire();
            if (unusedProperties != null && !unusedProperties.isEmpty()) {
                cropCatalog(clazz, unusedProperties);
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Catalog {0} maintainance: Deleted columns {1}", 
                        className,
                        unusedProperties
                ));
            }
            if (newProperties != null && !newProperties.isEmpty()) {
                extendCatalog(clazz, newProperties);
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Catalog {0} maintainance: Added columns {1}", 
                        className,
                        newProperties.keySet()
                ));
            }
        } catch (Exception e) {
            Logger.getLogger().error(MessageFormat.format(
                    "CAS: Сatalog {0} maintainance failed", className
            ), e);
        } finally {
            semaphore.release();
        }
    }
    
    private void extendCatalog(Class clazz, Map<String, IComplexType> newProperties) throws Exception {
        final String className = clazz.getSimpleName().toUpperCase();
        List<String> columns = new LinkedList<>();
        
        newProperties.forEach((propName, propVal) -> {
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
        });

        try (final Statement alter = connection.createStatement()) {
            for (String column : columns) {
                String alterSQL = MessageFormat.format("ALTER TABLE {0} ADD COLUMN {1}",
                    className,
                    column
                );
                alter.execute(alterSQL);                
            }
            connection.commit();
            storeStructure.get(className).addAll(newProperties.keySet());
        } catch (SQLException e) {
            throw new Exception("Unable to add column", e);
        }
    }
    
    private synchronized void cropCatalog(Class clazz, List<String> unusedProperties) throws Exception {
        final String className = clazz.getSimpleName().toUpperCase();
        
        String primaryKey = "";  
        final Map<String, String>       columns = new LinkedHashMap<>(); 
        final Map<String, String>       references = new LinkedHashMap<>(); 
        final Map<String, List<String>> constraints = new HashMap<>(); 
        
        // Primary key
        try (ResultSet rs = connection.getMetaData().getPrimaryKeys(null, null, className)) {
            if (rs.next()) {
                primaryKey = rs.getString("COLUMN_NAME");
            }
        } catch (SQLException e) {
            throw new Exception("Unable to determine primary key", e);
        }
        
        // Foreign keys
        try (ResultSet rs = connection.getMetaData().getImportedKeys(null, null, className)) {
            while (rs.next()) {
                references.put(rs.getString("FKCOLUMN_NAME"), "REFERENCES "+rs.getString("PKTABLE_NAME")+"("+rs.getString("PKCOLUMN_NAME")+")");
            }
        } catch (SQLException e) {
            throw new Exception("Unable to retrieve foreign keys", e);
        }
        
        // Columns
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, className, "%")) {
            while (rs.next()) {
                if (!unusedProperties.contains(rs.getString("COLUMN_NAME"))) {
                    columns.put(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("COLUMN_NAME").concat(" ")
                                .concat(rs.getString("TYPE_NAME")).concat(" ")
                                .concat("YES".equals(rs.getString("IS_NULLABLE")) ? "" : "NOT NULL").concat(" ")
                                .concat(rs.getString("COLUMN_NAME").equals(primaryKey.toUpperCase()) ? "PRIMARY KEY AUTOINCREMENT" : "").concat(" ")
                                .concat(references.containsKey(rs.getString("COLUMN_NAME")) ? references.get(rs.getString("COLUMN_NAME")) : "")
                    );
                }
            }
        } catch (SQLException e) {
            throw new Exception("Unable to get columns list", e);
        }
        
        try (ResultSet rs = connection.getMetaData().getIndexInfo(null, null, className, true, false)) {
            while (rs.next()) {
                if (!constraints.containsKey(rs.getString("INDEX_NAME"))) {
                    constraints.put(rs.getString("INDEX_NAME"), new LinkedList<>());
                }
                constraints.get(rs.getString("INDEX_NAME")).add(rs.getString("COLUMN_NAME"));
            }
        } catch (SQLException e) {
            throw new Exception("Unable to get indexes list", e);
        }
        
        StringJoiner constraintSql = new StringJoiner(",");
        AtomicInteger constraintIndex = new AtomicInteger(0);
        constraints.forEach((indexName, columnNames) -> {
            constraintIndex.addAndGet(1);
            constraintSql.add("CONSTRAINT UNIQUE_"+constraintIndex.get()+" UNIQUE ("+String.join(",", columnNames)+")");
        });

        String createSQL = MessageFormat.format(
                "CREATE TABLE IF NOT EXISTS {0} ({1}, {2})",
                className.concat("_NEW"),
                String.join(", ", columns.values()),
                constraintSql.toString()
        );
        String copySQL = MessageFormat.format(
                "INSERT INTO {0} SELECT {1} FROM {2}",
                className.concat("_NEW"),
                String.join(", ", columns.keySet()),
                className
        );
        String dropSQL = MessageFormat.format(
                "DROP TABLE IF EXISTS {0}",
                className
        );
        String renameSQL = MessageFormat.format(
                "ALTER TABLE {0} RENAME TO {1}",
                className.concat("_NEW"),
                className
        );
        
        try (final Statement statement = connection.createStatement()) {
            if (storeStructure.get(className).containsAll(unusedProperties)) {
                storeStructure.get(className).removeAll(unusedProperties);
                
                connection.setAutoCommit(true);
                connection.createStatement().executeUpdate("PRAGMA foreign_keys = OFF");
                connection.setAutoCommit(false);
                
                statement.executeUpdate(createSQL);
                statement.executeUpdate(copySQL);
                statement.executeUpdate(dropSQL);
                statement.executeUpdate(renameSQL);
                connection.commit();
                
                connection.setAutoCommit(true);
                connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
                connection.setAutoCommit(false);
            }
        } catch (SQLException e) {
            connection.rollback();
            throw new Exception("Unable to rebuild class catalog", e);
        }
    }

    @Override
    public Map<String, Integer> initClassInstance(Class clazz, String PID, Map<String, IComplexType> propDefinition, Integer ownerId) {
        final String className = clazz.getSimpleName().toUpperCase();
        if (!storeStructure.containsKey(className)/* || !storeStructure.get(className).containsAll(propDefinition.keySet())*/) {
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
                                            "CAS: New catalog {0} entry: #{1}-{2}", className, updateRS.getInt(1), PID
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
    public int updateClassInstance(Class clazz, Integer ID, Map<String, IComplexType> properties) {
        if (!properties.isEmpty()) {
            final String className = clazz.getSimpleName().toUpperCase();
            final String[] parts   = properties.keySet().toArray(new String[]{});
            
            final String updateSQL = "UPDATE "+className+" SET "+String.join(" = ?, ", parts)+" = ? WHERE ID = ?";
            final String updateTraceSQL = IDatabaseAccessService.prepareTraceSQL(updateSQL, properties.values().toArray(), ID);
            
            try (PreparedStatement update = connection.prepareStatement(updateSQL)) {
                List keys = new ArrayList(properties.keySet());
                properties.forEach((key, value) -> {
                    try {
                        update.setString(keys.indexOf(key)+1, value.toString().isEmpty() ? null : value.toString());
                    } catch (SQLException e) {
                        Logger.getLogger().error("Unable to update instance", e);
                    }
                });
                update.setInt(properties.size()+1, ID);
                semaphore.acquire();
                update.executeUpdate();
                connection.commit();
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Altered catalog {0} entry: #{1} {2}", className, ID, properties
                ));
                return RC_SUCCESS;
            } catch (SQLException | InterruptedException e) {
                Logger.getLogger().error("Unable to update instance", e);
                Logger.getLogger().debug("SQL Query: {0}", updateTraceSQL);
                return RC_ERROR;
            } finally {
                semaphore.release();
            }
        }
        return RC_ERROR;
    }
    
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
                        for (int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++) {
                            rowData.put(meta.getColumnName(colIdx), selectRS.getString(colIdx));
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
                        for (int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++) {
                            rowData.put(meta.getColumnName(colIdx), selectRS.getString(colIdx));
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
        String PID = readClassInstance(clazz, ID).get("PID");
        
        try {
            semaphore.acquire();
            
            try (PreparedStatement delete = connection.prepareStatement(deleteSQL)) {
                delete.setInt(1, ID);
                delete.executeUpdate();
                connection.commit();
                Logger.getLogger().debug(MessageFormat.format(
                        "CAS: Deleted catalog {0} entry: #{1}-{2}", className, ID, PID
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
                                "CAS: Found existing reference: {0}/#{1}-{2}",
                                selectRS.getString(1), selectRS.getInt(2), selectRS.getString(3)
                            ));
                            links.add(new ForeignLink(
                                    selectRS.getString(1), 
                                    selectRS.getInt(2), 
                                    selectRS.getString(3),
                                    !fkColumnName.toUpperCase().equals("OWN"))
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {}
        return links;
    }
    
    @Override
    public Class getOwnerClass(Class clazz) {
        final String className = clazz.getSimpleName().toUpperCase();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet foreignKeys = metaData.getImportedKeys(null, null, className);
            if (foreignKeys.next()) {
                String pkTableName  = foreignKeys.getString("PKTABLE_NAME");
                String selectSQL = "SELECT TABLE_CLASS FROM CLASSDEF WHERE TABLE_NAME = ?";
                try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                    select.setString(1, pkTableName);
                    try (ResultSet selectRS = select.executeQuery()) {
                        if (selectRS.next()) {
                            return Class.forName(selectRS.getString(1));
                        }
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException e) {}
        return null;
    }

}
