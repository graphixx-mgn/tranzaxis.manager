package codex.type;

import codex.mask.IMask;

public interface ISerializableType<T, M extends IMask<T>> extends IComplexType<T, M> {

    @Override
    default ISerializableType<T, M> setMask(M mask) {
        return this;
    }

}
