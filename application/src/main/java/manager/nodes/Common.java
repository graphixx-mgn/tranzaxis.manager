package manager.nodes;

import codex.explorer.tree.INode;
import codex.mask.DirMask;
import codex.model.*;
import codex.type.EntityRef;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.file.Path;
import java.util.List;
import manager.commands.common.DiskUsageReport;

public final class Common extends Catalog {
    
    private final static String PROP_WORK_DIR = "workDir";

    static {
        CommandRegistry.getInstance().registerCommand(DiskUsageReport.class);
    }

    public Common(EntityRef owner, String title) {
        this();
    }

    public Common() {
        super(null, ImageUtils.getByPath("/images/settings.png"), null, Language.get("desc"));

        // Properties
        model.addUserProp(PROP_WORK_DIR,  new FilePath(null).setMask(new DirMask()), true, Access.Select);

        // Handlers
        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.forEach((propName) -> {
                    switch (propName) {
                        case PROP_WORK_DIR:
                            childrenList().forEach((child) -> setChildMode(child, getWorkDir() != null));
                            break;
                    }
                });          
            }
        });
    }
    
    public final Path getWorkDir() {
        return (Path) model.getValue(PROP_WORK_DIR);
    }

    @Override
    public void attach(INode child) {
        setChildMode(child, getWorkDir() != null);
        super.attach(child);
    }
    
    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    private void setChildMode(INode node, boolean enabled) {
        node.setMode(enabled ? MODE_ENABLED + MODE_SELECTABLE : MODE_NONE);
    }
    
}