package codex.mask;

import codex.editor.FilePathEditor;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Маска, указывающая редактору {@link FilePathEditor} выбирать файлы.
 */
public class FileMask implements IPathMask {
    
    private final FileNameExtensionFilter filter;
    
    /**
     * Конструктор маски без ограничения на расширения файлов.
     */
    public FileMask() {
        this(null);
    }
    
    /**
     * Конструктор маски c ограничением на расширения файлов.
     * @param filter Экземпляр фильтра расширений.
     * <pre>
     *   new FileMask(new FileNameExtensionFilter("JPEG files", "jpg", "jpeg"));
     * </pre>
     */
    public FileMask(FileNameExtensionFilter filter) {
        this.filter = filter;
    }

    @Override
    public int getSelectionMode() {
        return JFileChooser.FILES_ONLY;
    }

    @Override
    public FileNameExtensionFilter getFilter() {
        return filter;
    }
    
}
