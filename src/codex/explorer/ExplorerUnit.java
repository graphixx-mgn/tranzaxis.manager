package codex.explorer;

import codex.explorer.browser.Browser;
import codex.explorer.tree.INode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.tree.TreePath;

public final class ExplorerUnit extends AbstractUnit {
    
    private JPanel      browsePanel;
    private JScrollPane navigatePanel;
    
    private final NodeTreeModel treeModel;
    private final Navigator     navigator;
    private final Browser       browser;
    
    public ExplorerUnit(NodeTreeModel treeModel) {
        Logger.getLogger().debug("Initialize unit: Explorer");
        ServiceRegistry.getInstance().registerService(new ExplorerAccessService(treeModel));
        
        this.treeModel = treeModel;
        this.navigator = new Navigator();
        this.browser   = new Browser();
        
        this.navigator.addNavigateListener((TreePath path) -> {
            this.browser.browse((INode) path.getLastPathComponent());
        });
    }
    
    @Override
    public JComponent createViewport() {
        JSplitPane splitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPanel.setBorder(null);
        splitPanel.setDividerLocation(250);
        splitPanel.setContinuousLayout(true);
        splitPanel.setDividerSize(6);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setMinimumSize(new Dimension(250, 0));
        navigatePanel = new JScrollPane();
        navigatePanel.setBorder(null);
        leftPanel.add(navigatePanel, BorderLayout.CENTER);
        splitPanel.setLeftComponent(leftPanel);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setMinimumSize(new Dimension(500, 0));
        browsePanel = new JPanel(new BorderLayout());
        browsePanel.setBorder(null);
        rightPanel.add(browsePanel, BorderLayout.CENTER);
        splitPanel.setRightComponent(rightPanel);
        return splitPanel;
    }

    @Override
    public void viewportBound() {
        navigator.setModel(treeModel);
        navigatePanel.setViewportView(navigator);
        browsePanel.add(browser, BorderLayout.CENTER);
    }
    
}
