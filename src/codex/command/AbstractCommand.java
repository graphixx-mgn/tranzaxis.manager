package codex.command;

import codex.component.button.IButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;

public abstract class AbstractCommand implements ICommand, ActionListener {
    
    private final ImageIcon icon;
    
    public AbstractCommand(ImageIcon icon) {
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
    public void actionPerformed(ActionEvent ae) {
        execute();
    }
    
}
