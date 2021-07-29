package codex.database;

import codex.context.IContext;
import codex.log.Logger;
import java.sql.*;
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

    private final static int PARAM_MIN_POOL_SIZE = 0;
    private final static int PARAM_MAX_POOL_SIZE = 50;
    private final static int PARAM_WAIT_TIMEOUT  = 10;

    private final static OracleAccessService INSTANCE = new OracleAccessService();
    
    /**
     * Возвращает экземпляр сервиса (синглтон). 
     */
    public static OracleAccessService getInstance() {
        return INSTANCE;
    }

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "DAS.Sql", name = "Preview SQL queries", icon = "/images/command.png", parent = OracleAccessService.class)
    private static class QueryContext implements IContext {}

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "DAS.Ucp", name = "Connections pool status", icon = "/images/ucp.png", parent = OracleAccessService.class)
    private static class UCPContext implements IContext {}
    
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

                    pds.setInitialPoolSize(PARAM_MIN_POOL_SIZE);
                    pds.setMinPoolSize(PARAM_MIN_POOL_SIZE);
                    pds.setMaxPoolSize(PARAM_MAX_POOL_SIZE);
                    pds.setInactiveConnectionTimeout(PARAM_WAIT_TIMEOUT);
                    pds.setTimeoutCheckInterval(1);

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
            Logger.getContextLogger(QueryContext.class).debug(
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
                    "Unable to execute query: {0}\nQuery:{1}",
                    e.getMessage().trim(),
                    IDatabaseAccessService.prepareTraceSQL(query, params)
            );
            throw new SQLException(
                    getCause(e).getMessage().trim(),
                    e.getSQLState(),
                    e.getErrorCode()
            );
        }
    }

    @Override
    public synchronized void update(Integer connectionID, String query, Object... params) throws SQLException {
        Logger.getContextLogger(QueryContext.class).debug(
                "Update query: {0} (connection #{1})",
                IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
        );
        Connection connection = idToPoolMap.get(connectionID).getConnection();
        PoolDataSource dataSource = (PoolDataSource) idToPoolMap.get(connectionID);
        Logger.getContextLogger(UCPContext.class).debug(
                "UCP usage state: busy={0}, avail={1}, max={2}",
                dataSource.getBorrowedConnectionsCount(),
                dataSource.getAvailableConnectionsCount(),
                dataSource.getMaxPoolSize()
        );

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

    @Override
    public PreparedStatement prepareStatement(Integer connectionID, String query, Object... params) throws SQLException {
        Logger.getContextLogger(QueryContext.class).debug(
                "Prepare statement: {0} (connection #{1})",
                IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
        );
        Connection connection = idToPoolMap.get(connectionID).getConnection();
        PoolDataSource dataSource = (PoolDataSource) idToPoolMap.get(connectionID);
        Logger.getContextLogger(UCPContext.class).debug(
                "UCP usage state: busy={0}, avail={1}, max={2}",
                dataSource.getBorrowedConnectionsCount(),
                dataSource.getAvailableConnectionsCount(),
                dataSource.getMaxPoolSize()
        );

        PreparedStatement statement = connection.prepareStatement(query);
        if (params != null) {
            int paramIdx = 0;
            for (Object param : params) {
                paramIdx++;
                statement.setObject(paramIdx, param);
            }
        }
        return statement;
    }

    @Override
    public CallableStatement prepareCallable(Integer connectionID, String query) throws SQLException {
        Connection connection = idToPoolMap.get(connectionID).getConnection();
        PoolDataSource dataSource = (PoolDataSource) idToPoolMap.get(connectionID);
        Logger.getContextLogger(UCPContext.class).debug(
                "UCP usage state: busy={0}, avail={1}, max={2}",
                dataSource.getBorrowedConnectionsCount(),
                dataSource.getAvailableConnectionsCount(),
                dataSource.getMaxPoolSize()
        );
        return connection.prepareCall(query);
    }

    private RowSet prepareSet(Integer connectionID) throws SQLException {
        Connection connection = idToPoolMap.get(connectionID).getConnection();
        PoolDataSource dataSource = (PoolDataSource) idToPoolMap.get(connectionID);
        Logger.getContextLogger(UCPContext.class).debug(
                "UCP usage state: busy={0}, avail={1}, max={2}",
                dataSource.getBorrowedConnectionsCount(),
                dataSource.getAvailableConnectionsCount(),
                dataSource.getMaxPoolSize()
        );
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
