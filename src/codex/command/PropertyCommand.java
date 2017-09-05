package codex.command;

import codex.component.button.CommandButton;
import codex.component.button.IButton;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;

public abstract class PropertyCommand implements ICommand<PropertyHolder>, ActionListener {
    
    protected PropertyHolder  context;
    protected IButton         button;
    
    public PropertyCommand(ImageIcon icon, String title) {
        this.button = new CommandButton(ImageUtils.resize(icon, 20, 20));
        this.button.addActionListener(this);
        this.button.setHint(title);
    }
    
    @Override
    public IButton getButton() {
        return button;
    }

    @Override
    public void setContext(PropertyHolder... context) {
        this.context = context[0];
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        execute(context);
    }

    @Override
    public PropertyHolder[] getContext() {
        return new PropertyHolder[]{ context };
    }
    
}
