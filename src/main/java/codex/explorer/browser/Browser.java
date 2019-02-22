package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
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
     * @param node Ссылка на узел.
     */
    public void browse(INode node) {
        editorPanel.removeAll();
        editorPanel.revalidate();
        editorPanel.repaint();
        
        selectorPanel.removeAll();
        selectorPanel.revalidate();
        selectorPanel.repaint();

        EditorPresentation editorPresentation = node.getEditorPresentation();
        if (editorPresentation != null) {
            editorPanel.add(editorPresentation);
            editorPresentation.refresh();
        }
        SelectorPresentation selectorPresentation = node.getSelectorPresentation();
        if (selectorPresentation != null) {
            selectorPanel.add(selectorPresentation);
            selectorPresentation.refresh();
        }
    }
    
}
