package codex.explorer.browser;

import codex.explorer.tree.AbstractNode;
import codex.model.Access;
import codex.property.PropertyHolder;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public final class Browser extends JPanel {
    
    private final JPanel editorPanel;
    private final JPanel selectorPanel;
    
    public Browser() {
        super(new BorderLayout());
        //setBorder(new EmptyBorder(5, 5, 5, 5));
        setBorder(new LineBorder(Color.BLUE, 2));
        
        editorPanel = new JPanel(new BorderLayout());
        add(editorPanel, BorderLayout.NORTH);
        
        selectorPanel = new JPanel(new BorderLayout());
        add(selectorPanel, BorderLayout.CENTER);
        
        //***
        editorPanel.setBorder(new LineBorder(Color.RED, 2));
        selectorPanel.setBorder(new LineBorder(Color.GREEN, 2));
        
        editorPanel.add(new JLabel("Editor"));
        selectorPanel.add(new JLabel("Selector"));
        //***
    }
    
    public void browse(AbstractNode node) {
        
        for (PropertyHolder propHolder : node.model.getProperties(Access.Any)) {
            System.out.println(propHolder.getTitle());
        }
        
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
