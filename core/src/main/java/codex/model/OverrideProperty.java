package codex.model;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.ImageIcon;

/**
 * Команда переключения наследования значения свойства от одноименного свойства 
 * родительской сущности.
 */
public final class OverrideProperty extends EditorCommand<IComplexType<Object, IMask<Object>>, Object> {
    
    private static final ImageIcon OVERRIDE = ImageUtils.getByPath("/images/override.png");
    private static final ImageIcon INHERIT  = ImageUtils.getByPath("/images/inherit.png");
    
    private Consumer<PropertyHolder> updater;

    public static Map<String, Boolean> getOverrideChanges(EntityModel model) {
        List<String> prevOVR = model.getOverride();
        List<String> currOVR = (List<String>) model.getUnsavedValue("OVR");
        return Stream.concat(prevOVR.stream(), currOVR.stream())
                .distinct().collect(Collectors.toMap(
                        prop -> prop,
                        prop -> !(prevOVR.contains(prop) && currOVR.contains(prop))
                ));
    }

    public static List<String> applyOverrideChanges(List<String> baseList, Map<String, Boolean> changes) {
        List<String> result = new LinkedList<>(baseList);
        for (Map.Entry<String, Boolean> entry : changes.entrySet()) {
            if (entry.getValue()) {
                if (result.contains(entry.getKey())) {
                    result.remove(entry.getKey());
                } else {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    public OverrideProperty(EntityModel parentModel, EntityModel childModel, String propName) {
        this(parentModel, childModel, propName, childModel.getEditor(propName));
    }

    /**
     * Конструктор команды.
     * @param parentModel Ссылка на родительскую модель.
     * @param childModel  Ссылка на дочернюю модель.
     * @param propName Свойство которое следует перекрыть.
     */
    @SuppressWarnings("unchecked")
    public OverrideProperty(EntityModel parentModel, EntityModel childModel, String propName, IEditor propEditor) {
        super(childModel.getProperty(propName).isInherited() ? OVERRIDE : INHERIT, Language.get("title"));
        
        PropertyHolder parentHolder = parentModel.getProperty(propName);
        PropertyHolder childHolder  = childModel.getProperty(propName);
        
        childModel.getProperty(EntityModel.OVR).addChangeListener((String name, Object oldValue, Object newValue) -> {
            boolean newOverride = newValue != null && ((List<String>) newValue).contains(propName);
            boolean oldOverride = oldValue != null && ((List<String>) oldValue).contains(propName);

            if (oldOverride != newOverride) {
                if (oldOverride) {
                    childModel.rollback(propName);
                }
            }

            childHolder.setInherited(newOverride ? null : parentHolder);
            ((AbstractEditor) propEditor).updateUI();

            Map<String, Boolean> overrideChanges = getOverrideChanges(childModel);
            propEditor.getLabel().setText(childModel.getProperty(propName).getTitle() + (
                    Boolean.TRUE.equals(overrideChanges.get(propName)) ?  " *" : ""
            ));

            if (oldOverride != newOverride) {
                if (newOverride) {
                    if (childModel.getProperty(propName).isEmpty()) {
                        childModel.setValue(propName, parentModel.getValue(propName));
                    }
                }
            }
            activate();
        });
        
        activator = (holder) -> new CommandStatus(true, childModel.getProperty(propName).isInherited() ? OVERRIDE : INHERIT);
        
        updater = (holder) -> {
            List<String> overrideProps = (List<String>) childModel.getUnsavedValue(EntityModel.OVR);
            if (!holder.isInherited()) {
                overrideProps.remove(propName);
            } else {
                overrideProps.add(propName);
            }
            childModel.setValue(EntityModel.OVR, overrideProps);
        };
        
        List<String> overrideProps = (List<String>) IComplexType.coalesce(
                childModel.getUnsavedValue(EntityModel.OVR),
                new LinkedList<>()
        );
        childHolder.setInherited(overrideProps.contains(propName) ? null : parentHolder);
    }

    @Override
    public void execute(PropertyHolder<IComplexType<Object, IMask<Object>>, Object> context) {
        updater.accept(context);
    }

}
