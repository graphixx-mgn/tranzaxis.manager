package codex.mask;

import codex.model.Entity;
import java.util.function.Function;
import java.util.function.Predicate;

public class EntityFilter<E extends Entity> implements IRefMask<E> {

    private final Predicate<E>       entityFilter;
    private final Function<E, Match> entityMatcher;

    /**
     * Конструктор маски.
     * @param entityFilter Пользовательский фильтр допустимых значений.
     */
    public EntityFilter(Predicate<E> entityFilter) {
        this(entityFilter, null);
    }

    /**
     * Конструктор маски.
     * @param entityFilter Пользовательский фильтр допустимых значений.
     * @param entityMatcher Условие соответствия сущности. Используется для
     * подсветки цветом в выпадающем списке {@link codex.editor.EntityRefEditor}.
     */
    public EntityFilter(Predicate<E> entityFilter, Function<E, Match> entityMatcher) {
        this.entityFilter = entityFilter != null ? entityFilter : (entity) -> true;
        this.entityMatcher = entityMatcher != null ? entityMatcher : (entity) -> Match.Unknown;
    }

    /**
     * Возвращает условие проверки сущностей.
     */
    public final Function<E, Match> getEntityMatcher() {
        return entityMatcher;
    }

    @Override
    public boolean verify(E value) {
        try {
            return entityFilter.test(value);
        } catch (ClassCastException e) {
            return false;
        }
    }


    /**
     * Результат проверки сущностей на предмет соответствия условию.
     */
    public enum Match {
        /**
         * Точное совпадение.
         */
        Exact,
        /**
         * Частичное совпадение.
         */
        About,
        /**
         * Не совпадает.
         */
        None,
        /**
         * Проверка не может быть выполнена.
         */
        Unknown
    }
}
