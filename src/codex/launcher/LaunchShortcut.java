package codex.launcher;

import codex.command.EntityCommand;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;

/**
 * Ярлык запуска команды с панели быстрого доступа {@link LauncherUnit}.
 */
final class LaunchShortcut extends LaunchButton implements IModelListener, INodeListener {
    
    private final static ImageIcon TILE = ImageUtils.getByPath("/images/strips_red.png");
    private final static ImageIcon LOST = ImageUtils.resize(ImageUtils.getByPath("/images/close.png"), 20, 20);
    private final static ImageIcon LOCK = ImageUtils.resize(ImageUtils.getByPath("/images/lock.png"),  28, 28);
    private final static ImageIcon CMD  = ImageUtils.getByPath("/images/command.png");
    
    public enum Status {
        UNKNOWN, 
        AVAILABLE, 
        DISABLED,
        LOCKED,
        LOST_COMMAND,
        LOST_ENTITY;
    }
    
    private final JLabel   signDelete;
    private final Shortcut shortcut;
    private       Status   status = Status.UNKNOWN;
    
    /**
     * Конструктор ярлыка.
     * @param entity Ссылка на сущность.
     * @param command Ссылка на команду, доступную для класса сущности.
     */
    LaunchShortcut(Shortcut shortcut) {
        super(shortcut.model.getPID(), null);
        this.shortcut = shortcut;
        setLayout(null);
        updateStatus();
        
        signDelete = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/clearval.png"), 18, 18));
        signDelete.setVisible(false);
        add(signDelete);
        signDelete.setBounds(
                getPreferredSize().width - signDelete.getPreferredSize().width - 3, 
                signDelete.getInsets().top + 3,
                signDelete.getPreferredSize().width, 
                signDelete.getPreferredSize().height
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
                getModel().setRollover(true);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (shortcut.model.remove()) {
                    Container panel = LaunchShortcut.this.getParent();
                    panel.remove(LaunchShortcut.this);
                    panel.revalidate();
                    panel.repaint();
                }
            }
        });
    }
    
    private void updateStatus() {
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
    
    private void updateView() {        
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
    public final void stateChanged(ChangeEvent event) {
        if (status == Status.AVAILABLE) {
            super.stateChanged(event);
        }
        signDelete.setVisible(getModel().isRollover());
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

}
