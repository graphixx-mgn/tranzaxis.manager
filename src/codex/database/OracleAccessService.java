package codex.database;

import codex.log.Logger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import javax.sql.RowSet;
import javax.sql.RowSetEvent;
import javax.sql.RowSetListener;
import oracle.jdbc.rowset.OracleJDBCRowSet;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * Реализация сервиса взаимодействия с базой данных Oracle с поддержкой пула 
 * соедиений.
 */
public class OracleAccessService implements IDatabaseAccessService {
    
    private final static OracleAccessService INSTANCE = new OracleAccessService();
    private static final Boolean DEV_MODE = "1".equals(java.lang.System.getProperty("showSql"));
    
    /**
     * Возвращает экземпляр сервиса (синглтон). 
     */
    public static OracleAccessService getInstance() {
        return INSTANCE;
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
                    Logger.getLogger().debug("OAS: Registered new connection #{0}: URL={1}, User={2}", SEQ.get(), url, user);
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
            if (DEV_MODE) {
                Logger.getLogger().debug(
                        "OAS: Select query: {0} (connection #{1})", 
                        IDatabaseAccessService.prepareTraceSQL(query, params), connectionID
                );
            }
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
            throw new SQLException(getCause(e).getMessage().trim());
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
                } catch (SQLException e) {}
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
    };
    
    private abstract class RowSetAdapter implements RowSetListener {

        @Override
        public void rowSetChanged(RowSetEvent event) {}

        @Override
        public void rowChanged(RowSetEvent event) {}

        @Override
        public void cursorMoved(RowSetEvent event) {}
    }
    
}
