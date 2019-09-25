package codex.type;

import codex.editor.FilePathEditor;
import codex.editor.IEditorFactory;
import codex.mask.IPathMask;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

/**
 * Тип-обертка {@link IComplexType} для интерфейса Path.
 */
public class FilePath implements ISerializableType<Path, IPathMask> {
    
    private final static IEditorFactory<FilePath, Path> EDITOR_FACTORY = FilePathEditor::new;
    
    private Path      value;
    private IPathMask mask;
    
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
    public IEditorFactory<? extends IComplexType<Path, IPathMask>, Path> editorFactory() {
        return EDITOR_FACTORY;
    }

    /**
     * Установить маску значения.
     */
    @Override
    public FilePath setMask(IPathMask mask) {
        this.mask = mask;
        return this;
    }
    
    /**
     * Возвращает маску значения.
     */
    @Override
    public IPathMask getMask() {
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
    
    @Override
    public String getQualifiedValue(Path val) {
        return val == null ? "<NULL>" : MessageFormat.format("file://{0}", val);
    }
    
}
