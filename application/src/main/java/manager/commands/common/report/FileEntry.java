package manager.commands.common.report;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import org.apache.commons.io.FileDeleteStrategy;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

@BranchLink(priority = 1)
public class FileEntry extends Entry {

    public FileEntry(EntityRef owner, String filePath) {
        this(owner, ImageUtils.getByPath("/images/unknown_file.png"), filePath);
    }

    protected FileEntry(EntityRef owner, ImageIcon icon, String filePath) {
        super(owner, icon, filePath);
    }

    @Override
    protected void deleteEntry() {
        File file = new File(getPID());
        try {
            FileDeleteStrategy.NORMAL.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (!file.exists()) {
               getParent().detach(this);
            } else {
                fireChangeEvent();
            }
        }
    }
}
