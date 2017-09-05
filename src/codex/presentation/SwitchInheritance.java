package codex.presentation;

import codex.command.PropertyCommand;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;

public class SwitchInheritance extends PropertyCommand {
        
    public static final ImageIcon OVERRIDE = ImageUtils.resize(ImageUtils.getByPath("/images/override.png"), 20, 20);
    public static final ImageIcon INHERIT  = ImageUtils.resize(ImageUtils.getByPath("/images/inherit.png"), 20, 20);
    
    private final PropertyHolder parentHolder;

    public SwitchInheritance(PropertyHolder parentHolder) {
        super(OVERRIDE, Language.get("override"));
        this.parentHolder = parentHolder;
    }

    @Override
    public void execute(PropertyHolder context) {
        context.inherit(context.isInherited() ? null : parentHolder);
        button.setIcon(context.isInherited() ? OVERRIDE : INHERIT);
        button.setHint(Language.get(context.isInherited() ? "override" : "inherit"));
    }

}
