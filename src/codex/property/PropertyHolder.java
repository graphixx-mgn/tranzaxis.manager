package codex.property;

public class PropertyHolder {
    
    private final Class   type;
    private final String  name;
    private final String  title;
    private final boolean mandatory;
    private Object        value;
    
    public PropertyHolder(Class type, String name, String title, Object value, boolean mandatory) {
        this.type      = type;
        this.name      = name;
        this.title     = title;
        this.value     = value;
        this.mandatory = mandatory;
    }
    
}
