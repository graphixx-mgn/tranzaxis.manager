package codex.explorer;

import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.Browser;
import codex.explorer.browser.TabbedMode;
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

/**
 * Модуль навигации по древовидной структуре сущностей.
 */
public final class ExplorerUnit extends AbstractUnit {
    
    private final static ExplorerUnit INSTANCE = new ExplorerUnit();
    public  final static ExplorerUnit getInstance() {
        return INSTANCE;
    }
    
    private JPanel          browsePanel;
    private JScrollPane     navigatePanel;
    private final Navigator navigator;
    private final Browser   browser;

    private ExplorerUnit() {
        this(new TabbedMode());
    }
    
    private ExplorerUnit(BrowseMode mode) {
        Logger.getLogger().debug("Initialize unit: Explorer");
        this.browser   = new Browser(mode);
        this.navigator = new Navigator();
        this.navigator.addNavigateListener((TreePath path) -> {
            this.browser.browse((INode) path.getLastPathComponent());
        });
    }
    
    public void setModel(NodeTreeModel treeModel) {
        navigator.setModel(treeModel);
        ServiceRegistry.getInstance().registerService(new ExplorerAccessService(treeModel));
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
        navigatePanel.setViewportView(navigator);
        browsePanel.add(browser, BorderLayout.CENTER);
        navigator.expandRow(0);
    }
    
}
