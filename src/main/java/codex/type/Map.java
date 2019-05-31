package codex.type;

import codex.editor.MapEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.LinkedHashMap;
//import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public class Map<K extends IComplexType, V extends IComplexType> implements ISerializableType<java.util.Map<K, V>, IMask<java.util.Map<K, V>>> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new MapEditor(propHolder);
    };
    
    java.util.Map<K, V> value;
    private final Class<K> keyClass;
    private final Class<V> valClass;
    private final Object   defKeyObject, defValObject;
    
    
    public Map(Class<K> keyClass, Class<V> valClass, java.util.Map<K, V> value) {
        this(keyClass, valClass, value, null, null);
    }
    
    public Map(Class<K> keyClass, Class<V> valClass, java.util.Map<K, V> value, Object defKeyObject, Object defValObject) {
        this.keyClass = keyClass;
        this.valClass = valClass;
        
        if (value != null) {
            this.value = new LinkedHashMap<>(value);
        } else {
            this.value = null;
        }
        if (valClass.isAssignableFrom(Enum.class) && defValObject == null) {
            throw new IllegalStateException("Value type is enum. Default value must be defined");
        }
        if (keyClass.isAssignableFrom(Enum.class) && defKeyObject == null) {
            throw new IllegalStateException("Key type is enum. Default value must be defined");
        }
        this.defKeyObject = defKeyObject;
        this.defValObject = defValObject;
    }
    
    public Class<K> getKeyClass() {
        return keyClass;
    }
    
    public Class<V> getValClass() {
        return valClass;
    }

    @Override
    public java.util.Map<K, V> getValue() {
        return value == null ? null : new LinkedHashMap<K, V>(value) {
            @Override
            public V put(K key, V value) {
                return Map.this.value.containsKey(key) ? null : Map.this.value.put(key, value);
            }
        };
    }

    @Override
    public void setValue(java.util.Map<K, V> value) {
        if (value != null && !value.isEmpty()) {
            this.value = new LinkedHashMap<>(value);
        } else {
            this.value = null;
        }
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
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return (complex.getValue() == null ? getValue() == null : complex.getValue().equals(getValue()));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.value);
        return hash;
    }

    @Override
    public void valueOf(String value) {
        final Class keyInternalType = (Class) ((ParameterizedType) keyClass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
        final Class valInternalType = (Class) ((ParameterizedType) valClass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
        
        Arrays.asList(value.replaceAll("^\\{(.*)\\}$", "$1").split(", ", -1)).stream()
                .map(pair -> pair.split("="))
                .collect(Collectors.toMap(
                        e->e[0], 
                        e->e[1]
                ))
                .forEach((keyStr, valStr) -> {
                    try {
                        K dbKey = (K) keyClass.getConstructor(new Class[] {keyInternalType}).newInstance(new Object[] {defKeyObject});
                        dbKey.valueOf(keyStr);
                        V dbVal = (V) valClass.getConstructor(new Class[] {valInternalType}).newInstance(new Object[] {defValObject});
                        dbVal.valueOf(valStr);
                        this.value.put(dbKey, dbVal);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public String getQualifiedValue(java.util.Map<K, V> val) {
        return val == null ? "<NULL>" : new StringBuilder().append("[")
            .append(val.entrySet().stream()
                .map((entry) -> {
                    return 
                            entry.getKey().getQualifiedValue(entry.getKey().getValue())
                            .concat("=")
                            .concat(entry.getValue().getQualifiedValue(entry.getValue().getValue()));
                }).collect(Collectors.joining(","))
            ).append("]").toString();
    }

}
