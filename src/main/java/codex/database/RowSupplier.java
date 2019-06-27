package codex.database;

import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.utils.Language;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Supplier;

public class RowSupplier implements IDataSupplier<Map<String, String>> {

    private final static IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
    private final static String       PAGINATION = Language.get(RowSupplier.class, "pagination", Locale.US);
    private final static List<String> SYSTEM_COLUMNS = Arrays.asList("ROWINDEX");

    private final Supplier<Integer> connection;
    private final String query;
    private final Supplier<Object[]> parameters;

    private Long offset = 0L;
    private Boolean finished = false;

    public RowSupplier(Supplier<Integer> connectionID, String query, Object... parameters) {
        this(connectionID, query, () -> parameters);
    }

    public RowSupplier(Supplier<Integer> connection, String query, Supplier<Object[]> parameters) {
        this.connection = connection;
        this.query      = query;
        this.parameters = parameters;
    }

    @Override
    public boolean ready() {
        return connection.get() != null;
    }

    @Override
    public List<Map<String, String>> get() throws NoDataAvailable {
        List<Map<String, String>> result = new LinkedList<>();
        try (ResultSet resultSet = DAS.select(connection.get(), prepareQuery(query), parameters.get())) {
            ResultSetMetaData meta = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++) {
                    if (!SYSTEM_COLUMNS.contains(meta.getColumnName(colIdx))) {
                        row.put(meta.getColumnName(colIdx), resultSet.getString(colIdx));
                    }
                }
                result.add(row);
            }
            if (result.size() < IDataSupplier.DEFAULT_LIMIT) {
                finished = true;
            } else {
                offset = offset+IDataSupplier.DEFAULT_LIMIT;
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new NoDataAvailable();
        }
    }

    @Override
    public boolean available() {
        return !finished;
    }

    private String prepareQuery(String query) {
        return MessageFormat.format(PAGINATION, query, String.valueOf(offset), String.valueOf(offset+IDataSupplier.DEFAULT_LIMIT));
    }
}
