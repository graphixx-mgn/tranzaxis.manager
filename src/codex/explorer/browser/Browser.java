package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.presentation.EditorPresentation;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Панель просмотра проводника. Отображает презентации редактора и селектора
 * активного узла дерева.
 */
public final class Browser extends JPanel {
    
    private final Launcher launchPanel;
    private final JPanel   editorPanel;
    private final JPanel   selectorPanel;
    
    /**
     * Конструктор панели.
     */
    public Browser() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(0, 5, 5, 5));
        
        launchPanel   = new Launcher();
        add(launchPanel, BorderLayout.CENTER);
        
        editorPanel = new JPanel(new BorderLayout());
        selectorPanel = new JPanel(new BorderLayout());
    }
    
    /**
     * Загружает указанный узел в панель просмотра.
     */
    public void browse(INode node) {
        if (launchPanel.isVisible()) {
            launchPanel.setVisible(false);
            add(editorPanel,   BorderLayout.NORTH);
            add(selectorPanel, BorderLayout.CENTER);
        }
        
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
