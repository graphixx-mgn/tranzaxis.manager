package codex.supplier;

import java.util.List;

/**
 * Базовый интерфейс поставщика данных.
 * @param <T> Тип возвращаемого поставщиком значения.
 */
public interface IDataSupplier<T> {

    Integer DEFAULT_LIMIT = 100;

    boolean ready();
    boolean available();
    List<T> get() throws NoDataAvailable;
    void    reset();


    class NoDataAvailable extends Exception {

        public NoDataAvailable() {
            super();
        }

        public NoDataAvailable(String message) {
            super(message);
        }

        public NoDataAvailable(Throwable cause) {
            super(cause);
        }

        @Override
        public String getMessage() {
            return getCause() != null ? getCause().getMessage() : super.getMessage();
        }
    }
}
