package codex.editor;

import codex.property.PropertyHolder;
import codex.type.Int;

/**
 * Редактор свойств типа {@link Int}, представляет собой поле ввода.
 */
public class IntEditor extends StrEditor {
    
    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public IntEditor(PropertyHolder propHolder) {
        super(
                propHolder,
                (text) -> {
                    try {
                        return text.isEmpty() || Integer.valueOf(text) <= Integer.MAX_VALUE;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                Integer::valueOf
        );
    }

}
