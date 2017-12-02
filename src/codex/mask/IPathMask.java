package codex.mask;

import codex.editor.FilePathEditor;
import codex.type.FilePath;
import java.nio.file.Path;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Маска значения свойства {@link FilePath}. Определяет, что можно будет выбрать
 * в редакторе {@link FilePathEditor}: папки или файлы, а также отфильтровать файлы
 * по расширению.
 */
public interface IPathMask extends IMask<Path> {
    
    /**
     * Выбор типа объектов файловой системы:
     * 0 - только файлы.
     * 1 - только папки.
     * 2 - файлы и папки.
     */
    int getSelectionMode();
    /**
     * Возвращает фильтр файлов по расширению.
     */
    FileNameExtensionFilter getFilter();

    @Override
    default public boolean verify(Path value) {
        return true;
    };
    
}
