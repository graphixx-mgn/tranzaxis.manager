package codex.notification;

import codex.editor.MapEditor;
import codex.model.Access;
import codex.service.LocalServiceOptions;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Map;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.util.LinkedHashMap;

public class NotifyServiceOptions extends LocalServiceOptions {
    
    private final static String PROP_SOURCES = "sources";
    
    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/notify.png"));
        
        model.addUserProp(PROP_SOURCES,
                new Map<>(Str.class, new Enum<NotifyCondition>(NotifyCondition.class){}.getClass(), new LinkedHashMap<>()),
                false, Access.Select
        );
        ((MapEditor) model.getEditor(PROP_SOURCES)).setMode(MapEditor.EditMode.ModifyPermitted);
    }
    
    public final java.util.Map<String, NotifyCondition> getSources() {
        return (java.util.Map<String, NotifyCondition>) model.getValue(PROP_SOURCES);
    }
    
}
