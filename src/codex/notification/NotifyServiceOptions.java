package codex.notification;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.EntityRef;
import codex.type.Enum;


public class NotifyServiceOptions extends CommonServiceOptions {
    
    public  final static String PROP_CONDITION  = "condition";
    
    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addUserProp(PROP_CONDITION,  new Enum(NotifyCondition.INACTIVE), false, Access.Select);
    }
    
    public final NotifyCondition getCondition() {
        return (NotifyCondition) model.getValue(PROP_CONDITION);
    }
    
}
