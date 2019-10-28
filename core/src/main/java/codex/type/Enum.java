package codex.type;

import codex.editor.EnumEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Objects;

public class Enum<T extends java.lang.Enum> implements ISerializableType<T, IMask<T>>, IParametrized {

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Undefined {}
    public static boolean isUndefined(java.lang.Enum value) {
        try {
            return value.getClass().getDeclaredField(value.name()).getAnnotation(Undefined.class) != null;
        } catch (NoSuchFieldException e) {
            //
        }
        return false;
    }

    private T value;
    private IMask<T> mask;

    protected Enum(Class<T> enumClass) {
        this(Arrays.stream(enumClass.getEnumConstants()).filter(val -> val.ordinal()==0).findFirst().get());
    }

    public Enum(T value) {
        this(value, true);
    }

    public Enum(T value, boolean allowUndefined) {
        setValue(value);

        if (!allowUndefined) {
            for (java.lang.Enum e : value.getClass().getEnumConstants()) {
                if (isUndefined(e)) {
                    setMask(new CheckUndefined());
                    break;
                }
            }
        }
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(T value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return isUndefined(value);
    }

    @Override
    public IEditorFactory<? extends IComplexType<T, IMask<T>>, T> editorFactory() {
        return (IEditorFactory<Enum<T>, T>) EnumEditor::new;
    }

    /**
     * Установить маску значения.
     */
    @Override
    public Enum<T> setMask(IMask<T> mask) {
        this.mask = mask;
        return this;
    }

    /**
     * Возвращает маску значения.
     */
    @Override
    public IMask<T> getMask() {
        return mask;
    }

    @Override
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return complex.getValue().equals(getValue());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.value);
        return hash;
    }

    @Override
    public String toString() {
        return value.name();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void valueOf(String value) {
        setValue((T) java.lang.Enum.valueOf(this.value.getClass().asSubclass(java.lang.Enum.class), value));
    }

    @Override
    public String getQualifiedValue(java.lang.Enum val) {
        return val.name();
    }

    private class CheckUndefined implements IMask<T> {

        @Override
        public boolean verify(T value) {
            return !isUndefined(value);
        }
    }
}
