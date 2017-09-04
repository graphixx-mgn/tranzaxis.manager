package codex.presentation;

import codex.explorer.tree.AbstractNode;
import java.awt.BorderLayout;
import javax.swing.JPanel;

public final class EditorPresentation extends JPanel {
 
    public EditorPresentation(AbstractNode node) {
        super(new BorderLayout());
        
        add(new EditorPage(node));
    }
    
}
