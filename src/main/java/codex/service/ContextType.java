package codex.service;

import codex.mask.IMask;
import codex.type.IComplexType;
import codex.type.ISerializableType;
import java.text.MessageFormat;
import java.util.Objects;

public class ContextType implements ISerializableType<ContextPresentation, IMask<ContextPresentation>> {

    private ContextPresentation value;

    public ContextType() {
        setValue(null);
    }

    @Override
    public ContextPresentation getValue() {
        return value;
    }

    @Override
    public void setValue(ContextPresentation value) {
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
        return IComplexType.coalesce(getValue().getContextClass().getCanonicalName(), "");
    }

    @Override
    public void valueOf(String value) {
        try {
            setValue(new ContextPresentation(Class.forName(value).asSubclass(IContext.class)));
        } catch (ClassNotFoundException e) {
            setValue(null);
        }
    }

    @Override
    public String getQualifiedValue(ContextPresentation val) {
        return val == null ? "<NULL>" : MessageFormat.format("({0})", val);
    }
}
