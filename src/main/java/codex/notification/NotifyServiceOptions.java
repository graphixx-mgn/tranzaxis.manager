package codex.notification;

import codex.editor.MapEditor;
import codex.model.Access;
import codex.service.ContextPresentation;
import codex.service.ContextType;
import codex.service.LocalServiceOptions;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Map;
import codex.utils.ImageUtils;
import java.util.LinkedHashMap;

public class NotifyServiceOptions extends LocalServiceOptions<NotificationService> {
    
    private final static String PROP_CONDITIONS = "conditions";
    
    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/notify.png"));
        
        model.addUserProp(PROP_CONDITIONS,
                new Map<>(
                        ContextType.class,
                        new Enum<NotifyCondition>(NotifyCondition.class){}.getClass(),
                        new LinkedHashMap<>()
                ),
                false, Access.Select
        );
        ((MapEditor) model.getEditor(PROP_CONDITIONS)).setMode(MapEditor.EditMode.ModifyPermitted);
    }
    
    public final java.util.Map<ContextPresentation, NotifyCondition> getSources() {
        return (java.util.Map<ContextPresentation, NotifyCondition>) model.getValue(PROP_CONDITIONS);
    }

    public final void setSources(java.util.Map<ContextPresentation, NotifyCondition> value) {
        model.setValue(PROP_CONDITIONS, value);
    }
    
}
