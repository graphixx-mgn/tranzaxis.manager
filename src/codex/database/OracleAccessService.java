package codex.database;

import codex.property.PropertyHolder;
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
    
    private final static AtomicInteger       SEQ = new AtomicInteger(0);
    private final static OracleAccessService INSTANCE = new OracleAccessService();
    
    public static OracleAccessService getInstance() {
        return INSTANCE;
    } 
    
    private OracleAccessService() {}
    
    private final Map<String, DataSource> urlToPool = new HashMap<>();
    private final Map<Integer, String>    idToUrl   = new HashMap<>();
    
    @Override
    public Integer registerConnection(String url, String user, String password) throws SQLException {
        String PID = url+"~"+user;
        if (!urlToPool.containsKey(PID)) {
            try {
                PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
                pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");

                pds.setURL(url);
                pds.setUser(user);
                pds.setPassword(password);

                pds.setInitialPoolSize(1);
                pds.setMinPoolSize(1);
                pds.setMaxPoolSize(5);
                
                urlToPool.put(PID, pds);
                idToUrl.put(SEQ.incrementAndGet(), PID);
                return SEQ.get();
            } catch (SQLException e) {
                throw new SQLException(getCause(e).getMessage().trim());
            }
        } else {
            return idToUrl.entrySet().stream().filter((entry) -> {
                return entry.getValue().equals(PID);
            }).findFirst().get().getKey();
        }
    }
    
    @Override
    public ResultSet select(Integer connectionID, String query, PropertyHolder... params) throws SQLException {
        try {
            final RowSet rowSet = prepareSet(connectionID);
            rowSet.setCommand(query);
            if (params != null) {
                int paramIdx = 0;
                for (PropertyHolder param : params) {
                    paramIdx++;
                    rowSet.setObject(paramIdx, param.getPropValue().getValue());
                }
            }
            rowSet.execute();
            return rowSet;
        } catch (SQLException e) {
            throw new SQLException(getCause(e).getMessage().trim());
        }
    }
    
    private RowSet prepareSet(Integer connectionID) throws SQLException {
        Connection connection = urlToPool.get(idToUrl.get(connectionID)).getConnection();
        final RowSet rowSet = new OracleJDBCRowSet(connection);
        rowSet.addRowSetListener(new RowSetAdapter() {                
            @Override
            public void cursorMoved(RowSetEvent event) {
                try {
                    if (rowSet.isLast()) {
                        connection.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
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
