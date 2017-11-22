package codex.explorer.browser;

import codex.explorer.tree.INode;
import codex.presentation.EditorPresentation;
import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.LineBorder;

/**
 * Панель просмотра проводника. Отображает презентации редактора и селектора
 * активного узла дерева.
 */
public final class Browser extends JPanel {
    
    private final static ImageIcon LAUNCH = ImageUtils.resize(ImageUtils.getByPath("/images/launch.png"), 20, 20);
    private final static ImageIcon VIEWER = ImageUtils.resize(ImageUtils.getByPath("/images/viewer.png"), 20, 20);
    
    private final JTabbedPane tabPanel;
    private final Launcher launchPanel;
    private final JPanel   editorPanel;
    private final JPanel   selectorPanel;
    
    /**
     * Конструктор панели.
     */
    public Browser() {
        super(new BorderLayout());
        
        launchPanel   = new Launcher();
        editorPanel   = new JPanel(new BorderLayout());
        selectorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(new LineBorder(Color.GREEN, 1));
        
        JPanel viewerPanel = new JPanel(new BorderLayout());
        viewerPanel.add(editorPanel,   BorderLayout.NORTH);
        viewerPanel.add(selectorPanel, BorderLayout.CENTER);

        tabPanel = new JTabbedPane();
        tabPanel.setTabPlacement(JTabbedPane.LEFT);
        tabPanel.addTab(null, LAUNCH, launchPanel);
        tabPanel.addTab(null, VIEWER, viewerPanel);
        tabPanel.setDisabledIconAt(0, ImageUtils.grayscale(LAUNCH));
        tabPanel.setDisabledIconAt(1, ImageUtils.grayscale(VIEWER));
        tabPanel.setEnabledAt(1, false);
        add(tabPanel, BorderLayout.CENTER);
    }
    
    /**
     * Загружает указанный узел в панель просмотра.
     */
    public void browse(INode node) {
        if (tabPanel.getSelectedIndex() != 1) {
            tabPanel.setSelectedIndex(1);
        }
        if (!tabPanel.isEnabledAt(1)) {
            tabPanel.setEnabledAt(1, true);
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
