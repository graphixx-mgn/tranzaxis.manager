package codex.explorer;

import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.Browser;
import codex.explorer.browser.TabbedMode;
import codex.explorer.tree.INode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.presentation.AncestorAdapter;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.Caller;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreePath;

/**
 * Модуль навигации по древовидной структуре сущностей.
 */
public final class ExplorerUnit extends AbstractUnit {
    
    private static ExplorerUnit INSTANCE;
    public  static ExplorerUnit getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExplorerUnit();
        }
        return INSTANCE;
    }

    private JSplitPane      splitPanel;
    private JPanel          browsePanel;
    private JScrollPane     navigatePanel;
    private final Navigator navigator;
    private final Browser   browser;

    private ExplorerUnit() {
        this(new TabbedMode());
    }
    
    private ExplorerUnit(BrowseMode mode) {
        Class parentUnitClass = Caller.getInstance().getClassStack().stream()
            .skip(1)
            .filter(AbstractUnit.class::isAssignableFrom)
            .findFirst().get();
        Logger.getLogger().debug("Initialize unit: Explorer ({0})", Language.get(parentUnitClass, "unit.title", Language.DEF_LOCALE));
        this.browser   = new Browser(mode);
        this.navigator = new Navigator(parentUnitClass);
        this.navigator.addNavigateListener((TreePath path) -> this.browser.browse((INode) path.getLastPathComponent()));
    }
    
    public void setModel(NodeTreeModel treeModel) {
        navigator.setModel(treeModel);
        ServiceRegistry.getInstance().registerService(new ExplorerAccessService(treeModel));
    }
    
    @Override
    public JComponent createViewport() {
        splitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPanel.setBorder(null);
        splitPanel.setContinuousLayout(true);
        splitPanel.setDividerSize(6);

        JPanel leftPanel = new JPanel(new BorderLayout());
        navigatePanel = new JScrollPane();
        navigatePanel.setBorder(null);
        navigatePanel.setViewportView(navigator);
        leftPanel.add(navigatePanel, BorderLayout.CENTER);
        splitPanel.setLeftComponent(leftPanel);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setMinimumSize(new Dimension(500, 0));
        browsePanel = new JPanel(new BorderLayout());
        browsePanel.setBorder(null);
        rightPanel.add(browsePanel, BorderLayout.CENTER);
        splitPanel.setRightComponent(rightPanel);

        Adjuster adjuster = new Adjuster();
        navigator.addAncestorListener(adjuster);
        navigator.addTreeExpansionListener(adjuster);

        return splitPanel;
    }

    @Override
    public void viewportBound() {
        browsePanel.add(browser, BorderLayout.CENTER);
        navigator.expandRow(0);
    }


    private class Adjuster extends AncestorAdapter implements TreeExpansionListener {

        @Override
        public void ancestorAdded(AncestorEvent e) {
            resizeNavigationPane();
        }

        @Override
        public void treeExpanded(TreeExpansionEvent event) {
            resizeNavigationPane();
        }

        @Override
        public void treeCollapsed(TreeExpansionEvent event) {}

        private void resizeNavigationPane() {
            int dividerPos = splitPanel.getDividerLocation();
            int navWidth   = navigator.getPreferredScrollableViewportSize().width + 20;
            if (navWidth > dividerPos) {
                splitPanel.setDividerLocation(navWidth);
            }
        }
    }
}
