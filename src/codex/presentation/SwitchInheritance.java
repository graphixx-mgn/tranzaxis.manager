package codex.presentation;

import codex.command.EditorCommand;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.function.Consumer;
import javax.swing.ImageIcon;

/**
 * Команда переключения наследования значения свойства от одноименного свойства 
 * родительской сущности.
 */
public class SwitchInheritance extends EditorCommand {
    
    public static final ImageIcon OVERRIDE = ImageUtils.resize(ImageUtils.getByPath("/images/override.png"), 18, 18);
    public static final ImageIcon INHERIT  = ImageUtils.resize(ImageUtils.getByPath("/images/inherit.png"), 18, 18);
    
    private final PropertyHolder           parentHolder;
    private final Consumer<PropertyHolder> switcher;

    /**
     * Конструктор команды.
     * @param childHolder Свойство которое наследует значение.
     * @param parentHolder Одноименное свойство родительской сущности.
     */
    public SwitchInheritance(PropertyHolder childHolder, PropertyHolder parentHolder) {
        super(childHolder.isInherited() ? OVERRIDE : INHERIT, Language.get("override"));
        switcher = (holder) -> {
            holder.setInherited(holder.isInherited() ? null : parentHolder);
            button.setIcon(holder.isInherited() ? OVERRIDE : INHERIT);
            button.setHint(Language.get(holder.isInherited() ? "override" : "inherit"));
        };
        this.parentHolder = parentHolder;
    }

    @Override
    public void execute(PropertyHolder context) {
        switcher.accept(context);
    }

}
