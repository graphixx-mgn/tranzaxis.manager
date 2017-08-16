package codex.explorer;

import codex.explorer.tree.Node;
import codex.explorer.tree.NodeRenderer;
import codex.explorer.tree.NodeSelectionListener;
import codex.explorer.tree.NodeTreeModel;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.tree.TreeSelectionModel;

public final class ExplorerUnit extends AbstractUnit {
    
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
        tree.setCellRenderer(new NodeRenderer());
        tree.addTreeSelectionListener(new NodeSelectionListener());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        
        Node root = new Node("Settings", ImageUtils.getByPath("/images/settings.png"));
        Node bases = new Node("Databases", ImageUtils.getByPath("/images/database.png")).setMode(Node.MODE_NONE);
        bases.insert(new Node("Base 1", ImageUtils.getByPath("/images/database.png")).setMode(Node.MODE_NONE));
        bases.insert(new Node("Base 2", ImageUtils.getByPath("/images/database.png")).setMode(Node.MODE_NONE));
        bases.insert(new Node("Base 3", ImageUtils.getByPath("/images/database.png")).setMode(Node.MODE_NONE));
        root.insert(new Node("Repositories", ImageUtils.getByPath("/images/debug.png")).setMode(Node.MODE_NONE));
        root.insert(bases);
        root.insert(new Node("Systems", ImageUtils.getByPath("/images/system.png")).setMode(Node.MODE_NONE));
        
        NodeTreeModel treeModel = new NodeTreeModel(root);
        tree.setModel(treeModel);
        
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        leftPanel.add(scroll, BorderLayout.CENTER);

        splitPanel.setLeftComponent(leftPanel);
        splitPanel.setRightComponent(rightPanel);
        return splitPanel;
    }
    
}
