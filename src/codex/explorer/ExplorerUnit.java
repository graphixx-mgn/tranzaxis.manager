package codex.explorer;

import codex.explorer.tree.NodeRenderer;
import codex.explorer.tree.NodeSelectionListener;
import codex.explorer.tree.NodeTreeModel;
import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.tree.TreeSelectionModel;

public final class ExplorerUnit extends AbstractUnit {
    
    private final NodeTreeModel treeModel;
    
    public ExplorerUnit(NodeTreeModel treeModel) {
        this.treeModel = treeModel;
    }
    
    private JTree tree;
    
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

        tree = new JTree();
        tree.setFont(new Font("Tahoma", 0, 12));
        tree.setRowHeight(22);
        tree.setBorder(new EmptyBorder(5, 10, 5, 2));
        tree.addTreeSelectionListener(new NodeSelectionListener());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new NodeRenderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        leftPanel.add(scroll, BorderLayout.CENTER);

        splitPanel.setLeftComponent(leftPanel);
        splitPanel.setRightComponent(rightPanel);
        return splitPanel;
    }

    @Override
    public void viewportBound() {
        tree.setModel(treeModel);
    }
    
}
