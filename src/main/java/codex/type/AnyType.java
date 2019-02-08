package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.AnyTypeView;
import codex.mask.IMask;

/**
 * Тип-обертка {@link IComplexType} для класса {@link Object}.
 */
public class AnyType implements IComplexType<Object, IMask<Object>> {

    private final static IEditorFactory EDITOR_FACTORY = AnyTypeView::new;

    private Object value;

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public void valueOf(String value) {}

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }

    @Override
    public String getQualifiedValue(Object val) {
        return null;
    }
}
