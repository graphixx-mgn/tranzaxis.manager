package codex.property;

import codex.model.AbstractModel;
import codex.utils.Language;
import codex.type.IComplexType;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

/** 
 * Класс реализует модель свойства сущности {@link AbstractModel}.
 * Хранит объект, реализующий интерфейс {@link IComplexType}.
 */
public class PropertyHolder<T extends IComplexType<V>, V> {
    
    private final String      name;
    private final String      title;
    private final String      desc;
    private final boolean     require;
    private IComplexType<V>   value;
    private PropertyHolder<T, V> inherit;
    private final List<PropertyChangeListener> listeners = new LinkedList<>();
    
    /**
     * Конструктор свойства. Наименование и описание достаются их ресурса локализаии
     * вызывающего класса.
     * @param name Идентификатор свойства, являющийся уникальным ключем в списке 
     * свойств сущности {@link AbstractModel}.
     * @param value Экземпляр {@link IComplexType}. NULL-значение не допустимо.
     * @param require Свойство обязательно должно иметь значение.
     */
    public PropertyHolder(String name, IComplexType<V> value, boolean require) {
        this(new Exception().getStackTrace()[1].getClassName().replaceAll(".*\\.(\\w+)", "$1"),name, value, require);
    }
    
    private PropertyHolder(String caller, String name, IComplexType<V> value, boolean require) {
        this(name, Language.get(caller, name+".title"), Language.get(caller, name+".desc"), value, require);
    }
    
    /**
     * Конструктор свойства.
     * @param name Идентификатор свойства, являющийся уникальным ключем в списке 
     * @param title Наименование свойства.
     * @param desc Детальное описание свойства, отображается при наведении мыши на
     * редактор свойства.
     * свойств сущности {@link AbstractModel}.
     * @param value Экземпляр {@link IComplexType}. NULL-значение не допустимо.
     * @param require Свойство обязательно должно иметь значение.
     */
    public PropertyHolder(String name, String title, String desc, IComplexType<V> value, boolean require) {
        if (value == null) {
            throw new IllegalStateException("Invalid value: NULL value is not supported");
        }
        this.name    = name;
        this.title   = title;
        this.desc    = desc;
        this.require = require;
        this.value   = value;
    }
    
    /**
     * Получить идентификатор свойства.
     */
    public final String getName() {
        return name;
    }
    
    /**
     * Получить наименование свойства.
     */
    public final String getTitle() { 
        return title; 
    }
    
    /**
     * Получить описание свойства.
     */
    public final String getDescriprion() { 
        return desc; 
    }
    
    /**
     * Получить экземпляр хранимого объекта {@link IComplexType}
     */
    public final IComplexType<? extends V> getPropValue() {
        return inherit == null ? value : inherit.getPropValue();
    }
    
    /**
     * Установка нового значения свойства. При этом допустимо передать как новый 
     * экземпляр {@link IComplexType} так и его внутреннее значения.
     */
    public final void setValue(V value) {
        V prevValue = getPropValue().getValue();
        if (value == null) {
            this.value.setValue(value);
        } else {
            if (IComplexType.class.isAssignableFrom(value.getClass())) {
                if (value.getClass().equals(this.value.getClass())) {
                    this.value = (IComplexType<V>) value;
                } else {
                    throw new IllegalStateException(
                            MessageFormat.format(
                                    "Invalid value: given ''{0}'' while expecting ''{1}''", 
                                    value.getClass().getCanonicalName(),
                                    this.value.getClass().getCanonicalName()
                            )
                    );
                }
            } else if (Enum.class.isAssignableFrom(value.getClass())) {
                if (!this.value.getValue().getClass().equals(value.getClass())) {
                    throw new IllegalStateException(
                            MessageFormat.format(
                                    "Invalid value: given ''{0}'' while expecting ''{1}''", 
                                    value.getClass().getCanonicalName(),
                                    this.value.getValue().getClass().getCanonicalName()
                            )
                    );
                }
                this.value.setValue(value);
            } else {
                this.value.setValue(value);
            }
        }
        fireChangeEvent(prevValue, getPropValue().getValue());
    }

    /**
     * Установить свойство для наследования значения.
     */
    public void setInherited(PropertyHolder propHolder) {
        V prevValue = getPropValue().getValue();
        inherit = propHolder;
        fireChangeEvent(prevValue, getPropValue().getValue());
    }
    
    /**
     * Возвращает флаг унаследованного значения свойства.
     */
    public boolean isInherited() {
        return inherit != null;
    }
    
    /**
     * Возвращает флаг корректности значения свойства.
     */
    public boolean isValid() {
        return !(isRequired() && isEmpty());
    }
    
    /**
     * Возвращает флаг обязательности значения свойства.
     */
    public final boolean isRequired() {
        return require;
    }
    
    /**
     * Возвращает флаг пустого значения свойства.
     */
    public final boolean isEmpty() {
        return getPropValue().isEmpty();
    }
    
    /**
     * Добавление слушателя события изменения значения свойства.
     */
    public final void addChangeListener(PropertyChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Оповещение слушателей об изменении значения свойства.
     * @param prevValue Предыдущее значение свойства.
     * @param nextValue Новое значение.
     */
    private void fireChangeEvent(Object prevValue, Object nextValue) {
        listeners.forEach((listener) -> {
            listener.propertyChange(name, prevValue, nextValue);
        });
    }
    
    /**
     * Возвращает строковое представление свойства.
     */
    @Override
    public final String toString() {
        if (getPropValue().getValue() == null) {
            return "";
        } else {
            return getPropValue().getValue().toString();
        }
    }
    
}
