package codex.type;

import codex.editor.ArrStrEditor;
import codex.editor.IEditorFactory;
import codex.mask.IArrMask;
import codex.mask.StrSetMask;
import codex.property.PropertyHolder;
import java.text.MessageFormat;
import java.util.*;

/**
 * Тип-обертка {@link IComplexType} для интерфейса {@literal List<String>}.
 */
public class ArrStr implements IComplexType<List<String>, IArrMask> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new ArrStrEditor(propHolder);
    };
    
    private List<String> value;
    private IArrMask mask;
    
    /**
     * Конструктор типа.
     * @param values Список параметров-строк (vararg) произвольной длины или массив.
     */
    public ArrStr(String... values) {
        this(values != null && values.length > 0 ? 
                Arrays.asList(values) :
                null
        );
    }
    
    /**
     * Конструктор типа.
     * @param value Список строк.
     */
    public ArrStr(List<String> value) {
        if (value != null && !value.isEmpty()) {
            // Может быть передан immutable List (Arrays.asList(...)) 
            this.value = new FormattedList(value);
        } else {
            this.value = new FormattedList(new LinkedList<>());
        }
    }

    @Override
    public List<String> getValue() {
        return value == null ? null : new FormattedList(value);
    }

    @Override
    public void setValue(List<String> value) {
        if (value != null && !value.isEmpty()) {
            this.value = new FormattedList(value);
        } else {
            this.value = new FormattedList(new LinkedList<>());
        }
    }
    
    @Override
    public boolean isEmpty() {
        return 
            value == null || 
            value.isEmpty() || (
                (mask instanceof StrSetMask) && 
                IComplexType.coalesce(value.get(0), "").isEmpty()
            );
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
    @Override
    public IComplexType setMask(IArrMask mask) {
        this.mask = mask;
        return this;
    }
    
    @Override
    public IArrMask getMask() {
        return mask;
    }
    
    @Override
    public String toString() {
        return merge(value);
    }
    
    @Override
    public void valueOf(String value) {
        setValue(parse(value));
    }
    
    @Override
    public String getQualifiedValue(List<String> val) {
        return val == null ? "<NULL>" :  MessageFormat.format("({0})'", val);
    }
    
    /**
     * Преобразование списка строк в строковое представление для записи в БД.
     * @param values Список строк.
     */
    public static String merge(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(values.size());
            values.forEach((value) -> {
                String valStr = IComplexType.coalesce(value, "");
                builder.append("[").append(valStr.length()).append("]").append(valStr);
            });
            return builder.toString();
        }
    }
    
    /**
     * Преобразование строкового представления массива в объект.
     * @param asStr Исходная строка.
     */
    public static List<String> parse(String asStr) throws IllegalStateException {
        List<String> values = new LinkedList();
        int size;
        if (asStr.length() > 0 && !asStr.isEmpty()) {
            int pos1 = asStr.indexOf('[');
            if (pos1 < 0) {
                pos1 = asStr.length();
            }
            try {
                size = Integer.parseInt(asStr.substring(0, pos1));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Wrong format of array string presentation. Wrong array size format.", e);
            }
            int     pos2 = asStr.indexOf(']', pos1);
            String  lenStr;
            int     len;
            boolean isNull;
            String  itemAsStr;
            while (pos2 != -1) {
                if (pos2 - pos1 < 1) {
                    throw new IllegalStateException("Wrong format of array string presentation");
                }
                try {
                    lenStr = asStr.substring(pos1 + 1, pos2);
                    isNull = (lenStr.length() == 0);
                    if (!isNull) {
                        len = Integer.parseInt(lenStr);
                    } else {
                        len = 0;
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Wrong format of array string presentation. Can't parse item length");
                }
                if (!isNull) {
                    if (len == 0) {
                        itemAsStr = "";
                    } else {
                        if (pos2 + len >= asStr.length()) {
                            throw new IllegalStateException("Wrong format of array string presentation. Wrong item length.", null);
                        }
                        itemAsStr = asStr.substring(pos2 + 1, pos2 + 1 + len);
                    }
                    values.add(itemAsStr);
                } else {
                    values.add("");
                }
                pos1 = pos2 + len + 1;
                if (pos1 >= asStr.length()) {
                    break;
                }
                pos2 = asStr.indexOf(']', pos1);
            }
            if (values.size() != size) {
                throw new IllegalStateException("Wrong format of array string presentation. Wrong array item count.", null);
            }
        } else {
            values.clear();
        }
        return values;
    }
    
    private class FormattedList extends LinkedList<String> {

        FormattedList() {
            super();
        }

        FormattedList(Collection<? extends String> c) {
            super(c);
        }
        
        @Override
        public String toString() {
            if (!isEmpty() && mask != null && mask.getFormat() != null) {
                return MessageFormat.format(
                    mask.getFormat(), 
                    toArray()
                ).replaceAll("\\{\\d+\\}", "");
            } else {
                return String.join(", ", this);
            }
        }
    }
    
}
