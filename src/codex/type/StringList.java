package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.StringListEditor;
import codex.property.PropertyHolder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        this(Arrays.asList(IComplexType.coalesce(values, new String[]{})));
    }
    
    /**
     * Конструктор типа.
     * @param value Список строк.
     */
    public StringList(List<String> value) {
        // Может быть передан immutable List (Arrays.asList(...)) 
        this.value = new ArrayList<>(IComplexType.coalesce(value, new ArrayList<String>()));
    }

    @Override
    public List<String> getValue() {
        return value;
    }

    @Override
    public void setValue(List<String> value) {
        this.value = new ArrayList<>(IComplexType.coalesce(value, new ArrayList<String>()));
    }
    
    @Override
    public boolean isEmpty() {
        return getValue().isEmpty();
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}
