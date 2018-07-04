package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Access;
import codex.type.Enum;
import codex.type.Int;
import codex.type.Str;
import codex.utils.ImageUtils;
import manager.type.WCStatus;


public class Offshoot extends BinarySource {

    public Offshoot(INode parent, String title) {
        super(parent, ImageUtils.getByPath("/images/branch.png"), title);
        
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getPID();
        });
        model.addDynamicProp("wcStatus", new Enum(WCStatus.Absent), Access.Edit, null);
        model.addUserProp("builtRev",    new Int(null), false, null);
        
        model.getEditor("builtRev").setEditable(false);
    }

    @Override
    public Class getChildClass() {
        return null;
    }
    
}