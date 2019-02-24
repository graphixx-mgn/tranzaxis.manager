package codex.model;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.AbstractEditor;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.ImageIcon;

/**
 * Команда переключения наследования значения свойства от одноименного свойства 
 * родительской сущности.
 */
public final class OverrideProperty extends EditorCommand {
    
    private static final ImageIcon           OVERRIDE = ImageUtils.resize(ImageUtils.getByPath("/images/override.png"), 18, 18);
    private static final ImageIcon           INHERIT  = ImageUtils.resize(ImageUtils.getByPath("/images/inherit.png"), 18, 18);
    private final static IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private Consumer<PropertyHolder> updater;

    /**
     * Конструктор команды.
     * @param parentModel Ссылка на родительскую модель.
     * @param childModel  Ссылка на дочернюю модель.
     * @param propName Свойство которое следует перекрыть.
     */
    @SuppressWarnings("unchecked")
    public OverrideProperty(EntityModel parentModel, EntityModel childModel, String propName) {
        super(childModel.getProperty(propName).isInherited() ? OVERRIDE : INHERIT, Language.get("title"));
        
        PropertyHolder parentHolder = parentModel.getProperty(propName);
        PropertyHolder childHolder  = childModel.getProperty(propName);
        
        childModel.getProperty(EntityModel.OVR).addChangeListener((String name, Object oldValue, Object newValue) -> {
            boolean newOverride = newValue != null && ((List<String>) newValue).contains(propName);

            childHolder.setInherited(newOverride ? null : parentHolder);
            ((AbstractEditor) childModel.getEditor(propName)).updateUI();
            
            boolean oldOverride = ((List<String>) IComplexType.coalesce(
                    childModel.getValue(EntityModel.OVR),
                    new LinkedList<>()
            )).contains(propName);
            
            if (oldOverride != newOverride) {
                childModel.getEditor(propName).getLabel().setText(childModel.getProperty(propName).getTitle() + " *");
            }
        });
        
        activator = (holder) -> new CommandStatus(true, childModel.getProperty(propName).isInherited() ? OVERRIDE : INHERIT);
        
        updater = (holder) -> {
            List<String> overrideProps = (List<String>) IComplexType.coalesce(
                    childModel.getUnsavedValue(EntityModel.OVR),
                    new LinkedList<>()
            );
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
    public void execute(PropertyHolder context) {
        updater.accept(context);
    }

}
