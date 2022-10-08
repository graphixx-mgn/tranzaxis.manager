package codex.database;

import codex.context.IContext;
import codex.log.Logger;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private enum Mode {
        Legacy, DataSource
    }
    private final static Mode MODE = Mode.DataSource;
    private final static OracleAccessService INSTANCE = new OracleAccessService();

    private final static int PARAM_MIN_POOL_SIZE = 0;
    private final static int PARAM_MAX_POOL_SIZE = 100;
    private final static int PARAM_WAIT_TIMEOUT  = (int) PARAM_MAX_POOL_SIZE / 10;

    /**
     * Возвращает экземпляр сервиса (синглтон). 
     */
    public static OracleAccessService getInstance() {
        return INSTANCE;
    }

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "DAS.Sql", name = "Preview SQL queries", icon = "/images/sql.png", parent = OracleAccessService.class)
    private static class QueryContext implements IContext {}

    @LoggingSource(debugOption = true)
    @IContext.Definition(id = "DAS.Ucp", name = "Connections pool status", icon = "/images/ucp.png", parent = OracleAccessService.class)
    private static class UCPContext implements IContext {}


    private static class Credential {
        private final String url, user, pass;
        private Credential(String url, String user, String pass) {
            this.url  = url;
            this.user = user;
            this.pass = pass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Credential that = (Credential) o;
            return url.equals(that.url) && user.equals(that.user) && pass.equals(that.pass);
        }
        @Override
        public int hashCode() {
            return Objects.hash(url, user, pass);
        }
    }

    private OracleAccessService() {}
    
    private final AtomicInteger SEQ = new AtomicInteger(0);
    private final Map<Integer, Credential> CREDENTIAL_MAP = new HashMap<>();
    private final Map<Integer, Connection> CONNECTION_MAP = new HashMap<>();
    private final Map<Integer, DataSource> DATASOURCE_MAP = new HashMap<>();

    @Override
    public Integer registerConnection(String url, String user, String password) throws SQLException {
        synchronized (SEQ) {
            Credential credential = new Credential(url, user, password);
            Optional<Integer> ID_OLD = CREDENTIAL_MAP.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(credential))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (ID_OLD.isPresent()) {
                return ID_OLD.get();
            } else {
                SEQ.incrementAndGet();
                CREDENTIAL_MAP.put(SEQ.get(), credential);

                if (MODE.equals(Mode.Legacy)) {
                    Connection conn = DriverManager.getConnection(url, user, password);
                    CONNECTION_MAP.put(SEQ.get(), conn);
                } else {
                    PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
                    pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");

                    pds.setURL(url);
                    pds.setUser(user);
                    pds.setPassword(password);

                    pds.setInitialPoolSize(PARAM_MIN_POOL_SIZE);
                    pds.setMinPoolSize(PARAM_MIN_POOL_SIZE);
                    pds.setMaxPoolSize(PARAM_MAX_POOL_SIZE);

                    pds.setTimeoutCheckInterval(1);
                    pds.setInactiveConnectionTimeout(PARAM_WAIT_TIMEOUT);

                    pds.setMaxConnectionReuseTime(PARAM_WAIT_TIMEOUT);
                    pds.setMaxConnectionReuseCount(PARAM_MAX_POOL_SIZE);

                    DATASOURCE_MAP.put(SEQ.get(), pds);
                }
                Logger.getLogger().debug("Registered new connection #{0}: URL={1}, User={2}", SEQ.get(), url, user);
                return SEQ.get();
            }
        }
    }

    private Connection getConnection(Integer ID) throws SQLException {
        synchronized (SEQ) {
            Connection connection = null;
            if (MODE.equals(Mode.Legacy)) {
                if (CONNECTION_MAP.containsKey(ID)) {
                    connection = CONNECTION_MAP.get(ID);
                    try {
                        if (connection.isClosed()) {
                            Credential credential = CREDENTIAL_MAP.get(ID);
                            connection = DriverManager.getConnection(credential.url, credential.user, credential.pass);
                            CONNECTION_MAP.put(ID, connection);
                        }
                    } catch (SQLException ignore) {}
                }
            } else {
                if (DATASOURCE_MAP.containsKey(ID)) {
                    PoolDataSource dataSource = (PoolDataSource) DATASOURCE_MAP.get(ID);
                    Logger.getContextLogger(UCPContext.class).debug(
                            "UCP usage state: busy={0}, avail={1}, max={2}",
                            dataSource.getBorrowedConnectionsCount(),
                            dataSource.getAvailableConnectionsCount(),
                            dataSource.getMaxPoolSize()
                    );
                    connection = dataSource.getConnection();
                }
            }
            if (connection == null) {
                throw new SQLException("Connection #" + ID + " not exists");
            }
            return connection;
        }
    }
    
    @Override
    public ResultSet select(Integer connectionID, String query, Object... params) throws SQLException {
        try {
            if (connectionID == null) throw new SQLException("Connection is not opened");
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
            if (e.getErrorCode() != 0) Logger.getLogger().error(
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
        if (connectionID == null) throw new SQLException("Connection is not opened");
        Logger.getContextLogger(QueryContext.class).debug(
                "Update query: {0} (connection #{1})",
                IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
        );
        Connection connection = getConnection(connectionID);
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
        }
    }

    @Override
    public PreparedStatement prepareStatement(Integer connectionID, String query, Object... params) throws SQLException {
        if (connectionID == null) throw new SQLException("Connection is not opened");
        Logger.getContextLogger(QueryContext.class).debug(
                "Prepare statement: {0} (connection #{1})",
                IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
        );
        Connection connection = getConnection(connectionID);
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
        if (connectionID == null) throw new SQLException("Connection is not opened");
        Connection connection = getConnection(connectionID);
        return connection.prepareCall(query);
    }

    private RowSet prepareSet(Integer connectionID) throws SQLException {
        Connection connection = getConnection(connectionID);
        final RowSet rowSet = new OracleJDBCRowSet(connection);
        rowSet.addRowSetListener(new RowSetAdapter() {                
            @Override
            public void cursorMoved(RowSetEvent event) {
                try {
                    if (rowSet.isAfterLast() && !rowSet.isClosed()) {
                        rowSet.close();
                    }
                } catch (SQLException ignore) {}
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
    
    private abstract static class RowSetAdapter implements RowSetListener {

        @Override
        public void rowSetChanged(RowSetEvent event) {}

        @Override
        public void rowChanged(RowSetEvent event) {}

        @Override
        public void cursorMoved(RowSetEvent event) {}
    }
    
}
