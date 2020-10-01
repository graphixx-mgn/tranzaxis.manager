package codex.mask;

import codex.editor.FilePathEditor;
import net.jcip.annotations.ThreadSafe;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Маска, указывающая редактору {@link FilePathEditor} выбирать только папки.
 * Фильтр файлов не используется.
 */
@ThreadSafe
public class DirMask implements IPathMask {

    @Override
    public int getSelectionMode() {
        return JFileChooser.DIRECTORIES_ONLY;
    }

    @Override
    public FileNameExtensionFilter getFilter() {
        return null;
    }
    
}
