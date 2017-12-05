package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.StringListEditor;
import codex.property.PropertyHolder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Тип-обертка {@link IComplexType} для интерфейса {@literal List<String>}.
 */
public class StringList implements IComplexType<List<String>> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new StringListEditor(propHolder);
    };
    
    private List<String> value = null;
    
    /**
     * Конструктор типа.
     * @param values Список параметров-строк (vararg) произвольной длины или массив.
     */
    public StringList(String... values) {
        this(values != null && values.length > 0 ? 
                Arrays.asList(values) :
                null
        );
    }
    
    /**
     * Конструктор типа.
     * @param value Список строк.
     */
    public StringList(List<String> value) {
        setValue(value);
    }

    @Override
    public List<String> getValue() {
        return value;
    }

    @Override
    public void setValue(List<String> value) {
        if (value != null && !value.isEmpty()) {
            // Может быть передан immutable List (Arrays.asList(...)) 
            this.value = new ArrayList(value) {
                @Override
                public String toString() {
                    return String.join(", ", this);
                }
            };
        } else {
            this.value = null;
        }
    }
    
    @Override
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
    @Override
    public String toString() {
        return value == null ? "" : value.toString();
    }
    
    @Override
    public void valueOf(String value) {
        setValue(
                new LinkedList<>(Arrays.asList(value.split(", ", -1)))
                        .stream()
                        .filter((string) -> {
                            return !string.isEmpty();
                        })
                        .collect(Collectors.toList())
        );
    }
    
}
