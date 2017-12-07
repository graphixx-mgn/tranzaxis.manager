package codex.launcher;

import codex.command.EntityCommand;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;

/**
 * Ярлык запуска команды с панели быстрого доступа {@link LauncherUnit}.
 */
final class CommandLauncher extends LaunchButton implements IModelListener {
    
    public enum Status {
        ACTIVE, INACTIVE, INCORRECT;
    }
    
    private final static ImageIcon WARN = ImageUtils.getByPath("/images/warn.png");
    private final static ImageIcon TILE = ImageUtils.getByPath("/images/strips_red.png");
    
    private EntityCommand   command;
    private Entity          entity;
    private final ImageIcon icon;
    private final String    title;
    
    private final JLabel    signDelete;
    private       Status    status;
    
    /**
     * Конструктор ярлыка.
     * @param entity Ссылка на сущность.
     * @param command Ссылка на команду, доступную для класса сущности.
     */
    CommandLauncher(Entity entity, EntityCommand command, String title) {
        super(title, null);
        this.command = command;
        this.entity  = entity;
        this.title   = title;
        this.icon    = command == null ? ImageUtils.getByPath("/images/warn.png") : command.getIcon();
        setLayout(null);
        
        signDelete = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/clearval.png"), 18, 18));
        signDelete.setVisible(false);
        add(signDelete);
        
        Insets insets = signDelete.getInsets();
        Dimension size = signDelete.getPreferredSize();
        signDelete.setBounds(
                getPreferredSize().width - size.width - 3, 
                insets.top + 3,
                size.width, 
                size.height
        );
        updateStatus();
        
        addActionListener((event) -> {
            if (status == Status.ACTIVE) {
                Map<String, IComplexType> params = command.getParameters();
                if (params != null) {
                    Logger.getLogger().debug("Perform command [{0}]. Context: {1}", command.getName(), entity);
                    command.execute(entity, params);
                }
            }
        });
        if (entity != null) {
            entity.model.addModelListener(this);
        }
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                getModel().setRollover(true);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                Container panel = CommandLauncher.this.getParent();
                panel.remove(CommandLauncher.this);
                panel.revalidate();
                panel.repaint();
                
                Shortcut shortcut = new Shortcut(title);
                shortcut.model.remove();
            }
        });
    }
    
    private void updateStatus() {
        Status newStatus;
        if (command == null) {
            newStatus = Status.INCORRECT;
        } else {
            Entity[] prevContext = command.getContext();
            try {
                command.setContext(entity);
                newStatus = command.getButton().isEnabled() ? Status.ACTIVE : Status.INACTIVE;
            } finally {
                command.setContext(prevContext);
            }
        }
        if (status != newStatus) {
            Logger.getLogger().debug("Shortcut ''{0}'' state changed: {1} -> {2}", title, status, newStatus);
            status = newStatus;
            switch (status) {
                case ACTIVE:
                    setIcon(icon);
                    setBorder(NORMAL_BORDER);
                    setForeground(Color.BLACK);
                    break;
                case INACTIVE:
                    setIcon(ImageUtils.grayscale(icon));
                    setForeground(Color.GRAY);
                    break;
                case INCORRECT:
                    setIcon(WARN);
                    setBorder(ERROR_BORDER);
                    setForeground(Color.decode("#DE5347"));
            }
            repaint();
        }
    }
    
    @Override
    public final void stateChanged(ChangeEvent event) {
        if (status == Status.ACTIVE) {
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
        CommandLauncher.this.entity  = null;
        CommandLauncher.this.command = null;
        updateStatus();
    }
    
    @Override
    public final void paint(Graphics g) {
        super.paint(g);
        if (status == Status.INCORRECT) {
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
