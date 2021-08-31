package manager.processor.database;

import codex.database.IDatabaseAccessService;
import codex.service.ServiceRegistry;
import manager.processor.Factory;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SqlProcessor<C extends DatabaseContext> extends DatabaseProcessor<C> {
    private   final static IDatabaseAccessService DAS = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class);
    protected SqlProcessor(C context) {
        super(context);
    }

    public static <C extends DatabaseContext> Factory<SqlProcessor<C>, C> getFactory() {
        return new Factory<SqlProcessor<C>, C>() {
            @Override
            protected SqlProcessor<C> newInstance(C context) {
                return new SqlProcessor<>(context);
            }
        };
    }

    @Override
    protected String executeSql(String query, Object...parameters) throws SQLException {
        try (
            PreparedStatement stmt = DAS.prepareStatement(getContext().getDatabase().getConnectionID(true), query);
        ) {
            stmt.executeUpdate();
            stmt.getConnection().close();
        }
        return null;
    }
}
