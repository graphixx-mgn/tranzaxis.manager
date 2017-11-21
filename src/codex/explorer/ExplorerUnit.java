package codex.explorer;

import codex.explorer.browser.Browser;
import codex.explorer.tree.INode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.MatteBorder;
import javax.swing.tree.TreePath;

public final class ExplorerUnit extends AbstractUnit {
    
    private final NodeTreeModel treeModel;
    private final Navigator     navigator;
    private final Browser       browser;
    
    private JScrollPane navigatePanel;
    private JScrollPane browsePanel;
    
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
        splitPanel.setDividerLocation(250);
        splitPanel.setContinuousLayout(true);
        splitPanel.setDividerSize(6);
        splitPanel.setBorder(new MatteBorder(1, 0, 0, 0, Color.GRAY));

        JPanel leftPanel  = new JPanel(new BorderLayout());
        JPanel rightPanel = new JPanel(new BorderLayout());
        leftPanel.setMinimumSize(new Dimension(250, 0));
        rightPanel.setMinimumSize(new Dimension(500, 0));
        
        navigatePanel = new JScrollPane();
        navigatePanel.setBorder(null);
        leftPanel.add(navigatePanel, BorderLayout.CENTER);
        
        browsePanel = new JScrollPane();
        browsePanel.setBorder(null);
        rightPanel.add(browsePanel, BorderLayout.CENTER);

        splitPanel.setLeftComponent(leftPanel);
        splitPanel.setRightComponent(rightPanel);
        return splitPanel;
    }

    @Override
    public void viewportBound() {
        navigator.setModel(treeModel);
        navigatePanel.setViewportView(navigator);
        browsePanel.setViewportView(browser);
    }
    
}
