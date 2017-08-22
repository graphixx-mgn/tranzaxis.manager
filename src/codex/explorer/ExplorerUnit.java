package codex.explorer;

import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.MatteBorder;

public final class ExplorerUnit extends AbstractUnit {
    
    private final NodeTreeModel treeModel;
    private final Navigator     navigator;
    
    private JScrollPane navigatePanel;
    
    public ExplorerUnit(NodeTreeModel treeModel) {
        this.treeModel = treeModel;
        this.navigator = new Navigator();
    }
    
    @Override
    public JComponent createViewport() {
        JSplitPane splitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPanel.setDividerLocation(250);
        splitPanel.setContinuousLayout(true);
        splitPanel.setDividerSize(6);
        splitPanel.setBorder(new MatteBorder(1, 0, 0, 0, Color.GRAY));

        JPanel leftPanel  = new JPanel(new BorderLayout());
        JPanel rightPanel = new JPanel();
        leftPanel.setMinimumSize(new Dimension(250, 0));
        rightPanel.setMinimumSize(new Dimension(500, 0));
        
        navigatePanel = new JScrollPane();
        navigatePanel.setBorder(null);
        leftPanel.add(navigatePanel, BorderLayout.CENTER);

        splitPanel.setLeftComponent(leftPanel);
        splitPanel.setRightComponent(rightPanel);
        return splitPanel;
    }

    @Override
    public void viewportBound() {
        navigator.setModel(treeModel);
        navigatePanel.setViewportView(navigator);
    }
    
}
