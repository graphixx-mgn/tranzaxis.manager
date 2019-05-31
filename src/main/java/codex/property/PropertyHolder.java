package codex.property;

import codex.editor.IEditor;
import codex.mask.IMask;
import codex.model.AbstractModel;
import codex.model.EntityModel;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

/** 
 * Класс реализует модель свойства сущности {@link AbstractModel}.
 * Хранит объект, реализующий интерфейс {@link IComplexType}.
 */
public class PropertyHolder<T extends IComplexType<V, ? extends IMask<V>>, V> {
    
    private final String  name;
    private final String  title;
    private final String  desc;
    private final String  placeholder;
    private       boolean require;

    private T value;
    private PropertyHolder<T, V> inherit;
    private final List<IPropertyChangeListener> changeListeners = new LinkedList<>();
    private final List<IPropertyStateListener>  stateListeners  = new LinkedList<>();
    
    /**
     * Конструктор свойства. Наименование и описание достаются их ресурса локализаии
     * вызывающего класса.
     * @param name Идентификатор свойства, являющийся уникальным ключем в списке 
     * свойств сущности {@link AbstractModel}.
     * @param value Экземпляр {@link IComplexType}. NULL-значение не допустимо.
     * @param require Свойство обязательно должно иметь значение.
     */
    public PropertyHolder(String name, T value, boolean require) {
        this(
                name, 
                EntityModel.SYSPROPS.contains(name) ? Language.get(EntityModel.class, name+".title") : Language.lookup(name+".title"), 
                EntityModel.SYSPROPS.contains(name) ? Language.get(EntityModel.class, name+".desc")  : Language.lookup(name+".desc"), 
                value, 
                require
        );
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
    public PropertyHolder(String name, String title, String desc, T value, boolean require) {
        if (value == null) {
            throw new IllegalStateException("Invalid value: NULL value is not supported");
        }
        this.name    = name;
        this.title   = title;
        this.desc    = desc;
        this.require = require;
        this.value   = value;
        
        String propPlaceHolder = Language.lookup(name+".placeholder");
        String typePlaceHolder = Language.lookup("placeholder");
        
        this.placeholder = 
                Language.NOT_FOUND.equals(propPlaceHolder) ? (
                    Language.NOT_FOUND.equals(typePlaceHolder) ? IEditor.NOT_DEFINED : typePlaceHolder 
                ): propPlaceHolder;
    }
    
    /**
     * Возвращает тип (класс значения) свойства.
     */
    public final Class<T> getType() {
        return (Class<T>) value.getClass();
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
     * Получить строку-заменитель значения (когда значение свойства не задано).
     * Свойство автоматически ищет заменитель в локализующих ресурсах класса сущности,
     * которая владеет свойством по имени "{name}.placeholder", если не удалось
     * найти - ищет в ресурсах класса типа данного свойства по имени "placeholder".
     */
    public final String getPlaceholder() { 
        return placeholder;
    }
    
    /**
     * Получить экземпляр хранимого объекта {@link IComplexType}
     */
    public T getPropValue() {
        return inherit == null ? value : inherit.getPropValue();
    }
    
    /**
     * Получить экземпляр хранимого объекта без учета наследования{@link IComplexType}
     */
    public T getOwnPropValue() {
        return value;
    }
    
    /**
     * Установка нового значения свойства. При этом допустимо передать как новый 
     * экземпляр {@link IComplexType} так и его внутреннее значения.
     */
    public final void setValue(V value) {
        V prevValue = getPropValue().getValue();
        if (value == null) {
            this.value.setValue(null);
        } else {
            if (IComplexType.class.isAssignableFrom(value.getClass())) {
                if (getType().isAssignableFrom(value.getClass())) {
                    this.value = (T) value;
                    fireChangeEvent(prevValue, getPropValue().getValue());
                    return;
                } else {
                    throw new IllegalStateException(
                            MessageFormat.format(
                                    "Invalid value: given ''{0}'' while expecting ''{1}''", 
                                    value.getClass().getCanonicalName(),
                                    this.value.getClass().getCanonicalName()
                            )
                    );
                }
            } else if (getType().equals(Enum.class)/*Enum.class.isAssignableFrom(value.getClass())*/) {
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
        if (
                (prevValue == null && getOwnPropValue().getValue() != null) || 
                (prevValue != null && getOwnPropValue().getValue() == null) ||
                (prevValue != null && getOwnPropValue().getValue() != null && !prevValue.equals(getOwnPropValue().getValue()))
        ) {
            fireChangeEvent(prevValue, getOwnPropValue().getValue());
        }
    }

    /**
     * Установить свойство для наследования значения.
     */
    public void setInherited(PropertyHolder propHolder) {
        inherit = propHolder;
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
        if (value instanceof EntityRef && ((EntityRef) value).getValue() != null) {
            return ((EntityRef) value).getValue().model.isValid();
        } else {
            return !(isRequired() && isEmpty());
        }
    }
    
    /**
     * Возвращает флаг обязательности значения свойства.
     */
    public final boolean isRequired() {
        return require;
    }
    
    public void setRequired(boolean require) {
        this.require = require;
        fireStatusChangeEvent();
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
    public final void addChangeListener(IPropertyChangeListener listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Удаление слушателя события изменения значения свойства.
     */
    public final void removeChangeListener(IPropertyChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    /**
     * Оповещение слушателей об изменении значения свойства.
     * @param prevValue Предыдущее значение свойства.
     * @param nextValue Новое значение.
     */
    private void fireChangeEvent(Object prevValue, Object nextValue) {
        new LinkedList<>(changeListeners).forEach((listener) -> {
            listener.propertyChange(name, prevValue, nextValue);
        });
    }
    
    /**
     * Добавление слушателя события изменения состояния свойства.
     */
    public final void addStateListener(IPropertyStateListener listener) {
        stateListeners.add(listener);
    }
    
    /**
     * Оповещение слушателей об изменении состояния свойства.
     */
    private void fireStatusChangeEvent() {
        new LinkedList<>(stateListeners).forEach((listener) -> listener.propertyStatusChange(name));
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
