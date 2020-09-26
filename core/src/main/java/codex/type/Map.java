package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.MapEditor;
import codex.mask.IMask;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Map<K, V> implements ISerializableType<java.util.Map<K, V>, IMask<java.util.Map<K, V>>> {

    private final Class<? extends ISerializableType<K, ? extends IMask<K>>> keyClass;
    private final Class<? extends ISerializableType<V, ? extends IMask<V>>> valClass;

    private final Class<?> keyParamClass;
    private final Class<?> valParamClass;

    private final IMask<K> keyMask;
    private final IMask<V> valMask;

    private final ISerializableType<K, ? extends IMask<K>> keyBuf;
    private final ISerializableType<V, ? extends IMask<V>> valBuf;

    private java.util.Map<K, V> value;
    private final V defaultValue;

    @SuppressWarnings("unchecked")
    public Map(
            ISerializableType<K, ? extends IMask<K>> defKey,
            ISerializableType<V, ? extends IMask<V>> defVal,
            java.util.Map<K, V> value) {

        defaultValue = defVal.getValue();

        keyClass = (Class<? extends ISerializableType<K, IMask<K>>>) defKey.getClass();
        keyParamClass = IParametrized.class.isAssignableFrom(keyClass) ? ((IParametrized) defKey).getValueClass() : null;
        keyMask  = defKey.getMask();

        valClass = (Class<? extends ISerializableType<V, IMask<V>>>) defVal.getClass();
        valParamClass = IParametrized.class.isAssignableFrom(valClass) ? ((IParametrized) defVal).getValueClass() : null;
        valMask  = defVal.getMask();

        keyBuf = createKey();
        valBuf = createVal();

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
        return new InternalMap(value);
    }

    @Override
    public void setValue(java.util.Map<K, V> value) {
        if (value != null) {
            this.value = new InternalMap(value);
        } else {
            this.value = new InternalMap();
        }
    }

    public java.util.Map.Entry<? extends ISerializableType<K, ? extends IMask<K>>, ? extends ISerializableType<V, ? extends IMask<V>>> newEntry() {
        return new AbstractMap.SimpleEntry<>(createKey(), createVal());
    }

    @SuppressWarnings("unchecked")
    private ISerializableType<K, ? extends IMask<K>> createKey() {
        try {
            final ISerializableType<K, ? extends IMask<K>> instance;

            if (IParametrized.class.isAssignableFrom(keyClass)) {
                Constructor<? extends ISerializableType<K, ? extends IMask<K>>> ctor = keyClass.getDeclaredConstructor(Class.class);
                ctor.setAccessible(true);
                instance = ctor.newInstance(keyParamClass);
            } else {
                Constructor<? extends ISerializableType<K, ? extends IMask<K>>> ctor = keyClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                instance = ctor.newInstance();
            }
            ((ISerializableType<K,IMask<K>>) instance).setMask(keyMask);
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ISerializableType<V, ? extends IMask<V>> createVal() {
        try {
            final ISerializableType<V, ? extends IMask<V>> instance;
            if (IParametrized.class.isAssignableFrom(valClass)) {
                Constructor<? extends ISerializableType<V, ? extends IMask<V>>> ctor = valClass.getDeclaredConstructor(Class.class);
                ctor.setAccessible(true);
                instance = ctor.newInstance(valParamClass);
            } else {
                Constructor<? extends ISerializableType<V, ? extends IMask<V>>> ctor = valClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                instance = ctor.newInstance();
            }
            instance.setValue(defaultValue);
            ((ISerializableType<V, IMask<V>>) instance).setMask(valMask);
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    @Override
    public IEditorFactory<Map<K, V>, java.util.Map<K, V>> editorFactory() {
        return MapEditor::new;
    }

    @Override
    public void valueOf(String value) {
        if (value != null && !value.isEmpty()) {
            List<String> list = ArrStr.parse(value);
            for (int keyIdx = 0; keyIdx < list.size(); keyIdx = keyIdx+2) {
                if (keyBuf != null) keyBuf.valueOf(list.get(keyIdx));
                if (valBuf != null) valBuf.valueOf(list.get(keyIdx+1));
                if (keyBuf != null) {
                    this.value.put(keyBuf.getValue(), valBuf == null ? null : valBuf.getValue());
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
                keyBuf.setValue(k);
                valBuf.setValue(v);
                list.add(keyBuf.toString());
                list.add(valBuf.toString());
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
                                keyBuf.getQualifiedValue(kvEntry.getKey()),
                                valBuf.getQualifiedValue(kvEntry.getValue()))
                        )
                        .collect(Collectors.joining(",\n"))
        );
    }


    private class InternalMap extends LinkedHashMap <K, V> {

        private InternalMap() {
            super();
        }

        private InternalMap(java.util.Map<? extends K, ? extends V> m) {
            super(m);
        }

        @Override
        public boolean equals(Object o) {
            boolean superCheck = super.equals(o);
            if (!superCheck) return false;

            java.util.Map<K, V> map = (java.util.Map<K, V>) o;
            Iterator<K> mapIterator = map.keySet().iterator();
            Iterator<K> theIterator = keySet().iterator();
            while (theIterator.hasNext()) {
                if (!theIterator.next().equals(mapIterator.next())) {
                    return false;
                }
            }
            return true;
        }
    }
}
