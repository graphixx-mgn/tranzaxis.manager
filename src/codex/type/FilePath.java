package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.FilePathEditor;
import codex.property.PropertyHolder;
import java.nio.file.Path;

/**
 * Тип-обертка {@link IComplexType} для интерфейса Path.
 */
public class FilePath implements IComplexType<Path>  {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new FilePathEditor(propHolder);
    };
    
    private Path value;
    
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
    
}
