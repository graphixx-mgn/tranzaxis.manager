package codex.mask;

import codex.type.IComplexType;


/**
 * Базовый интерфейс масок значений свойств.
 * @param <T> Класс-обертка {@link IComplexType}
 */
public interface IMask<T> {
    
    /**
     * Проверяет корректность переданного значения.
     */
    boolean verify(T value);
    
}
