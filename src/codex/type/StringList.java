package codex.type;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Implements new property type: string value from list of possible values.
 * @see AbstractType
 * @author Gredyaev Ivan
 */
public class StringList implements AbstractType {
    
    private final LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
    
    /**
     * Creates new instance of StringList
     * @param range List of possible values
     * @param value 
     */
    public StringList(List<String> range, String value) {
        for (String rangeItem : range) {
            if (rangeItem == null) {
                throw new IllegalStateException(
                        "Range must not contain NULL value"
                );
            }
            if (values.containsKey(rangeItem)) {
                throw new IllegalStateException(
                        MessageFormat.format("Range already has item ''{0}''", rangeItem)
                );
            }
            this.values.put(rangeItem, rangeItem.equals(value));
        }
    }

    @Override
    public String getValue() {
        for (String key : values.keySet()) {
            if (values.get(key)) {
                return key;
            }
        }
        return null;
    }

    @Override
    public void setValue(Object value) {
        values.replaceAll((k, v) -> Boolean.FALSE);
        if (value != null) {
            if (values.containsKey(value.toString())) {
                values.replace(value.toString(), Boolean.TRUE);
            } else {
                throw new IllegalStateException(
                        MessageFormat.format("Value ''{0}'' is out of range", value)
                );
            }
        }
    }

    @Override
    public String toString() {
        String value = getValue();
        if (value == null) {
            return "";
        } else {
            return value;
        }
    }
}
