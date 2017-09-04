package codex.command;

import codex.component.button.IButton;
import javax.swing.ImageIcon;

public interface ICommand {
    
    public ImageIcon getIcon();
    public void      bindButton(IButton button);
    public void      execute();
    
}
