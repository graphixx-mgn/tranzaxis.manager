package codex.config;

import codex.context.IContext;
import codex.database.IDatabaseAccessService;
import codex.log.*;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.service.AbstractService;
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
import java.sql.Savepoint;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.sqlite.*;
import org.sqlite.core.Codes;

import javax.swing.*;

/**
 * Реализация интерфейса сервиса загрузки и сохранения данных модели на базе SQLite.
 */
@IContext.Definition(id = "CAS", name = "Configuration Access Service", icon = "/images/config.png")
public final class ConfigStoreService extends AbstractService<ConfigServiceOptions> implements IConfigStoreService, IContext {

    private Connection connection;
    private final Map<String, TableInfo> tableRegistry = new HashMap<>();

    // Контексты
    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "CAS.Dmp", name = "Show tables information", icon = "/images/dump.png", parent = ConfigStoreService.class)
    private static class DumpContext implements IContext {}

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "CAS.Ddl", name = "Table structure changes", icon = "/images/maintenance.png", parent = ConfigStoreService.class)
    private static class DdlContext implements IContext {}

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "CAS.Sql", name = "Preview SQL queries", icon = "/images/command.png", parent = ConfigStoreService.class)
    private static class QueryContext implements IContext {}


    /**
     * Конструктор сервиса.
     */
    public ConfigStoreService() {
        File configFile = new File(System.getProperty("user.home") + getOption("file"));
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
        }

        try {
            DriverManager.registerDriver(new JDBC());
            connection = DriverManager.getConnection("jdbc:sqlite:"+ configFile.getPath());

            // Use in order to do not save session changes
//            connection = DriverManager.getConnection("jdbc:sqlite:");
//            try(Statement stat = connection.createStatement()) {
//                stat.executeUpdate("restore from "+configFile.getPath());
//            }

            connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
            if (connection != null) {
                final DatabaseMetaData meta = connection.getMetaData();
                List<String> sysTables = Arrays.asList("sqlite_master", "sqlite_sequence");
                
                try (ResultSet rs = meta.getTables(null, null, "%", new String[] { "TABLE" })) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (!sysTables.contains(tableName)) {
                            try {
                                if (!tableRegistry.containsKey(tableName)) {
                                    tableRegistry.put(tableName, new TableInfo(tableName));
                                }
                            } catch (Exception e) {
                                Logger.getLogger().error("Unable to build table registry", e);
                            }
                        }
                    }
                    connection.setAutoCommit(false);
                }
                initClassDef();
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to read DB file", e);
        }
    }

    @Override
    public void startService() {
        super.startService();
        Logger.getContextLogger(DumpContext.class).debug(
                "Table structure dump:\n{0}",
                tableRegistry.values().stream().map((tableInfo) -> {
                    return MessageFormat.format("[{0}]\n", tableInfo.name).concat(tableInfo.toString());
                }).collect(Collectors.joining("\n\n"))
        );
        SwingUtilities.invokeLater(this::maintainClassDef);
    }

    @Override
    public synchronized void buildClassCatalog(Class clazz, Map<String, IComplexType> propDefinition) throws Exception {
        final String className = clazz.getSimpleName().toUpperCase();
        if (tableRegistry.containsKey(className)) {
            return;
        }
        Map<String, String> columns = new LinkedHashMap<String, String>() {{
            put(EntityModel.ID,  "INTEGER PRIMARY KEY AUTOINCREMENT");
            put(EntityModel.SEQ, "INTEGER NOT NULL");
            put(EntityModel.PID, "TEXT NOT NULL");
            put(EntityModel.OWN, getColumnDefinition(propDefinition.get(EntityModel.OWN), false));
            put(EntityModel.OVR, "TEXT");
        }};
        for (Map.Entry<String, IComplexType> entry : propDefinition.entrySet()) {
            if (!columns.containsKey(entry.getKey())) {
                columns.put(entry.getKey(), getColumnDefinition(entry.getValue(), true));
            }
        }
        String nameFormat = "%-".concat(
                Integer.toString(
                        columns.keySet().stream().mapToInt(String::length).max().orElse(0)+2
                ).concat("s ")
        );
        String createSQL = MessageFormat.format(
                    "CREATE TABLE IF NOT EXISTS {0} (\n\t{1}\n);",
                    className,
                    columns.entrySet().stream()
                            .map((entry) -> String.format(nameFormat, "["+entry.getKey()+"]").concat(entry.getValue()))
                            .collect(Collectors.joining(",\n\t"))
        );
        String indexSQL = MessageFormat.format(
                "CREATE UNIQUE INDEX IF NOT EXISTS IDX_{0}_PID_OWN ON {0} ([PID], [OWN])",
                className
        );
        String triggerBI = generateTriggerCode(className, TriggerKind.Before_Insert);
        String triggerBU = generateTriggerCode(className, TriggerKind.Before_Update);

        Logger.getContextLogger(QueryContext.class).debug(
            "Create table queries:\n{0}",
            "[1] ".concat(createSQL)
                  .concat("\n[2] ").concat(indexSQL)
                  .concat("\n[3] ").concat(triggerBI)
                  .concat("\n[4] ").concat(triggerBU)
        );
        
        String registerSQL = "INSERT INTO CLASSDEF (TABLE_NAME, TABLE_CLASS) SELECT ?, ? WHERE NOT EXISTS(SELECT 1 FROM CLASSDEF WHERE TABLE_NAME = ?)";
        
        Savepoint savepoint = connection.setSavepoint(className);
        try (
            final PreparedStatement create   = connection.prepareStatement(createSQL);
            final Statement         index    = connection.createStatement();
            final Statement         trigger  = connection.createStatement();
            final PreparedStatement register = connection.prepareStatement(registerSQL);
        ) {
            create.executeUpdate();
            index.executeUpdate(indexSQL);
            trigger.executeUpdate(triggerBI);
            trigger.executeUpdate(triggerBU);
            
            register.setString(1, className);
            register.setString(2, clazz.getCanonicalName());
            register.setString(3, className);
            register.executeUpdate();
            
            if (!tableRegistry.containsKey(className)) {
                tableRegistry.put(className, new TableInfo(className));
            }

            boolean dumpContextAllowed = Logger.contextAllowed(Collections.singletonList(DumpContext.class), Level.Debug);
            Logger.getContextLogger(DdlContext.class).debug(
            "Created class catalog {0} => {1}: {2}{3}",
                    clazz.getCanonicalName(),
                    className,
                    tableRegistry.get(className).columnInfos.stream()
                            .map((columnInfo) -> columnInfo.name)
                            .collect(Collectors.joining(",")),
                    dumpContextAllowed ? "\n".concat(tableRegistry.get(className).toString()) : ""
            );
            connection.releaseSavepoint(savepoint);
            connection.commit();
        } catch (SQLException e) {
            Logger.getContextLogger(DdlContext.class).error(MessageFormat.format("Unable to create class catalog ''{0}''", className), e);
            try {
                Logger.getLogger().warn("Perform rollback");
                connection.rollback(savepoint);
            } catch (SQLException e1) {
                Logger.getLogger().error("Unable to rollback database", e1);
            }
            throw e;
        }
    }

    @Override
    public synchronized Map<String, Integer> initClassInstance(Class clazz, String PID, Map<String, IComplexType> propDefinition, Integer ownerId) throws Exception {        
        final String className = clazz.getSimpleName().toUpperCase();
        
        if (!tableRegistry.containsKey(className)) {
            buildClassCatalog(clazz, propDefinition);
        } else {
            Map<String, IComplexType> invalidReferences = propDefinition.entrySet().stream()
                    .filter((entry) -> {
                        return 
                                entry.getValue() instanceof EntityRef &&
                                ((EntityRef) entry.getValue()).getEntityClass() != null &&
                                tableRegistry.get(className).refInfos.stream()
                                        .noneMatch((refInfo) -> refInfo.fkColumn.equals(entry.getKey()));
                    }).collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));
            List<String> deleteProps = tableRegistry.get(className).columnInfos.stream()
                    .filter((columnInfo) -> {
                        return !EntityModel.SYSPROPS.contains(columnInfo.name) && (
                                    !propDefinition.keySet().contains(columnInfo.name) ||
                                     invalidReferences.keySet().contains(columnInfo.name)
                        );
                    })
                    .map((columnInfo) -> columnInfo.name)
                    .collect(Collectors.toList());
            Map<String, IComplexType> addedProps = propDefinition.entrySet().stream()
                    .filter((entry) -> {
                        return
                                invalidReferences.keySet().contains(entry.getKey()) ||
                                tableRegistry.get(className).columnInfos.stream().noneMatch((columnInfo) -> columnInfo.name.equals(entry.getKey()));
                    }).collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));
            if (!deleteProps.isEmpty() || !addedProps.keySet().isEmpty()) {
                maintainClassCatalog(clazz, deleteProps, addedProps);
            }
        }

        final String insertSQL = MessageFormat.format(
                "INSERT INTO {0} ([SEQ], [PID], [OWN]) VALUES ((SELECT IFNULL(MAX([SEQ]), 0)+1 FROM {0}), ?, ?)", className
        );

        Logger.getContextLogger(QueryContext.class).debug(
                "Insert query: {0}",
                IDatabaseAccessService.prepareTraceSQL(
                        MessageFormat.format("INSERT INTO {0} ([PID], [OWN]) VALUES (?, ?)", className),
                        PID,
                        ownerId
                )
        );
        
        Savepoint savepoint = connection.setSavepoint(className);
        try (
            PreparedStatement insert = connection.prepareStatement(insertSQL, new String[] {EntityModel.ID});
        ) {
            insert.setString(1, PID);
            if (ownerId == null) {
                insert.setNull(2, Codes.SQLITE_TEXT);
            } else {
                insert.setInt(2, ownerId);
            }
            
            int affectedRows = insert.executeUpdate();
            if (affectedRows != 1) {
                throw new SQLException("Affected rows: "+affectedRows);
            }
            
            Map<String, Integer> keys = new HashMap<>();
            try (ResultSet updateRS = insert.getGeneratedKeys()) {                        
                if (updateRS.next()) {
                    Logger.getLogger().debug(MessageFormat.format(
                            "Created new catalog {0} entry: #{1}-{2}", className, updateRS.getInt(1), PID
                    ));
                    keys.put(EntityModel.ID, updateRS.getInt(1));
                    try (PreparedStatement read = connection.prepareStatement(
                            MessageFormat.format("SELECT SEQ FROM {0} WHERE ID = ?", className)
                    )) {
                        read.setInt(1, updateRS.getInt(1));
                        try (ResultSet readRS = read.executeQuery()) {
                            if (readRS.next()) {
                                keys.put(EntityModel.SEQ, readRS.getInt(1));
                            }
                        }
                    }
                }
            }
            connection.releaseSavepoint(savepoint);
            connection.commit();
            return keys;
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to save instance ''{0}'' to class catalog ''{1}'': {2}", PID, className, e.getMessage());
            try {
                Logger.getLogger().warn("Perform rollback");
                connection.rollback(savepoint);
            } catch (SQLException e1) {
                Logger.getLogger().error("Unable to rollback database", e1);
            }
            throw e;
        }
    }
    
    @Override
    public synchronized void updateClassInstance(Class clazz, Integer ID, Map<String, IComplexType> properties) throws Exception {
        if (!properties.isEmpty()) {
            final String className = clazz.getSimpleName().toUpperCase();
            String nameFormat = "%-".concat(
                    Integer.toString(
                            properties.keySet().stream().mapToInt(String::length).max().orElse(0)+2
                    ).concat("s ")
            );
            String updateSQL = MessageFormat.format(
                    "UPDATE {0} SET \n\t{1}\nWHERE [ID] = ?;",
                    className,
                    properties.keySet().stream()
                            .map(propName -> String.format(nameFormat, "["+propName+"]").concat(" = ?"))
                            .collect(Collectors.joining(",\n\t"))
            );

            Logger.getContextLogger(QueryContext.class).debug(
                    "Update query: {0}",
                    IDatabaseAccessService.prepareTraceSQL(updateSQL, properties.values().toArray(), ID)
            );
            
            Savepoint savepoint = connection.setSavepoint(className);
            try (
                PreparedStatement update = connection.prepareStatement(updateSQL)
            ) {
                List<String> keys = new ArrayList<>(properties.keySet());
                for (Map.Entry<String, IComplexType> entry : properties.entrySet()) {
                    update.setString(
                            keys.indexOf(entry.getKey())+1,
                            entry.getValue().toString() == null || entry.getValue().toString().isEmpty() ? null : entry.getValue().toString()
                    );
                }
                update.setInt(properties.size()+1, ID);
                update.executeUpdate();

                Logger.getLogger().debug(
                "Altered catalog {0} entry: #{1} {2}",
                        className, ID, properties
                );
                connection.releaseSavepoint(savepoint);
                connection.commit();
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to update catalog entry: {0}", e.getMessage());
                try {
                    Logger.getLogger().warn("Perform rollback");
                    connection.rollback(savepoint);
                } catch (SQLException e1) {
                    Logger.getLogger().error("Unable to rollback database", e1);
                }
                throw e;
            }
        }
    }
    
    @Override
    public boolean isInstanceExists(Class clazz, Integer ID) {
        return !readClassInstance(clazz, ID).isEmpty();
    }
    
    @Override
    public boolean isInstanceExists(Class clazz, String PID, Integer ownerId) {
        return !readClassInstance(clazz, PID, ownerId).isEmpty();
    }
    
    @Override
    public Map<String, String> readClassInstance(Class clazz, Integer ID) {
        Map<String, String> rowData = new LinkedHashMap<>();
        final String className = clazz.getSimpleName().toUpperCase();
        if (tableRegistry.containsKey(className)) {
            final String selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE [ID] = ?", className);
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
        Map<String, String> rowData = new LinkedHashMap<>();
        final String className = clazz.getSimpleName().toUpperCase();
        if (tableRegistry.containsKey(className)) {
            final String selectSQL;
            if (ownerId != null) {
                selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE [PID] = ? AND [OWN] = ?", className);
            } else {
                selectSQL = MessageFormat.format("SELECT * FROM {0} WHERE [PID] = ? AND [OWN] IS NULL", className);
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
    public <E extends Entity> List<EntityRef<E>> readCatalogEntries(Integer ownerId, Class<E> entityClass) {
        final List<EntityRef<E>> references = new LinkedList<>();
        final String className  = entityClass.getSimpleName().toUpperCase();

        if (tableRegistry.containsKey(className)) {
            final String selectSQL;
            if (ownerId != null) {
                selectSQL = MessageFormat.format("SELECT [ID] FROM {0} WHERE [OWN] = ? ORDER BY [SEQ]", className);
            } else {
                selectSQL = MessageFormat.format("SELECT [ID] FROM {0} WHERE [OWN] IS NULL ORDER BY [SEQ]", className);
            }
            try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                select.setFetchSize(10);
                if (ownerId != null) {
                    select.setInt(1, ownerId);
                }
                try (ResultSet selectRS = select.executeQuery()) {
                    while (selectRS.next()) {
                        references.add(EntityRef.build(entityClass, selectRS.getInt(1)));
                    }
                } catch (SQLException e) {
                    Logger.getLogger().error("Unable to read catalog", e);
                }
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to read catalog", e);
            }
        }
        return references;
    }

    @Override
    public <E extends Entity> List<EntityRef<E>> readCatalogEntries(Class<E> entityClass) {
        final List<EntityRef<E>> references = new LinkedList<>();
        final String className  = entityClass.getSimpleName().toUpperCase();

        if (tableRegistry.containsKey(className)) {
            final String selectSQL = MessageFormat.format("SELECT [ID] FROM {0} ORDER BY [SEQ]", className);
            try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                select.setFetchSize(10);
                try (ResultSet selectRS = select.executeQuery()) {
                    while (selectRS.next()) {
                        references.add(EntityRef.build(entityClass, selectRS.getInt(1)));
                    }
                } catch (SQLException e) {
                    Logger.getLogger().error("Unable to read catalog", e);
                }
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to read catalog", e);
            }
        }
        return references;
    }

    @Override
    public List<ForeignLink> findReferencedEntries(Class clazz, Integer ID) {        
        List<ForeignLink> links = new LinkedList<>();
        final String className = clazz.getSimpleName().toUpperCase();
        
        tableRegistry.values().stream()
                .filter((tableInfo) -> {
                    return
                            !tableInfo.name.equals(className) &&
                            tableInfo.refInfos.stream().anyMatch((refInfo) -> refInfo.pkTable.equals(className));
                }).forEach((tableInfo) -> {
                    String fkColumnName = tableInfo.refInfos.stream()
                            .filter((refInfo) -> refInfo.pkTable.equals(className))
                            .map((refInfo) -> refInfo.fkColumn).findFirst()
                            .get();
                    
                    String selectSQL = MessageFormat.format(
                            "SELECT [TABLE_CLASS], [ID], [PID] FROM {0}, CLASSDEF WHERE [{1}] = ? AND [TABLE_NAME] = ?",
                            tableInfo.name, fkColumnName
                    );
                    try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
                        select.setInt(1, ID);
                        select.setString(2, tableInfo.name.toUpperCase());
                        try (ResultSet selectRS = select.executeQuery()) {
                            while (selectRS.next()) {
                                Class<? extends Entity> entityClass;
                                String entityClassName = selectRS.getString(1);
                                try {
                                    entityClass = Class.forName(entityClassName).asSubclass(Entity.class);
                                    links.add(new ForeignLink(
                                            entityClass,
                                            selectRS.getInt(2),
                                            selectRS.getString(3),
                                            !fkColumnName.toUpperCase().equals(EntityModel.OWN))
                                    );
                                } catch (ClassNotFoundException e) {
                                    Logger.getLogger().warn("Class '{0}' is not found", entityClassName);
                                }
                            }
                        }
                    } catch (SQLException e) {/**/}
                });
        return links;
    }    
    
    @Override
    public  synchronized void removeClassInstance(Class clazz, Integer ID) throws Exception {        
        final String className = clazz.getSimpleName().toUpperCase();
        String PID = readClassInstance(clazz, ID).get(EntityModel.PID);
        final String deleteSQL = MessageFormat.format("DELETE FROM {0} WHERE [ID] = ?", className);

        Logger.getContextLogger(QueryContext.class).debug(
                "Delete query: {0}",
                IDatabaseAccessService.prepareTraceSQL(deleteSQL, ID)
        );
        
        Savepoint savepoint = connection.setSavepoint(className);
        try (
            PreparedStatement delete = connection.prepareStatement(deleteSQL)
        ) {
            delete.setInt(1, ID);
            delete.executeUpdate();
            
            connection.releaseSavepoint(savepoint);
            connection.commit();
            Logger.getLogger().debug(MessageFormat.format(
                    "Deleted catalog {0} entry: #{1}-{2}", className, ID, PID
            ));
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to delete class catalog entry: {0}", e.getMessage());
            try {
                Logger.getLogger().warn("Perform rollback");
                connection.rollback(savepoint);
            } catch (SQLException e1) {
                Logger.getLogger().error("Unable to rollback database", e1);
            }
            throw e;
        }
    }
    
    @Override
    public Class<? extends Entity> getOwnerClass(Class clazz) throws Exception {
        final String className = clazz.getSimpleName().toUpperCase();
        if (!tableRegistry.containsKey(className)) {
            buildClassCatalog(clazz, new HashMap<String, IComplexType>() {{
                put(EntityModel.OWN, new EntityRef<>(null));
            }});
        }
        Optional<ReferenceInfo> ownReference = tableRegistry.get(className).refInfos.stream()
                .filter((refInfo) -> EntityModel.OWN.equals(refInfo.fkColumn))
                .findFirst();
        if (ownReference.isPresent()) {
            return getCatalogClass(ownReference.get().pkTable);
        } else {
            throw new IllegalStateException("Column OWN is not a foreign key");
        }
    }
    
    @Override
    public synchronized void maintainClassCatalog(Class clazz, List<String> unusedProperties, Map<String, IComplexType> newProperties) throws Exception {
        final String  className = clazz.getSimpleName().toUpperCase();
        final List<String> queries = new LinkedList<>();
        
        final List<String> added    = new LinkedList<>();
        final List<String> deleted  = new LinkedList<>();
        final List<String> modified = new LinkedList<>();

        boolean needRebuild =
                !unusedProperties.isEmpty() ||
                tableRegistry.get(className).columnInfos.stream()
                        .anyMatch(columnInfo -> newProperties.containsKey(columnInfo.name));

        if (needRebuild) {
            Map<String, String> columns = new LinkedHashMap<>();
            for (ColumnInfo columnInfo : tableRegistry.get(className).columnInfos) {
                if (!columns.containsKey(columnInfo.name)) {
                    if (unusedProperties.contains(columnInfo.name)) {
                        deleted.add(columnInfo.name);
                    } else if (newProperties.containsKey(columnInfo.name)) {
                        columns.put(
                                columnInfo.name,
                                getColumnDefinition(
                                        newProperties.get(columnInfo.name),
                                        !columnInfo.name.equals(EntityModel.OWN)
                                )
                        );
                        modified.add(columnInfo.name);
                    } else {
                        columns.put(columnInfo.name, tableRegistry.get(className).getColumnDefinition(columnInfo));
                    }
                }
            }
            for (Map.Entry<String, IComplexType> entry : newProperties.entrySet()) {
                if (!columns.containsKey(entry.getKey())) {
                    columns.put(entry.getKey(), getColumnDefinition(entry.getValue(), true));
                    added.add(entry.getKey());
                }
            }
            String nameFormat = "%-".concat(
                Integer.toString(
                        columns.keySet().stream().mapToInt(String::length).max().orElse(0)
                ).concat("s ")
            );
            queries.add(MessageFormat.format(
                "CREATE TABLE IF NOT EXISTS {0} (\n\t{1}\n)",
                className.concat("_NEW"),
                    columns.entrySet().stream()
                        .map((entry) -> String.format(nameFormat, "["+entry.getKey()+"]").concat(entry.getValue()))
                        .collect(Collectors.joining(",\n\t"))
            ));

            try (
                PreparedStatement select = connection.prepareStatement(MessageFormat.format("SELECT * FROM {0}", className));
                ResultSet rs = select.executeQuery()
            ) {
                if (rs.next()) {
                    queries.add(MessageFormat.format("INSERT INTO {0} ({1}) SELECT {1} FROM {2}",
                        className.concat("_NEW"),
                            tableRegistry.get(className).columnInfos.stream()
                                .filter((info) -> !unusedProperties.contains(info.name))
                                .map((info) -> "["+info.name+"]")
                                .collect(Collectors.joining(", ")),
                        className
                    ));
                }
            }
            queries.add(MessageFormat.format(
                    "DROP TABLE IF EXISTS {0}",
                    className
            ));
            queries.add(MessageFormat.format(
                    "ALTER TABLE {0} RENAME TO {1}",
                    className.concat("_NEW"),
                    className
            ));
            queries.add(MessageFormat.format(
                    "CREATE UNIQUE INDEX IF NOT EXISTS IDX_{0}_PID_OWN ON {0} (PID, OWN)",
                    className
            ));
        } else {
            Map<String, String> columns = new LinkedHashMap<>();
            for (Map.Entry<String, IComplexType> entry : newProperties.entrySet()) {
                if (!columns.containsKey(entry.getKey())) {
                    columns.put(entry.getKey(), getColumnDefinition(entry.getValue(), true));
                    added.add(entry.getKey());
                }
            }
            queries.addAll(columns.entrySet().stream()
                    .map((entry) -> {
                        return MessageFormat.format(
                            "ALTER TABLE {0} ADD COLUMN {1}",
                            className, MessageFormat.format("[{0}] {1}", entry.getKey(), entry.getValue())
                        );
                    }).collect(Collectors.toList()));
        }

        queries.add(generateTriggerCode(className, TriggerKind.Before_Insert));
        queries.add(generateTriggerCode(className, TriggerKind.Before_Update));

        Logger.getContextLogger(QueryContext.class).debug(
                "Alter table queries:\n{0}",
                queries.stream()
                    .map((query) -> "[".concat(Integer.toString(queries.indexOf(query)+1).concat("] ").concat(query)))
                    .collect(Collectors.joining("\n"))
        );
        
        if (needRebuild) {
            connection.setAutoCommit(true);
            connection.createStatement().executeUpdate("PRAGMA foreign_keys = OFF");
            connection.setAutoCommit(false);
        }
        Savepoint savepoint = connection.setSavepoint(className);
        try (
            final Statement statement = connection.createStatement();
        ) {
            for (String query : queries) {
                statement.execute(query);
            }
            connection.releaseSavepoint(savepoint);
            connection.commit();
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to maintain class catalog: {0}", e.getMessage());
            try {
                Logger.getLogger().warn("Perform rollback");
                connection.rollback(savepoint);
            } catch (SQLException e1) {
                Logger.getLogger().error("Unable to rollback database", e1);
            }
            throw e;
        } finally {
            if (needRebuild) {
                connection.setAutoCommit(true);
                connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
                connection.setAutoCommit(false);
            }
        }

        StringJoiner joiner = new StringJoiner("\n");
        if (!added.isEmpty()) {
            joiner.add(MessageFormat.format(
                    "Added columns:    {0}", 
                    added
            ));
        }
        if (!modified.isEmpty()) {
            joiner.add(MessageFormat.format(
                    "Modified columns: {0}", 
                    modified
            ));
        }
        if (!deleted.isEmpty()) {
            joiner.add(MessageFormat.format(
                    "Deleted columns:  {0}", 
                    deleted
            ));
        }
        tableRegistry.put(className, new TableInfo(className));

        boolean dumpContextAllowed = Logger.contextAllowed(Collections.singletonList(DumpContext.class), Level.Debug);
        Logger.getContextLogger(DdlContext.class).debug(
        "Catalog {0} maintenance complete:\n{1}{2}",
                className,
                joiner.toString(),
                dumpContextAllowed ? "\n".concat(tableRegistry.get(className).toString()) : ""
        );
    }

    @Override
    public boolean deleteConfirmRequired() {
        return getSettings().isConfirmRequired();
    }

//    public void exportConfiguration(Exporter exporter) {
//        try {
//            exporter.loadEntities(getClassCatalogs().entrySet().stream()
//                    .filter(entry ->!Catalog.class.isAssignableFrom(entry.getValue()))
//                    .filter(entry -> exporter.getClassFilter().test(entry.getValue()))
//                    .collect(
//                            LinkedHashMap::new,
//                            (map, entry) -> {
//                                List<Entity> entities = readCatalogIDs(entry.getValue()).stream()
//                                        .map(ID -> EntityRef.build(entry.getValue(), ID).getValue())
//                                        .filter(entity ->
//                                                exporter.getEntityFilter().test(entity) &&
//                                                entity.getParent() != null &&
//                                                entity.getParent().allowModifyChild()
//                                        )
//                                        .collect(Collectors.toCollection(LinkedList::new));
//                                if (!entities.isEmpty()) {
//                                    map.put(entry.getValue(), entities);
//                                }
//                            },
//                            Map::putAll
//                    )
//            );
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private void initClassDef() {
        String createSQL = "CREATE TABLE IF NOT EXISTS CLASSDEF (TABLE_NAME TEXT, TABLE_CLASS TEXT)";
        try (final Statement statement = connection.createStatement()) {
            statement.execute(createSQL);
            connection.commit();
        } catch (SQLException e) {
            Logger.getContextLogger(DdlContext.class).error("Unable to create class definition table", e);
        }
    }

    private void maintainClassDef() {
        List<String> queries = new LinkedList<>();
        List<String> classNotExists = new LinkedList<>();
        List<String> tableNotExists = new LinkedList<>();

        String selectTables = "SELECT TABLE_NAME, TABLE_CLASS FROM CLASSDEF";
        try (final PreparedStatement select = connection.prepareStatement(selectTables)) {
            try (ResultSet selectRS = select.executeQuery()) {
                while (selectRS.next()) {
                    String objectTable = selectRS.getString("TABLE_NAME");
                    String objectClass = selectRS.getString("TABLE_CLASS");
                    try {
                        Class.forName(objectClass);
                        if (!tableRegistry.containsKey(objectTable)) {
                            tableNotExists.add(objectTable);
                            throw new ClassNotFoundException();
                        }
                    } catch (ClassNotFoundException e) {
                        if (!tableNotExists.contains(objectTable)) {
                            classNotExists.add(objectTable);
                        }
                        queries.add(MessageFormat.format(
                                "DROP TABLE IF EXISTS {0}",
                                objectTable
                        ));
                        queries.add(MessageFormat.format(
                                "DELETE FROM CLASSDEF WHERE [TABLE_NAME] = ''{0}''",
                                objectTable
                        ));
                    }
                }
            }
            Savepoint savepoint = connection.setSavepoint("CLASSDEF");
            try (final Statement delete = connection.createStatement()) {
                for (String query : queries) {
                    delete.execute(query);
                }
                connection.releaseSavepoint(savepoint);
                connection.commit();

                Logger.getContextLogger(DdlContext.class).debug(
                        "Class definition maintenance complete:\nObsolete classes: {0}\nObsolete tables:  {1}",
                        classNotExists,
                        tableNotExists
                );
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to maintain class definition table: {0}", e.getMessage());
                try {
                    Logger.getLogger().warn("Perform rollback");
                    connection.rollback(savepoint);
                } catch (SQLException e1) {
                    Logger.getLogger().error("Unable to rollback database", e1);
                }
                throw e;
            }
        } catch (SQLException e) {
            Logger.getContextLogger(DdlContext.class).error("Unable to read class definitions", e);
        }
    }
    
    private Class<? extends Entity> getCatalogClass(String tableName) throws Exception {
        String selectSQL = "SELECT [TABLE_CLASS] FROM CLASSDEF WHERE [TABLE_NAME] = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setString(1, tableName.toUpperCase());
            try (ResultSet selectRS = select.executeQuery()) {
                if (selectRS.next()) {
                    return Class.forName(selectRS.getString(1)).asSubclass(Entity.class);
                }
            }
        }
        throw new IllegalStateException("Catalog class could not be identified");
    }
    
    private String getColumnDefinition(IComplexType propVal, boolean nullable) throws Exception {
        StringJoiner joiner = new StringJoiner(" ");
        
        if (propVal instanceof EntityRef) {
            Class refClazz;
            if ((refClazz = ((EntityRef) propVal).getEntityClass()) != null) {
                if (!tableRegistry.containsKey(refClazz.getSimpleName().toUpperCase())) {
                    buildClassCatalog(refClazz, new HashMap<String, IComplexType>() {{
                        put(EntityModel.OWN, new EntityRef<>(null));
                    }});
                }
                joiner.add(
                        "INTEGER REFERENCES "
                        .concat(refClazz.getSimpleName().toUpperCase())
                        .concat("(ID)")
                );
                if (!nullable) {
                    joiner.add("NOT NULL");
                }
            } else {
                joiner.add("INTEGER");
            }
        } else {
            joiner.add("TEXT");
        }
        return joiner.toString();
    }
    
    private String generateTriggerCode(String tableName, TriggerKind kind) {
        return MessageFormat.format(
                "CREATE TRIGGER IF NOT EXISTS {0}_{2}_CHECK_OWN\n" +
                "   {1} ON {2}\n" +
                "   WHEN NEW.OWN IS NULL\n" +
                "   BEGIN\n" +
                "       SELECT CASE WHEN(\n" +
                "           (SELECT ID FROM {2} WHERE OWN IS NULL AND PID == NEW.PID) != NEW.ID\n" +
                "       ) THEN RAISE (ABORT, \"Columns PID, OWN are not unique\") \n" +
                "   END;\n" +
                "END;",
                kind.name().toUpperCase(),
                kind.value,
                tableName
        );
    }

    private class TableInfo {
        
        final String name;
        final String pkey;
        final List<ColumnInfo>    columnInfos;
        final List<IndexInfo>     indexInfos;
        final List<ReferenceInfo> refInfos;
        
        TableInfo(String tableName) throws Exception {
            this.name = tableName;
            this.pkey = getPrimaryKey(tableName);
            this.columnInfos = getColumnInfos(tableName);
            this.indexInfos  = getIndexInfos(tableName);
            this.refInfos    = getReferenceInfos(tableName);
        }
        
        final String getPrimaryKey(String tableName) throws Exception {
            try (ResultSet rs = connection.getMetaData().getPrimaryKeys(null, null, tableName)) {
                if (rs.next()) {
                    return rs.getString("COLUMN_NAME");
                } else {
                    return "<not defined>";
                }
            }
        }
        
        final List<ColumnInfo> getColumnInfos(String tableName) throws Exception {
            List<ColumnInfo> columnInfoList = new LinkedList<>();
            
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    columnInfoList.add(new ColumnInfo(
                            rs.getString("COLUMN_NAME"), 
                            rs.getString("TYPE_NAME"), 
                            "YES".equals(rs.getString("IS_NULLABLE"))
                    ));
                }
            }
            return columnInfoList;
        }
        
        final List<IndexInfo> getIndexInfos(String tableName) throws Exception {
             List<IndexInfo> indexInfoList = new LinkedList<>();
            
            try (ResultSet rs = connection.getMetaData().getIndexInfo(null, null, tableName, true, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    indexInfoList.stream()
                            .filter((info) -> info.name.equals(indexName))
                            .findFirst()
                            .orElseGet(() -> {
                                IndexInfo indexInfo = new IndexInfo(indexName);
                                indexInfoList.add(indexInfo);
                                return indexInfo;
                            })
                            .addColumn(rs.getString("COLUMN_NAME"));
                }
            }
            return indexInfoList;
        }
        
        final List<ReferenceInfo> getReferenceInfos(String tableName) throws Exception {
            List<ReferenceInfo> refInfoList = new LinkedList<>();
            try (ResultSet rs = connection.getMetaData().getImportedKeys(null, null, tableName)) {
                while (rs.next()) {
                    refInfoList.add(new ReferenceInfo(
                            rs.getString("PKTABLE_NAME"), 
                            rs.getString("PKCOLUMN_NAME"), 
                            rs.getString("FKCOLUMN_NAME")
                    ));
                }
            }
            return refInfoList;
        }
        
        final String getColumnDefinition(ColumnInfo info) {
            Optional<ReferenceInfo> ref = refInfos.stream()
                    .filter((refInfo) -> info.name.equals(refInfo.fkColumn))
                    .findFirst();
            
            if (info.name.equals(pkey.toUpperCase())) {
                return "INTEGER PRIMARY KEY AUTOINCREMENT";
            } else if (ref.isPresent()) {
                return info.type
                       .concat(" ")
                       .concat(MessageFormat.format(
                               "REFERENCES {0} ({1}) ",
                               ref.get().pkTable, ref.get().pkColumn
                       ))
                       .concat(info.nullable ? "" : "NOT NULL");
            } else {
                return info.type.concat(info.nullable ? "" : " NOT NULL");
            }
        }
        
        final List<String> getTriggerNames() {
            List<String> names = new LinkedList<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT NAME FROM sqlite_master WHERE TYPE = ? AND TBL_NAME = ?")) {
                select.setString(1, "trigger");
                select.setString(2, name);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        names.add(rs.getString("NAME"));
                    }
                }
            } catch (SQLException e) {/**/}
            return names;
        }
        
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            String nameFormat = "%-".concat(
                    Integer.toString(
                            columnInfos.stream()
                                .mapToInt((columnInfo) -> columnInfo.name.length())
                                .max().orElse(0)
                    ).concat("s ")
            );
            builder.append(MessageFormat.format(
                    "Columns:\n{0}", 
                    columnInfos.stream().map((columnInfo) -> {
                        return " * "
                                .concat(
                                        columnInfo.name.equals(pkey.toUpperCase()) ? "[PK] " : (
                                                refInfos.stream().anyMatch((refInfo) -> columnInfo.name.equals(refInfo.fkColumn)) ? "[FK] " : "     "
                                        )
                                )
                                .concat(String.format(nameFormat, columnInfo.name))
                                .concat(getColumnDefinition(columnInfo));
                    }).collect(Collectors.joining("\n"))
            ));
            if (!indexInfos.isEmpty()) {
                builder.append(MessageFormat.format(
                        "\nIndexes:\n{0}", 
                        indexInfos.stream().map((indexInfo) -> " * "
                                .concat(indexInfo.name)
                                .concat(" (").concat(String.join(", ", indexInfo.columns)).concat(")")).collect(Collectors.joining("\n"))
                ));
            }
            if (!getTriggerNames().isEmpty()) {
                builder.append(MessageFormat.format(
                        "\nTriggers:\n{0}", 
                        getTriggerNames().stream()
                                .map(" * "::concat)
                                .collect(Collectors.joining("\n"))
                ));
            }
            return builder.toString();
        }
    }


    private class ColumnInfo {
        
        final String  name;
        final String  type;
        final Boolean nullable;
        
        ColumnInfo(String name, String type, Boolean nullable) {
            this.name = name;
            this.type = type;
            this.nullable = Boolean.TRUE.equals(nullable);
        }

    }


    private class IndexInfo {
        
        final String name;
        final List<String> columns = new LinkedList<>();
        
        IndexInfo(String name) {
            this.name = name;
        }
        
        void addColumn(String name) {
            columns.add(name);
        }
        
    }


    private class ReferenceInfo {
        
        final String pkTable;
        final String pkColumn;
        final String fkColumn;
        
        ReferenceInfo(String pkTable, String pkColumn, String fkColumn) {
            this.pkTable  = pkTable;
            this.pkColumn = pkColumn;
            this.fkColumn = fkColumn;
        }
        
    }


    private enum TriggerKind {
        
        Before_Insert("BEFORE INSERT"), 
        Before_Update("BEFORE UPDATE");
        
        final String value;
        
        TriggerKind(String value) {
            this.value = value;
        }
    }
}