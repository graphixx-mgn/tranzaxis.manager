package codex.type;

public class TypeWrapper<T> implements AbstractType {
    
    private T value;
    
    public TypeWrapper(T value) {
        this.value = value;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(Object value) {
        this.value = (T) value;
    }
    
    @Override
    public String toString() {
        if (value == null) {
            return "";
        } else {
            return value.toString();
        }
    }

}
