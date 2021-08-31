package manager.processor.database;

import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.utils.Language;
import manager.processor.Factory;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Arrays;

public class CallProcessor<C extends DatabaseContext> extends DatabaseProcessor<C> {
    private   final static IDatabaseAccessService DAS = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class);
    protected CallProcessor(C context) {
        super(context);
    }

    public static <C extends DatabaseContext> Factory<CallProcessor<C>, C> getFactory() {
        return new Factory<CallProcessor<C>, C>() {
            @Override
            protected CallProcessor<C> newInstance(C context) {
                return new CallProcessor<>(context);
            }
        };
    }

    @Override
    protected String generateQuery(String tag, Object... parameters) {
        return Language.get(getContext().getProvisionClass(), tag.concat(QUERY_TAG_SUFFIX));
    }

    @Override
    protected String executeSql(String query, Object... parameters) throws SQLException {
        try (CallableStatement stmt = DAS.prepareCallable(getContext().getDatabase().getConnectionID(true), query)) {
            registerParameters(stmt, parameters);
            stmt.execute();
        }
        return null;
    }

    protected void registerParameters(CallableStatement stmt, Object...parameters) throws SQLException {
        int stmtParamCount = stmt.getParameterMetaData().getParameterCount();
        if (stmtParamCount > parameters.length) {
            throw new Error(MessageFormat.format(
                    "Not enough parameters ({0}) to populate statement ({1})",
                    parameters.length, stmtParamCount
            ));
        }
        if (stmtParamCount < parameters.length) {
            Logger.getContextLogger(OracleAccessService.class).warn(
                    "Too many parameters ({0}) while need just {1}",
                    parameters.length, stmtParamCount
            );
        }
        Logger.getContextLogger(OracleAccessService.class).debug(
                "Map {0} command parameters to the call statement: {1}",
                stmtParamCount, Arrays.asList(parameters)
        );
        for (int paramIdx = 1; paramIdx <= stmtParamCount; paramIdx++) {
            stmt.setObject(paramIdx, parameters[paramIdx-1]);
        }
    }
}
