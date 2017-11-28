package codex.supplier;

import java.util.concurrent.Callable;

/**
 * Базовый интерфейс поставщика данных.
 * @param <T> Тип возвращаемого поставщиком значения.
 */
public interface IDataSupplier<T> extends Callable<T> {
    
    
}
