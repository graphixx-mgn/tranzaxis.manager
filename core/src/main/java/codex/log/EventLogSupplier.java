package codex.log;

import codex.supplier.IDataSupplier;
import codex.utils.Language;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;

class EventLogSupplier implements IDataSupplier<Map<String, String>>, Closeable {

    private final static String PAGINATION = Language.get(EventLogSupplier.class, "pagination", Language.DEF_LOCALE);

    private final Connection connection;
    private String  query;
    private Long    offset = 0L;

    EventLogSupplier(Connection connection) {
        this.connection = connection;
    }

    void setQuery(String query) {
        reset();
        this.query = query;
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public boolean available(ReadDirection direction) {
        return true;
    }

    @Override
    public List<Map<String, String>> getNext() throws LoadDataException {
        List<Map<String, String>> result = new LinkedList<>();
        try (ResultSet resultSet = connection.prepareStatement(prepareQuery(query)).executeQuery()) {
            ResultSetMetaData meta = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++) {
                    row.put(meta.getColumnName(colIdx), resultSet.getString(colIdx));
                }
                result.add(row);
            }
            if (result.size() < IDataSupplier.DEFAULT_LIMIT) {
                offset = offset+result.size();
            } else {
                offset = offset+IDataSupplier.DEFAULT_LIMIT;
            }
            return result;
        } catch (SQLException e) {
            throw new LoadDataException(e);
        }
    }

    @Override
    public List<Map<String, String>> getPrev() throws LoadDataException {
        return Collections.emptyList();
    }

    @Override
    public void reset() {
        offset = 0L;
    }

    protected String prepareQuery(String query) {
        return MessageFormat.format(PAGINATION, query, IDataSupplier.DEFAULT_LIMIT, String.valueOf(offset));
    }

    @Override
    public void close() throws IOException {
        try {
            this.connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
