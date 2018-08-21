package codex.model;

import codex.command.EditorCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.ArrStr;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;

/**
 * Команда переключения наследования значения свойства от одноименного свойства 
 * родительской сущности.
 */
public class OverrideProperty extends EditorCommand {
    
    private static final ImageIcon           OVERRIDE = ImageUtils.resize(ImageUtils.getByPath("/images/override.png"), 18, 18);
    private static final ImageIcon           INHERIT  = ImageUtils.resize(ImageUtils.getByPath("/images/inherit.png"), 18, 18);
    private final static IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private Consumer<PropertyHolder> switcher = (holder) -> {};
    private Consumer<PropertyHolder> updater  = (holder) -> {};
    private Consumer<PropertyHolder> saver    = (holder) -> {}; 

    /**
     * Конструктор команды.
     * @param childHolder Свойство которое наследует значение.
     * @param parentHolder Одноименное свойство родительской сущности.
     */
    public OverrideProperty(Entity entity, PropertyHolder propHolder, PropertyHolder parentHolder) {
        super(propHolder.isInherited() ? OVERRIDE : INHERIT, Language.get("title"));
        
        activator = (holders) -> {
            button.setEnabled(
                    holders != null && holders.length > 0 && 
                    !(holders.length > 1 && !multiContextAllowed()) && (
                            available == null || Arrays.asList(holders).stream().allMatch(available)
                    )
            );
        };
        
        switcher = (holder) -> {
            if (entity.model.getChanges().contains(propHolder.getName())) {
                MessageBox.show(
                    MessageType.CONFIRMATION, null, 
                    Language.get("error@unsavedprop"), 
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            propHolder.setValue(entity.model.getValue(propHolder.getName()));
                            updater.accept(holder);
                            saver.accept(holder);
                            ((JComponent) entity.model.getEditor(propHolder.getName())).updateUI();
                        }
                    }
                );
            } else {
                updater.accept(holder);
                saver.accept(holder);
            }
        };
        
        updater = (holder) -> {
            holder.setInherited(holder.isInherited() ? null : parentHolder);
            button.setIcon(holder.isInherited() ? OVERRIDE : INHERIT);
        };
        
        saver = (holder) -> {
            List<String> overrideProps = (List<String>) IComplexType.coalesce(
                    entity.model.getValue(EntityModel.OVR),
                    new LinkedList<>()
            );
            if (holder.isInherited()) {
                overrideProps.remove(propHolder.getName());
            } else {
                overrideProps.add(propHolder.getName());
            }
            
            // Установка значения без вызова событий
            entity.model.getProperty(EntityModel.OVR).getPropValue().valueOf(ArrStr.merge(overrideProps));
            Map<String, IComplexType> values = new LinkedHashMap<>();
            values.put(EntityModel.OVR, entity.model.getProperty(EntityModel.OVR).getPropValue());
            int result = CAS.updateClassInstance(entity.getClass(), entity.model.getID(), values);
            if (result != IConfigStoreService.RC_SUCCESS) {
                MessageBox.show(MessageType.ERROR, Language.get(EntityModel.class.getSimpleName(), "error@notsaved"));
            }
        };
        
        if (entity.model.getValue(EntityModel.OVR) != null) {            
            List<String> overrideProps = (List<String>) entity.model.getValue(EntityModel.OVR);
            if (!overrideProps.contains(propHolder.getName())) {
                updater.accept(propHolder);
            }
        } else {
            updater.accept(propHolder);
        }
    }

    @Override
    public void execute(PropertyHolder context) {
        switcher.accept(context);
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }

}
