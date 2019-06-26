package codex.supplier;

import java.util.List;

/**
 * Базовый интерфейс поставщика данных.
 * @param <T> Тип возвращаемого поставщиком значения.
 */
public interface IDataSupplier<T> {

    boolean ready();
    default boolean available() {
        return false;
    }
    List<T> get() throws NoDataAvailable;


    class NoDataAvailable extends Exception {}
}
