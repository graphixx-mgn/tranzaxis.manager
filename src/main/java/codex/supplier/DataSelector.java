package codex.supplier;

/**
 * Абстрактный класс селектора данных из поставщика.
 * @param <V> Тип данных, возвращаемых поставщиком.
 * @param <R> Тип значения передаваемого селектором при выборе.
 */
public abstract class DataSelector<V, R> {

    private final IDataSupplier<V> supplier;

    public DataSelector(IDataSupplier<V> supplier){
        this.supplier = supplier;
    }

    public IDataSupplier<V> getSupplier() {
        return supplier;
    }

    public abstract R select();

}
