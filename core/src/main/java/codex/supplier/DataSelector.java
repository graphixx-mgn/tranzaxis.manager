package codex.supplier;

import net.jcip.annotations.ThreadSafe;

/**
 * Абстрактный класс селектора данных из поставщика.
 * @param <V> Тип данных, возвращаемых поставщиком.
 * @param <R> Тип значения передаваемого селектором при выборе.
 */
@ThreadSafe
public abstract class DataSelector<V, R> {

    private final IDataSupplier<V> supplier;

    public DataSelector(IDataSupplier<V> supplier){
        this.supplier = supplier;
    }

    public IDataSupplier<V> getSupplier() {
        return supplier;
    }

    public abstract R select(R initialVal);

}
