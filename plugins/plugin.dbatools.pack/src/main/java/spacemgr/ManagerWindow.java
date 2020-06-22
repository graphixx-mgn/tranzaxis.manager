package spacemgr;

import codex.component.dialog.Dialog;
import codex.model.Catalog;
import codex.model.Entity;
import codex.presentation.SelectorPresentation;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.command.objects.TableSpace;
import java.awt.*;

public class ManagerWindow extends Dialog {

    ManagerWindow(TableSpaceView tbsView) {
        super(
                Dialog.findNearestWindow(),
                ImageUtils.getByPath(Language.get(TableSpaceManager.class, "icon")),
                Language.get(TableSpaceManager.class, "title"),
                tbsView.getSelectorPresentation(),
                null
        );
        setModal(false);
        SelectorPresentation presentation = tbsView.getSelectorPresentation();
        if (presentation != null) {
            presentation.enableSorting();
            presentation.setColumnRenderer(1, TableSpace.getPctRenderer());
            presentation.setColumnRenderer(2, TableSpace.getSizeRenderer());
            presentation.setColumnRenderer(3, TableSpace.getSizeRenderer());
            presentation.setColumnRenderer(4, TableSpace.getSizeRenderer());
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(800, 600);
    }


    static class TableSpaceView extends Catalog {

        TableSpaceView() {
            super(null, null, null, null);
        }

        @Override
        public Class<? extends Entity> getChildClass() {
            return TableSpace.class;
        }

        @Override
        public boolean allowModifyChild() {
            return false;
        }
    }
}
