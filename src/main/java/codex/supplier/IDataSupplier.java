package codex.supplier;

import java.util.List;

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
}
