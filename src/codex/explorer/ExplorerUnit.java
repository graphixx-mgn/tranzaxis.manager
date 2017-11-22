package codex.explorer;

import codex.explorer.browser.Browser;
import codex.explorer.launcher.Launcher;
import codex.explorer.tree.INode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.border.MatteBorder;
import javax.swing.tree.TreePath;

public final class ExplorerUnit extends AbstractUnit {
    
    private final static ImageIcon LAUNCH = ImageUtils.resize(ImageUtils.getByPath("/images/launch.png"), 20, 20);
    private final static ImageIcon VIEWER = ImageUtils.resize(ImageUtils.getByPath("/images/viewer.png"), 20, 20);
    
    private JTabbedPane tabPanel;
    private JScrollPane launchPanel;
    private JScrollPane browsePanel;
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
        tabPanel = new JTabbedPane();
        tabPanel.setFocusable(false);
        tabPanel.setBorder(new MatteBorder(1, 0, 0, 0, Color.GRAY));
        tabPanel.setTabPlacement(JTabbedPane.LEFT);
        
        launchPanel = new JScrollPane();
        launchPanel.setBorder(null);
        tabPanel.addTab(null, LAUNCH, launchPanel);

        JSplitPane splitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPanel.setBorder(null);
        splitPanel.setDividerLocation(250);
        splitPanel.setContinuousLayout(true);
        splitPanel.setDividerSize(6);
        tabPanel.addTab(null, VIEWER, splitPanel);
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setMinimumSize(new Dimension(250, 0));
        navigatePanel = new JScrollPane();
        navigatePanel.setBorder(null);
        leftPanel.add(navigatePanel, BorderLayout.CENTER);
        splitPanel.setLeftComponent(leftPanel);
        
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setMinimumSize(new Dimension(500, 0));
        browsePanel = new JScrollPane();
        browsePanel.setBorder(null);
        rightPanel.add(browsePanel, BorderLayout.CENTER);
        splitPanel.setRightComponent(rightPanel);
        
        return tabPanel;
    }

    @Override
    public void viewportBound() {
        launchPanel.setViewportView(new Launcher());
        navigator.setModel(treeModel);
        navigatePanel.setViewportView(navigator);
        browsePanel.setViewportView(browser);
    }
    
}
