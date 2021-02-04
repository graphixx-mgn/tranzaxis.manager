package codex.supplier;

import java.util.*;
import java.util.function.Supplier;

/**
 * Базовый интерфейс поставщика данных.
 * @param <T> Тип возвращаемого поставщиком значения.
 */
public interface IDataSupplier<T> {

    Integer DEFAULT_LIMIT = 100;

    boolean ready();
    boolean available(ReadDirection direction);
    List<T> getNext() throws LoadDataException;
    List<T> getPrev() throws LoadDataException;
    void    reset();

    public enum ReadDirection {
        Forward, Backward
    }

    class LoadDataException extends Exception {

        public LoadDataException() {
            super();
        }

        public LoadDataException(String message) {
            super(message);
        }

        public LoadDataException(Throwable cause) {
            super(cause);
        }

        @Override
        public String getMessage() {
            return getCause() != null ? getCause().getMessage() : super.getMessage();
        }
    }

    final class MapSupplier implements IDataSupplier<Map<String, String>> {

        public static MapSupplier build(String[] columns, Supplier<List<Vector<String>>> dataSupplier) {
            return new MapSupplier(columns, dataSupplier);
        }

        private final String[] columns;
        private final Supplier<List<Vector<String>>> supplier;
        private List<Map<String, String>> data = new LinkedList<>();

        private MapSupplier(String[] columns, Supplier<List<Vector<String>>> dataSupplier) {
            this.columns  = columns;
            this.supplier = dataSupplier;
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public boolean available(ReadDirection direction) {
            return false;
        }

        @Override
        public List<Map<String, String>> getNext() throws LoadDataException {
            if (data.isEmpty()) {
                for (Vector dataVector : supplier.get()) {
                    HashMap<String, String> map = new LinkedHashMap<>();
                    for (int idx = 0; idx < dataVector.size(); idx++) {
                        map.put(columns[idx], (String) dataVector.get(idx));
                    }
                    this.data.add(map);
                }
            }
            return data;
        }

        @Override
        public List<Map<String, String>> getPrev() throws LoadDataException {
            return data;
        }

        @Override
        public void reset() {}
    }
}
