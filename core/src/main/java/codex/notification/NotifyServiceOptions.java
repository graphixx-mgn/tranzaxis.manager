package codex.notification;

import codex.context.ContextView;
import codex.context.IContext;
import codex.editor.MapEditor;
import codex.log.Logger;
import codex.model.Access;
import codex.model.EntityDefinition;
import codex.service.Service;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.utils.Language;
import org.atteo.classindex.ClassIndex;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@EntityDefinition(icon = "/images/notify.png")
public class NotifyServiceOptions extends Service<NotificationService> {
    
    final static String PROP_CONDITIONS  = "conditions";
    final static String PROP_READTRIGGER = "read.trigger";

    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        Map<ContextView, NotifyCondition> sources = StreamSupport.stream(ClassIndex.getSubclasses(IContext.class).spliterator(), false)
                .filter(aClass -> aClass.isAnnotationPresent(NotifySource.class))
                .map(ctxClass -> Logger.getContextRegistry().getContext(ctxClass))
                .collect(Collectors.toMap(
                        ContextView::new,
                        ctxInfo -> ctxInfo.getClazz().getAnnotation(NotifySource.class).condition()
                ));

        // Properties
        model.addUserProp(PROP_CONDITIONS,
                new codex.type.Map<ContextView, NotifyCondition>(
                        new EntityRef<>(ContextView.class),
                        new Enum<>(NotifyCondition.ALWAYS),
                        sources
                ) {
                    @Override
                    public void valueOf(String value) {
                        if (value != null && !value.isEmpty()) {
                            List<String> list = ArrStr.parse(value);
                            Map<ContextView, NotifyCondition> propValue = getValue();
                            for (int keyIdx = 0; keyIdx < list.size(); keyIdx = keyIdx+2) {
                                ContextView ctxView = new ContextView(Logger.getContextRegistry().getContext(list.get(keyIdx)));
                                if (propValue.containsKey(ctxView)) {
                                    NotifyCondition ctxCondition = NotifyCondition.valueOf(list.get(keyIdx+1));
                                    propValue.put(ctxView, ctxCondition);
                                }
                            }
                            setValue(propValue);
                        }
                    }

                    @Override
                    public String toString() {
                        Map<ContextView, NotifyCondition> propValue = getValue();
                        if (propValue == null || propValue.isEmpty()) {
                            return "";
                        } else {
                            List<String> list = new LinkedList<>();
                            propValue.forEach((k, v) -> {
                                list.add(k.getContextClass().getTypeName());
                                list.add(v.name());
                            });
                            return ArrStr.merge(list);
                        }
                    }

                    @Override
                    public void setValue(Map<ContextView, NotifyCondition> value) {
                        super.setValue(value);
                    }
                },
                false, Access.Select
        );
        model.addUserProp(PROP_READTRIGGER, new Enum<>(MessageView.ReadTrigger.OnClick), true, Access.Select);

        model.addPropertyGroup(Language.get("group@system"), PROP_CONDITIONS);
        model.addPropertyGroup(Language.get("group@inbox"),  PROP_READTRIGGER);

        // Editor settings
        ((MapEditor) model.getEditor(PROP_CONDITIONS)).setMode(EnumSet.noneOf(MapEditor.EditMode.class));
    }
    
    @SuppressWarnings("unchecked")
    final java.util.Map<ContextView, NotifyCondition> getSources() {
        return (java.util.Map<ContextView, NotifyCondition>) model.getValue(PROP_CONDITIONS);
    }
    
}
