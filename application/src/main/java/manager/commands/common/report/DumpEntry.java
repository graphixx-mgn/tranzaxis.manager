package manager.commands.common.report;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import java.io.File;

@BranchLink(priority = 3)
public class DumpEntry extends FileEntry {

    public DumpEntry(EntityRef owner, String filePath) {
        super(owner, ImageUtils.getByPath("/images/thread.png"), filePath);
        if (filePath != null) {
            File file = new File(filePath);
            setTitle(file
                    .getParentFile()
                    .getParentFile()
                    .getParentFile()
                    .getParentFile()
                    .getName()
                    .concat(File.separator)
                    .concat(file.getName())
            );
        }
    }
}
