package manager.commands.common;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;
import javax.swing.*;

class RepoEntity extends Catalog {

    private final static ImageIcon IMG_REPO  = ImageUtils.getByPath("/images/repository.png");
    private final static ImageIcon IMG_TRASH = ImageUtils.combine(ImageUtils.grayscale(IMG_REPO), ImageUtils.getByPath("/images/unavailable.png"));

    RepoEntity(String repoDirName) {
        super(
                null,
                repoDirName.equals(DiskUsageReport.TRASH) ? IMG_TRASH : IMG_REPO,
                repoDirName,
                null
        );
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return EntryEntity.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

}