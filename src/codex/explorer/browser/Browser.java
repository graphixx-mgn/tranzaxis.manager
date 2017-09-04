package codex.explorer.browser;

import codex.explorer.tree.AbstractNode;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public final class Browser extends JPanel {
    
    private final JPanel editorPanel;
    private final JPanel selectorPanel;
    
    public Browser() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        editorPanel = new JPanel(new BorderLayout());
        add(editorPanel, BorderLayout.NORTH);
        
        selectorPanel = new JPanel(new BorderLayout());
        add(selectorPanel, BorderLayout.CENTER);
    }
    
    public void browse(AbstractNode node) {
        editorPanel.removeAll();
        editorPanel.revalidate();
        editorPanel.repaint();
        
        selectorPanel.removeAll();
        selectorPanel.revalidate();
        selectorPanel.repaint();
        
        if (node.getEditorPresentation() != null) {
            editorPanel.add(node.getEditorPresentation());
        }
        if (node.getSelectorPresentation() != null) {
            selectorPanel.add(node.getSelectorPresentation());
        }
    }
    
}
