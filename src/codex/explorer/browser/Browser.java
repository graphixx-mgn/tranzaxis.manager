package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.presentation.EditorPresentation;
import java.awt.BorderLayout;
import javax.swing.JPanel;

/**
 * Панель просмотра проводника. Отображает презентации редактора и селектора
 * активного узла дерева.
 */
public final class Browser extends JPanel {
    
    private final JPanel   editorPanel;
    private final JPanel   selectorPanel;
    
    /**
     * Конструктор панели.
     */
    public Browser() {
        super(new BorderLayout());

        editorPanel   = new JPanel(new BorderLayout());
        selectorPanel = new JPanel(new BorderLayout());
        
        add(editorPanel,   BorderLayout.NORTH);
        add(selectorPanel, BorderLayout.CENTER);
    }
    
    /**
     * Загружает указанный узел в панель просмотра.
     */
    public void browse(INode node) {
        editorPanel.removeAll();
        editorPanel.revalidate();
        editorPanel.repaint();
        
        selectorPanel.removeAll();
        selectorPanel.revalidate();
        selectorPanel.repaint();
        
        if (node.getEditorPresentation() != null) {
            EditorPresentation presentation = node.getEditorPresentation();
            editorPanel.add(presentation);
            presentation.activateCommands();
        }
        if (node.getSelectorPresentation() != null) {
            selectorPanel.add(node.getSelectorPresentation());
        }
    }
    
}
