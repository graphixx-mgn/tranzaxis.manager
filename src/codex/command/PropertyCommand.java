package codex.command;

import codex.component.button.IButton;
import codex.property.PropertyHolder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.swing.ImageIcon;

public abstract class PropertyCommand implements ICommand<PropertyHolder>, ActionListener {
    
    private final ImageIcon icon;
    private List<PropertyHolder> context;
    
    public PropertyCommand(ImageIcon icon) {
        this.icon = icon;
    }
    
    @Override
    public ImageIcon getIcon() {
        return icon;
    }

    @Override
    public void bindButton(IButton button) {
        button.addActionListener(this);
    }

    @Override
    public void setContext(PropertyHolder... context) {
        this.context = Arrays.asList(context);
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        for (PropertyHolder propHolder : new LinkedList<>(context)) {
            execute(propHolder);
        }
    }
    
}
