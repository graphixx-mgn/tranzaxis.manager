package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.FilePathEditor;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Тип-обертка {@link IComplexType} для интерфейса Path.
 */
public class FilePath implements IComplexType<Path>  {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new FilePathEditor(propHolder);
    };
    
    private Path        value;
    private IMask<Path> mask;
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public FilePath(Path value) {
        this.value = value;
    }
    
    @Override
    public Path getValue() {
        return value;
    }

    @Override
    public void setValue(Path value) {
        this.value = value;
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }

    /**
     * Установить маску значения.
     */
    @Override
    public IComplexType setMask(IMask<Path> mask) {
        this.mask = mask;
        return this;
    }
    
    /**
     * Возвращает маску значения.
     */
    @Override
    public IMask<Path> getMask() {
        return mask;
    }
    
    @Override
    public String toString() {
        return IComplexType.coalesce(value, "").toString();
    }
    
    @Override
    public void valueOf(String value) {
        setValue(Paths.get(value));
    }
    
}
