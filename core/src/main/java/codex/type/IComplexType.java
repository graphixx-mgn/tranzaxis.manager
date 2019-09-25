package codex.type;

import codex.editor.AbstractEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.utils.Language;
import java.awt.Color;
import java.io.Serializable;
import java.text.MessageFormat;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Интерфеqс пользовательского типа данных для хранения в свойствах {@link PropertyHolder}
 * Фактически является "оберткой" над Java классами и интерфейсами.
 * @param <T> Базовый Java класс или интерфейс на базе которого строится реализация 
 * интерфейса.
 * @param <M> Тип маски для данного типа.
 */
public interface IComplexType<T, M extends IMask<T>> extends Serializable {
    
    /**
     * Получить экземпляр базового Java класса или интерфейса 
     * @return Object Внутреннее значение, соответствующее типу параметра класса.
     */
    T getValue();
    
    /**
     * Установить внутреннее значение.
     * @param value Новое значение, соответствующее типу параметра класса.
     */
    void setValue(T value);
    
    /**
     * Проверить установлено ли значение свойства. Понятие "пустое значение" при 
     * этом определяется в каждой реализации по-своему.
     */
    default boolean isEmpty() {
        return getValue() == null;
    };
    
    /**
     * Установить маску значения.
     */
    default IComplexType<T, M> setMask(M mask) {
        return this;
    }
    
    /**
     * Возвращает маску значения.
     */
    default M getMask() {
        return null;
    }
    
    /**
     * Фабричный метод возвращает фабрику редакторов свойств данного типа 
     * (конечной реализации).
     * @return Реализация интерфейса фабрики {@link IEditorFactory}
     */

    default IEditorFactory<? extends IComplexType<T, M>, T> editorFactory() {
        return (IEditorFactory<IComplexType<T, M>, T>) propHolder -> new AbstractEditor<IComplexType<T, M>, T>(propHolder) {
            @Override
            public Box createEditor() {
                JTextField textField = new JTextField(MessageFormat.format(
                        Language.get(IComplexType.class, "error@noeditor"),
                        propHolder.getPropValue().getClass().getCanonicalName()
                ));
                textField.setBorder(new EmptyBorder(0, 3, 0, 3));
                textField.setHorizontalAlignment(JTextField.CENTER);
                textField.setEditable(false);
                textField.setForeground(Color.RED);

                Box container = new Box(BoxLayout.X_AXIS);
                container.add(textField);
                return container;
            }

            @Override
            public void setValue(T value) {}
        };
    }
    
    /**
     * Создание значения типа из строкового представления.
     */
    void valueOf(String value);
    
    /**
     * Возврашает строковое представление для использования при логировании.
     */
    String getQualifiedValue(T val);
    
    /**
     * Аналог функции COALESCE (расширенная версия NVL) в Oracle, перебирает 
     * значения аргументов, пока не встретит первый не равный NULL и возвращает его.
     * @param <T> Тип передаваемых аргументов
     * @param values Набор однотипных аргументов произвольной длины.
     * @return Первый не NULL или NULL если такого не нашлось.
     */
    static <T> T coalesce(final T... values) {
        if (values != null) {
            for (T value : values) 
                if (value != null)
                    return value;
        }
        return null;
    }
    
    /**
     * Проверка всех переданных аргументов что они не NULL.
     * @param <T> Тип передаваемых аргументов
     * @param values Набор однотипных аргументов произвольной длины.
     * @return TRUE, если все аргументы != NULL, иначе FALSE.
     */
    static <T> boolean notNull(final T... values) {
        if (values != null) {
            for (T value : values)
                if (value == null)
                    return false;
            return true;
        }
        return false;
    }
    
}
