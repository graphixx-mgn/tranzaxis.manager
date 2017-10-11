package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.FilePathEditor;
import codex.property.PropertyHolder;

/**
 * Тип-обертка {@link IComplexType} для интерфейса Path.
 * @param <Integer> Базовый Java класс: Path.
 */
public class FilePath<Path> extends Str<Path>  {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new FilePathEditor(propHolder);
    };
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public FilePath(Path value) {
        super(value);
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
}
