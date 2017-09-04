package codex.presentation;

import codex.component.button.CommandButton;
import codex.editor.EditorFactory;
import codex.editor.IEditor;
import codex.explorer.tree.AbstractNode;
import codex.model.Access;
import codex.command.ICommand;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

public final class EditorPage extends JPanel {
    
    public EditorPage(AbstractNode node) {
        super(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 0, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        
        int lineIdx = 1, maxSize = 0;
        for (PropertyHolder propHolder : node.model.getProperties(Access.Edit)) {
            gbc.gridx = 0; 
            gbc.gridy = lineIdx;
            gbc.weightx = 0;
            
            IEditor propEditor = EditorFactory.createEditor(propHolder);
            add(propEditor.getLabel(), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            Box container = new Box(BoxLayout.X_AXIS);
            
            container.add(propEditor.getEditor());
            container.add(Box.createRigidArea(new Dimension(1, 28)));
            
            CommandButton button;
            for (ICommand command : propHolder.getCommands()) {
                button = new CommandButton(ImageUtils.resize(command.getIcon(), 20, 20));
                
                container.add(button);
                container.add(Box.createHorizontalStrut(2));
                command.bindButton(button);
            }
            add(container, gbc);
            
            maxSize = Math.max(maxSize, propEditor.getLabel().getFontMetrics(IEditor.FONT_BOLD).stringWidth(propHolder.getTitle()));            
            lineIdx++;
        }
        add(Box.createHorizontalStrut(Math.max(280, maxSize+30)));
    }
    
}
