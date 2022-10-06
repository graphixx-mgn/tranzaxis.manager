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

public final class Common extends Catalog {
    
    private final static String PROP_WORK_DIR = "workDir";

    public Common(EntityRef<Entity> owner, String title) {
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
                            childrenList().forEach((child) -> setChildMode(child));
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
        setChildMode(child);
        super.attach(child);
    }
    
    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    private void setChildMode(INode node) {
        boolean enabled = getWorkDir() != null || node instanceof DatabaseRoot || node instanceof EnvironmentRoot;
        node.setMode(enabled ? MODE_ENABLED + MODE_SELECTABLE : MODE_NONE);
    }
}