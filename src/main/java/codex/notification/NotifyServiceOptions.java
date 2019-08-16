package codex.notification;

import codex.editor.MapEditor;
import codex.model.Access;
import codex.context.ContextPresentation;
import codex.context.ContextType;
import codex.context.IContext;
import codex.service.LocalServiceOptions;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.utils.ImageUtils;
import org.atteo.classindex.ClassIndex;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class NotifyServiceOptions extends LocalServiceOptions<NotificationService> {
    
    private final static String PROP_CONDITIONS = "conditions";
    
    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/notify.png"));

        Map<ContextPresentation, NotifyCondition> sources = StreamSupport.stream(ClassIndex.getSubclasses(IContext.class).spliterator(), false)
                .filter(aClass -> aClass.isAnnotationPresent(NotifySource.class))
                .collect(Collectors.toMap(
                        ContextPresentation::new,
                        ctxClass -> ctxClass.getAnnotation(NotifySource.class).condition()
                ));
        
        model.addUserProp(PROP_CONDITIONS,
                new codex.type.Map<>(
                        ContextType.class,
                        new Enum<NotifyCondition>(NotifyCondition.class){}.getClass(),
                        sources
                ),
                false, Access.Select
        );
        ((MapEditor) model.getEditor(PROP_CONDITIONS)).setMode(MapEditor.EditMode.ModifyPermitted);
    }
    
    final java.util.Map<ContextPresentation, NotifyCondition> getSources() {
        return (java.util.Map<ContextPresentation, NotifyCondition>) model.getValue(PROP_CONDITIONS);
    }
    
}
