package manager.nodes;

import codex.explorer.tree.INode;
import codex.type.Str;
import codex.utils.ImageUtils;


public class Release extends BinarySource {

    public Release(INode parent, String title) {
        super(parent, ImageUtils.getByPath("/images/release.png"), title);
        
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getPID();
        });
    }

    @Override
    public Class getChildClass() {
        return null;
    }
    
}
