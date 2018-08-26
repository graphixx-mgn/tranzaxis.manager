package manager.nodes;

import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;


public class Release extends BinarySource {

    public Release(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/release.png"), title);
        
        // Properties
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getPID();
        });
    }

    @Override
    public Class getChildClass() {
        return null;
    }
    
}
