package codex.presentation;

import codex.command.PropertyCommand;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import javax.swing.ImageIcon;

public class OverrideValue extends PropertyCommand {
        
    public static final ImageIcon OVERRIDE = ImageUtils.resize(ImageUtils.getByPath("/images/override.png"), 20, 20);
    public static final ImageIcon INHERIT  = ImageUtils.resize(ImageUtils.getByPath("/images/inherit.png"), 20, 20);
    
    private final PropertyHolder parentHolder;

    public OverrideValue(PropertyHolder parentHolder) {
        super(OVERRIDE);
        this.parentHolder = parentHolder;
    }

    @Override
    public void execute(PropertyHolder context) {
        context.setOverride(context.isOverridden() ? null : parentHolder);
        button.setIcon(context.isOverridden() ? OVERRIDE : INHERIT);
    }

}
