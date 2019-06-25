package codex.mask;

import codex.model.Entity;
import java.util.function.Function;

public interface IRefMask<E extends Entity> extends IMask<E> {

    /**
     * Возвращает условие проверки сущностей.
     */
    default Function<E, EntityFilter.Match> getEntityMatcher() {
        return e -> EntityFilter.Match.None;
    }

}
