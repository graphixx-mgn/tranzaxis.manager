package codex.context;

import codex.mask.IMask;
import codex.type.IComplexType;
import codex.type.ISerializableType;
import java.text.MessageFormat;
import java.util.Objects;

public class ContextType implements ISerializableType<ContextView, IMask<ContextView>> {

    private ContextView value;

    public ContextType() {
        setValue(null);
    }

    @Override
    public ContextView getValue() {
        return value;
    }

    @Override
    public void setValue(ContextView value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return (complex.getValue() == null ? getValue() == null : complex.getValue().equals(getValue()));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Objects.hashCode(this.value);
        return hash;
    }

    @Override
    public String toString() {
        return IComplexType.coalesce(getValue().getContextClass().getTypeName(), "");
    }

    @Override
    public void valueOf(String value) {
        try {
            setValue(new ContextView(Class.forName(value).asSubclass(IContext.class)));
        } catch (ClassNotFoundException e) {
            setValue(null);
        }
    }

    @Override
    public String getQualifiedValue(ContextView val) {
        return val == null ? "<NULL>" : MessageFormat.format("({0})", val);
    }
}
