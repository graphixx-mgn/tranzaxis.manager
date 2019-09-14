package codex.database;

import codex.context.IContext;
import codex.log.Level;
import codex.log.Logger;
import java.sql.*;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import javax.sql.RowSet;
import javax.sql.RowSetEvent;
import javax.sql.RowSetListener;
import codex.log.LoggingSource;
import codex.service.AbstractService;
import oracle.jdbc.rowset.OracleJDBCRowSet;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * Реализация сервиса взаимодействия с базой данных Oracle с поддержкой пула 
 * соедиений.
 */
@IContext.Definition(id = "DAS", name = "Database Access Service", icon = "/images/database.png")
public class OracleAccessService extends AbstractService<OracleAccessOptions> implements IDatabaseAccessService, IContext {
    
    private final static OracleAccessService INSTANCE = new OracleAccessService();
    
    /**
     * Возвращает экземпляр сервиса (синглтон). 
     */
    public static OracleAccessService getInstance() {
        return INSTANCE;
    }

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "DAS.Sql", name = "Preview SQL queries", icon = "/images/command.png", parent = OracleAccessService.class)
    private static class QueryContext implements IContext {
        static void debug(String message, Object... params) {
            Logger.getLogger().log(Level.Debug, MessageFormat.format(message, params));
        }
    }
    
    private OracleAccessService() {}
    
    private final AtomicInteger SEQ = new AtomicInteger(0);
    private final Map<String, Integer>     urlToIdMap = new HashMap<>();
    private final Map<Integer, DataSource> idToPoolMap = new HashMap<>();
    
    @Override
    public Integer registerConnection(String url, String user, String password) throws SQLException {
        synchronized (urlToIdMap) {
            String PID = url+"~"+user+"~"+password;
            if (!urlToIdMap.containsKey(PID)) {
                try {
                    PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
                    pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");

                    pds.setURL(url);
                    pds.setUser(user);
                    pds.setPassword(password);

                    pds.setInitialPoolSize(1);
                    pds.setMinPoolSize(1);
                    pds.setMaxPoolSize(5);

                    urlToIdMap.put(PID, SEQ.incrementAndGet());
                    idToPoolMap.put(SEQ.get(), pds);
                    Logger.getLogger().debug("Registered new connection #{0}: URL={1}, User={2}", SEQ.get(), url, user);
                    return SEQ.get();
                } catch (SQLException e) {
                    throw new SQLException(getCause(e).getMessage().trim());
                }
            } else {
                return urlToIdMap.get(PID);
            }
        }
    }
    
    @Override
    public ResultSet select(Integer connectionID, String query, Object... params) throws SQLException {
        try {
            final RowSet rowSet = prepareSet(connectionID);
            QueryContext.debug(
                    "Select query: {0} (connection #{1})",
                    IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
            );
            rowSet.setCommand(query);
            if (params != null) {
                int paramIdx = 0;
                for (Object param : params) {
                    paramIdx++;
                    rowSet.setObject(paramIdx, param);
                }
            }
            rowSet.execute();
            return rowSet;
        } catch (SQLException e) {
            Logger.getLogger().error(
                    "Unable to execute query: {0}{1}",
                    e.getMessage(),
                    IDatabaseAccessService.prepareTraceSQL(query, params)
            );
            throw new SQLException(getCause(e).getMessage().trim());
        }
    }

    @Override
    public synchronized void update(Integer connectionID, String query, Object... params) throws SQLException {
        QueryContext.debug(
                "Update query: {0} (connection #{1})",
                IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
        );
        Connection connection = idToPoolMap.get(connectionID).getConnection();
        connection.setAutoCommit(false);
        Savepoint savepoint = connection.setSavepoint();

        try (
                PreparedStatement update = connection.prepareStatement(query)
        ) {
            if (params != null) {
                int paramIdx = 0;
                for (Object param : params) {
                    paramIdx++;
                    update.setObject(paramIdx, param);
                }
            }
            update.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            Logger.getLogger().error(
                    "Unable to execute update query: {0}{1}",
                    e.getMessage(),
                    IDatabaseAccessService.prepareTraceSQL(query, params)
            );
            try {
                Logger.getLogger().warn("Perform rollback");
                connection.rollback(savepoint);
            } catch (SQLException e1) {
                Logger.getLogger().error("Unable to rollback database", e1);
            }
            throw new SQLException(getCause(e).getMessage().trim());
        } finally {
            connection.setAutoCommit(true);
            connection.close();
        }
    }

    private RowSet prepareSet(Integer connectionID) throws SQLException {
        Connection connection = idToPoolMap.get(connectionID).getConnection();
        final RowSet rowSet = new OracleJDBCRowSet(connection);
        rowSet.addRowSetListener(new RowSetAdapter() {                
            @Override
            public void cursorMoved(RowSetEvent event) {
                try {
                    if (rowSet.isAfterLast() && !rowSet.isClosed()) {
                        rowSet.close();
                        connection.close();
                    }
                } catch (SQLException e) {
                    //
                }
            }
        });
        return rowSet;
    }
    
    private Throwable getCause(Throwable exception) {
        Throwable throwable = exception;
        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
    
    private abstract class RowSetAdapter implements RowSetListener {

        @Override
        public void rowSetChanged(RowSetEvent event) {}

        @Override
        public void rowChanged(RowSetEvent event) {}

        @Override
        public void cursorMoved(RowSetEvent event) {}
    }
    
}
