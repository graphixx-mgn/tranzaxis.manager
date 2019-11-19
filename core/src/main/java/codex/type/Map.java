package codex.type;

import codex.editor.MapEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Map<K, V> implements ISerializableType<java.util.Map<K, V>, IMask<java.util.Map<K, V>>> {

    private final Class<? extends ISerializableType<K, ? extends IMask<K>>> keyClass;
    private final Class<? extends ISerializableType<V, ? extends IMask<V>>> valClass;
    private final Class<?> valParamClass;
    private final Class<?> keyParamClass;

    private final ISerializableType<K, ? extends IMask<K>> dbKey;
    private final ISerializableType<V, ? extends IMask<V>> dbVal;

    private java.util.Map<K, V> value;

    public Map(
            Class<? extends ISerializableType<K, ? extends IMask<K>>> keyClass,
            Class<? extends ISerializableType<V, ? extends IMask<V>>> valClass,
            java.util.Map<K, V> value) {

        if (IParametrized.class.isAssignableFrom(keyClass)) {
            //noinspection unchecked
            this.keyClass = ((Class<? extends ISerializableType<K, IMask<K>>>) ((ParameterizedType) keyClass.getGenericSuperclass()).getRawType());
            this.keyParamClass = ((Class<?>) ((ParameterizedType) keyClass.getGenericSuperclass()).getActualTypeArguments()[0]);
        } else {
            this.keyClass = keyClass;
            this.keyParamClass = null;
        }
        if (IParametrized.class.isAssignableFrom(valClass)) {
            //noinspection unchecked
            this.valClass = ((Class<? extends ISerializableType<V, IMask<V>>>) ((ParameterizedType) valClass.getGenericSuperclass()).getRawType());
            this.valParamClass = ((Class<?>) ((ParameterizedType) valClass.getGenericSuperclass()).getActualTypeArguments()[0]);
        } else {
            this.valClass = valClass;
            this.valParamClass = null;
        }

        dbKey = createKey();
        dbVal = createVal();
        if (dbKey == null) throw new InstantiationError("Unable to create key wrapping class instance: "+keyClass);
        if (dbVal == null) throw new InstantiationError("Unable to create value wrapping class instance: "+valClass);

        setValue(value);
    }

    public Class<? extends ISerializableType<K, ? extends IMask<K>>> getKeyClass() {
        return keyClass;
    }

    public Class<? extends ISerializableType<V, ? extends IMask<V>>> getValClass() {
        return valClass;
    }

    @Override
    public java.util.Map<K, V> getValue() {
        return new LinkedHashMap<>(value);
    }

    public java.util.Map.Entry<? extends ISerializableType<K, ? extends IMask<K>>, ? extends ISerializableType<V, ? extends IMask<V>>> getEntry() {
        return new AbstractMap.SimpleEntry<>(createKey(), createVal());
    }

    @Override
    public void setValue(java.util.Map<K, V> value) {
        if (value != null) {
            this.value = new LinkedHashMap<>(value);
        } else {
            this.value = new LinkedHashMap<>();
        }
    }

    @Override
    public IEditorFactory<Map<K, V>, java.util.Map<K, V>> editorFactory() {
        return MapEditor::new;
    }

    private ISerializableType<K, ? extends IMask<K>> createKey() {
        try {
            if (IParametrized.class.isAssignableFrom(keyClass)) {
                Constructor<? extends ISerializableType<K, ? extends IMask<K>>> ctor = keyClass.getDeclaredConstructor(Class.class);
                ctor.setAccessible(true);
                return ctor.newInstance(keyParamClass);
            } else {
                return keyClass.getConstructor().newInstance();
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ISerializableType<V, ? extends IMask<V>> createVal() {
        try {
            if (IParametrized.class.isAssignableFrom(valClass)) {
                Constructor<? extends ISerializableType<V, ? extends IMask<V>>> ctor = valClass.getDeclaredConstructor(Class.class);
                ctor.setAccessible(true);
                return ctor.newInstance(valParamClass);
            } else {
                return valClass.getConstructor().newInstance();
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void valueOf(String value) {
        if (value != null && !value.isEmpty()) {
            List<String> list = ArrStr.parse(value);
            for (int keyIdx = 0; keyIdx < list.size(); keyIdx = keyIdx+2) {
                if (dbKey != null) dbKey.valueOf(list.get(keyIdx));
                if (dbVal != null) dbVal.valueOf(list.get(keyIdx+1));
                if (dbKey != null) {
                    this.value.put(dbKey.getValue(), dbVal == null ? null : dbVal.getValue());
                }
            }
        }
    }

    @Override
    public String toString() {
        if (value == null || value.isEmpty()) {
            return "";
        } else {
            List<String> list = new LinkedList<>();
            value.forEach((k, v) -> {
                dbKey.setValue(k);
                dbVal.setValue(v);
                list.add(dbKey.toString());
                list.add(dbVal.toString());
            });
            return ArrStr.merge(list);
        }
    }

    @Override
    public String getQualifiedValue(java.util.Map<K, V> val) {
        return val == null || val.isEmpty() ? "<NULL>" : MessageFormat.format(
                "[\n{0}\n]",
                val.entrySet().stream()
                        .map(kvEntry -> MessageFormat.format(
                                "    {0}={1}",
                                dbKey.getQualifiedValue(kvEntry.getKey()),
                                dbVal.getQualifiedValue(kvEntry.getValue()))
                        )
                        .collect(Collectors.joining(",\n"))
        );
    }
}
