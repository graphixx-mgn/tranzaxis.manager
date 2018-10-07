package codex.launcher;

import codex.command.EntityCommand;
import codex.component.border.DashBorder;
import codex.component.border.RoundedBorder;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Ярлык запуска команды с панели быстрого доступа {@link LauncherUnit}.
 */
class LaunchShortcut extends LaunchButton implements IModelListener, INodeListener {
    
    private final static ImageIcon TILE = ImageUtils.getByPath("/images/strips_red.png");
    private final static ImageIcon LOST = ImageUtils.resize(ImageUtils.getByPath("/images/close.png"), 20, 20);
    private final static ImageIcon LOCK = ImageUtils.resize(ImageUtils.getByPath("/images/lock.png"),  28, 28);
    private final static ImageIcon CMD  = ImageUtils.getByPath("/images/command.png");
    private final static ImageIcon MOVE = ImageUtils.resize(ImageUtils.getByPath("/images/close.png"), 28, 28);
    
    public enum Status {
        UNKNOWN, 
        AVAILABLE, 
        DISABLED,
        LOCKED,
        LOST_COMMAND,
        LOST_ENTITY;
    }
    
    private final JPanel   controls;
    private final Shortcut shortcut;
    private       Status   status = Status.UNKNOWN;
    
    /**
     * Конструктор ярлыка.
     * @param entity Ссылка на сущность.
     * @param command Ссылка на команду, доступную для класса сущности.
     */
    LaunchShortcut(Shortcut shortcut) {
        super(shortcut.model.getPID(), LOCK);
        this.shortcut = shortcut;
        setLayout(null);
        updateStatus();

        JLabel signDelete = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/clearval.png"), 18, 18));
        signDelete.setBorder(new EmptyBorder(2, 3, 2, 3));
        
        controls = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2d.fill(new TopEnd(getWidth(), getHeight(), 18));
                g2d.dispose();
            }
        };
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.setOpaque(true);
        controls.setVisible(false);
        controls.add(signDelete);
        controls.add(Box.createHorizontalStrut(
                getPreferredSize().width - controls.getComponentCount()*24 - 2
        ), 0);
        add(controls);
        controls.setBounds(
                1,
                controls.getInsets().top + 1,
                controls.getPreferredSize().width, 
                controls.getPreferredSize().height
        );
        
        addActionListener((event) -> {
            if (status == Status.AVAILABLE) {
                SwingUtilities.invokeLater(() -> {
                    Entity entity = (Entity) shortcut.model.getValue("entity");
                    EntityCommand command = entity.getCommand((String) shortcut.model.getValue("command"));
                    
                    Entity[] prevContext = command.getContext();
                    Map<String, IComplexType> params;
                    try {
                        command.setContext(entity);
                        params = command.getParameters();
                        if (params != null) {
                            Logger.getLogger().debug("Perform command [{0}]. Context: {1}", command.getName(), entity);
                            command.execute(entity, params);
                            updateStatus();
                        }
                    } finally {
                        command.setContext(prevContext);
                    }
                });
            }
        });

        if (status != Status.LOST_ENTITY) {
            Entity entity = (Entity) shortcut.model.getValue("entity");
            entity.addNodeListener(this);
            entity.model.addModelListener(this);
        }

        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                e.setSource(LaunchShortcut.this);
                LaunchShortcut.this.processMouseEvent(e);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                e.setSource(LaunchShortcut.this);
                LaunchShortcut.this.processMouseEvent(e);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (shortcut.model.remove()) {
                    Container panel = LaunchShortcut.this.getParent();
                    panel.remove(LaunchShortcut.this);
                }
            }
        });
    }
    
    Shortcut getShortcut() {
        return shortcut;
    }

    @Override
    public Dimension getMinimumSize() {
        return super.getPreferredSize();
    }
    
    @Override
    public void setEnabled(boolean enabled)  {
        super.setEnabled(enabled);
        setOpacity(enabled ? 1 : 0.7f);
        setDisabledIcon(ImageUtils.combine(
                ImageUtils.grayscale((ImageIcon) getIcon()), 
                MOVE)
        );
        setBorder(new RoundedBorder(new DashBorder(Color.RED, 5, 1), 18));
        stateChanged();
    }
    
    protected void updateStatus() {
        Status newStatus = status;
        Entity entity  = (Entity) shortcut.model.getValue("entity");
        String cmdName = (String) shortcut.model.getValue("command");
        
        boolean cmdExist = entity.getCommands().stream().anyMatch((command) -> {
            return command.getName().equals(cmdName);
        });
        if (entity.model.getID() == null) {
            newStatus = Status.LOST_ENTITY;
        } else if (!cmdExist) {
            newStatus = Status.LOST_COMMAND;
        } else if (entity.islocked()) {
            newStatus = Status.LOCKED;
        } else {
            EntityCommand command = entity.getCommand(cmdName);            
            Entity[] prevContext = command.getContext();
            try {
                command.setContext(entity);
                newStatus = command.getButton().isEnabled() ? Status.AVAILABLE : Status.DISABLED;
            } finally {
                command.setContext(prevContext);
            }
        }
        
        if (status != newStatus) {
            Logger.getLogger().debug("LCU: Shortcut ''{0}'' state changed: {1} -> {2}", shortcut, status, newStatus);
            status = newStatus;
        }
        updateView();
    }
    
    protected void updateView() {
        if (isEnabled()) {
            switch (status) {
                case LOST_ENTITY:
                    setIcon(ImageUtils.combine(((Entity) shortcut.model.getValue("entity")).getIcon(), LOST));
                    setBorder(ERROR_BORDER);
                    setForeground(Color.decode("#DE5347"));
                    break;

                case LOST_COMMAND:
                    setIcon(ImageUtils.combine(CMD, LOST));
                    setBorder(ERROR_BORDER);
                    setForeground(Color.decode("#DE5347"));
                    break;

                case LOCKED:
                    setIcon(ImageUtils.combine(getCommandIcon(), LOCK));
                    setBorder(NORMAL_BORDER);
                    setForeground(Color.GRAY);
                    break;

                case DISABLED: 
                    setIcon(ImageUtils.grayscale(getCommandIcon()));
                    setBorder(NORMAL_BORDER);
                    setForeground(Color.GRAY);
                    break;

                case AVAILABLE:
                    setIcon(getCommandIcon());
                    setBorder(NORMAL_BORDER);
                    setForeground(Color.BLACK);
                    break;
            }
            repaint();
        }
    }
    
    private ImageIcon getCommandIcon() {
        Entity entity = (Entity) shortcut.model.getValue("entity");
        EntityCommand command = entity.getCommand((String) shortcut.model.getValue("command"));
        Entity[] prevContext = command.getContext();
        try {
            command.setContext(entity);
            return (ImageIcon) command.getButton().getIcon();
        } finally {
            command.setContext(prevContext);
        }
    }
    
    @Override
    protected void stateChanged() {
        if (isEnabled()) {
            if (status == Status.AVAILABLE) {
                super.stateChanged();
            } else if (status == Status.LOCKED) {
                setBorder(getModel().isRollover() ? new RoundedBorder(new LineBorder(Color.GRAY, 1), 18) : NORMAL_BORDER);
            }
            controls.setBackground(
                    status == Status.LOST_ENTITY || status == Status.LOST_COMMAND ? getForeground() : Color.decode("#3399FF")
            );
        }
        controls.setVisible(getModel().isRollover() && isEnabled());
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        updateStatus();
    }

    @Override
    public void modelDeleted(EntityModel model) {
        updateStatus();
    }

    @Override
    public void childChanged(INode node) {
        updateStatus();
    }
    
    @Override
    public final void paint(Graphics g) {
        super.paint(g);
        if (status == Status.LOST_ENTITY || status == Status.LOST_COMMAND) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
            int tileWidth = TILE.getIconWidth();
            int tileHeight = TILE.getIconHeight();
            Insets ins = getInsets();
            for (int y = 2; y < getHeight()-4; y += tileHeight) {
                for (int x = 2; x < getWidth()-4; x += tileWidth) {
                    g2d.drawImage(TILE.getImage(), x, y, this);
                }
            }
            g2d.dispose();
        }
    }
    
    class TopEnd extends Path2D.Float {

        TopEnd(float width, float height, float radius) {
            moveTo(0, height);
            lineTo(0, radius/2);
            curveTo(0, radius/2 , 0, 0, radius/2, 0);
            lineTo(width - radius/2, 0);
            curveTo(width - radius/2, 0, width, 0, width, radius/2);
            lineTo(width, height);
            
            closePath();
        }
        
    }

}
