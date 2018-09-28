package manager.nodes;

import codex.command.EditorCommand;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.editor.AbstractEditor;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.mask.DataSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import manager.commands.RunAll;
import manager.commands.RunExplorer;
import manager.commands.RunServer;
import manager.type.WCStatus;

public class Environment extends Entity implements INodeListener {
    
    private final static IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private final IDataSupplier<String> layerSupplier = new RowSelector(
            RowSelector.Mode.Value, () -> {
                return ((Database) model.getUnsavedValue("database")).getConnectionID(true);
            }, 
            "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
    ) {
        @Override
        public boolean isReady() {
            return model.getUnsavedValue("database") != null;
        } 
    };

    private final IDataSupplier<String> versionSupplier = new IDataSupplier<String>() {
        @Override
        public String call() throws Exception {
            Database database = (Database) model.getUnsavedValue("database");
            String   layerUri = (String) model.getUnsavedValue("layerURI");
            if (IComplexType.notNull(database, layerUri)) {
                ResultSet rs = DAS.select(database.getConnectionID(false), "SELECT VERSION FROM RDX_DDSVERSION WHERE LAYERURI = ?", layerUri);
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
            return null;
        }
    };
    
    private final IDataSupplier<String> instanceSupplier = new RowSelector(
            RowSelector.Mode.Row, () -> {
                return ((Database) model.getUnsavedValue("database")).getConnectionID(true);
            }, 
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
    );
    
    private final EditorCommand layerSelector = new DataSetMask(null, layerSupplier);
    
    public Environment(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/instance.png"), title, null);

        MutablePropHolder offshoot = new MutablePropHolder("offshoot", new EntityRef(
                Offshoot.class, 
                (entity) -> {
                    return
                            entity.getParent().getParent().equals(model.getUnsavedValue("repository")) && 
                            entity.model.getValue("built") != null && 
                            ((Offshoot) entity).getStatus() == WCStatus.Succesfull;
                },
                (entity) -> {
                    if (model.getUnsavedValue("layerURI") == null) 
                        return EntityRef.Match.Unknown;

                    String version = (String) model.getUnsavedValue("version");
                    if (version == null || version.isEmpty())
                        return EntityRef.Match.Unknown;

                    String entityVersion = entity.model.getPID();
                    return entityVersion.equals(version.substring(0, version.lastIndexOf("."))) ? EntityRef.Match.Exact : EntityRef.Match.None;
                }
        ), true);
        
        MutablePropHolder release = new MutablePropHolder("release", new EntityRef(
                Release.class, 
                (entity) -> {
                    return entity.getParent().getParent().equals(model.getUnsavedValue("repository"));
                },
                (entity) -> {
                    String version = (String) model.getUnsavedValue("version");
                    if (version == null || version.isEmpty())
                        return EntityRef.Match.Unknown;

                    String entityVersion = entity.model.getPID();
                    return entityVersion.equals(version) ? EntityRef.Match.Exact : (
                                entityVersion.substring(0, entityVersion.lastIndexOf(".")).equals(
                                        version.substring(0, version.lastIndexOf("."))
                                ) ? EntityRef.Match.About : EntityRef.Match.None
                           );
                }
        ), true);
        
        // Properties
        model.addUserProp("jvmServer",   new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp("jvmExplorer", new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp("layerURI",    new Str(null),                   true,  Access.Select);
        
        model.addUserProp("database",    new EntityRef(Database.class),   false, null);
        model.addDynamicProp("version",  new Str(null), Access.Select, () -> {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    updateVersion();
                }
            });
            thread.start();
            return model.getValue("version");
        });
        model.addUserProp("instanceId", new ArrStr().setMask(new DataSetMask(
                "{0} - {1}", instanceSupplier
        )), false, Access.Select);
        
        model.addUserProp("repository",  new EntityRef(Repository.class), true,  Access.Select);
        model.addUserProp(offshoot,      Access.Select);
        model.addUserProp(release,       Access.Select);
        model.addUserProp("autoRelease", new Bool(false), false, Access.Any);
        model.addDynamicProp("binaries", new EntityRef(BinarySource.class), Access.Edit, () -> {
            return IComplexType.coalesce(model.getValue("offshoot"), model.getValue("release"));
        }, "offshoot", "release", "repository");
        model.addUserProp("userNote",    new Str(null), false, null);
        
        // Property settings
        release.setMandatory(model.getValue("offshoot") == null);
        offshoot.setMandatory(model.getValue("release") == null);
        if (model.getValue("release") != null) {
            ((Entity) model.getValue("release")).addNodeListener(this);
        }
        
        // Editor settings
        model.addPropertyGroup(Language.get("group@database"), "database", "instanceId", "version");
        model.addPropertyGroup(Language.get("group@binaries"), "repository", "offshoot", "release");
        model.getEditor("layerURI").addCommand(layerSelector);
        
        EditorCommand synkRelease = new SynkRelease();
        ((AbstractEditor) model.getEditor("release")).addCommand(synkRelease);
        model.getEditor("release").setVisible(model.getValue("repository")  != null);
        model.getEditor("offshoot").setVisible(model.getValue("repository") != null);
        model.getEditor("instanceId").setVisible(model.getValue("database") != null);
        model.getEditor("version").setVisible(model.getValue("database")    != null);
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case "database":
                    layerSelector.activate();
                    SwingUtilities.invokeLater(() -> {
                        model.updateDynamicProp("version");
                    });
                    model.setValue("instanceId", null);
                    model.getEditor("instanceId").setVisible(newValue != null);
                    model.getEditor("version").setVisible(newValue    != null);
                    break;
                    
                case "layerURI":
                    SwingUtilities.invokeLater(() -> {
                        model.updateDynamicProp("version");
                    });
                    break;
                    
                case "version":
                    ((AbstractEditor) model.getEditor("release")).updateUI();
                    ((AbstractEditor) model.getEditor("offshoot")).updateUI();
                    synkRelease.activate();
                    break;
                    
                case "repository":
                    model.setValue("release",  null);
                    model.setValue("offshoot", null);
                    model.getEditor("release").setVisible(newValue  != null);
                    model.getEditor("offshoot").setVisible(newValue != null);
                    break;
                    
                case "release":
                    Release oldRelease = (Release) oldValue;
                    Release newRelease = (Release) newValue;
                    if (oldValue != null) {
                        oldRelease.removeNodeListener(this);
                        if (oldRelease.islocked()) {
                            getLock().release();
                        }
                    }
                    if (newValue != null) {
                        model.setValue("offshoot", null);
                        newRelease.addNodeListener(this);
                        if (newRelease.islocked()) {
                            try {
                                getLock().acquire();
                            } catch (InterruptedException e) {}
                        }
                    }
                    offshoot.setMandatory(newValue == null);
                    break;
                    
                case "offshoot":
                    if (newValue != null) {
                        model.setValue("autoRelease", false);
                        model.setValue("release", null);
                        synkRelease.activate();
                    }
                    release.setMandatory(newValue == null);
                    break;
            }
        });
        
        // Commands
        addCommand(new RunAll() {
            @Override
            public void execute(Entity entity, Map<String, IComplexType> map) {
                updateVersion();
                super.execute(entity, map);
            }
        });
        addCommand(new RunServer() {
            @Override
            public void execute(Entity entity, Map<String, IComplexType> map) {
                updateVersion();
                super.execute(entity, map);
            }
        });
        addCommand(new RunExplorer() {
            @Override
            public void execute(Entity entity, Map<String, IComplexType> map) {
                updateVersion();
                super.execute(entity, map);
            }
        });
    }
    
    private final void updateVersion() {
        try {
            model.setValue("version", versionSupplier.call());
        } catch (Exception e) {}
    }
    
    public boolean canStartServer() {
        return IComplexType.notNull(
                model.getValue("binaries"),
                model.getValue("layerURI"),
                model.getValue("instanceId")
        );
    }
    
    public boolean canStartExplorer() {
        return IComplexType.notNull(
                model.getValue("binaries"),
                model.getValue("layerURI")
        );
    }

    @Override
    public void childChanged(INode node) {
        if (node instanceof Release) {
            try {
                if (((Release) node).islocked()) {
                    getLock().acquire();
                } else {
                    getLock().release();
                }
            } catch (InterruptedException e) {}
        }
    }
    
    private class MutablePropHolder extends PropertyHolder {
    
        public MutablePropHolder(String name, IComplexType value, boolean require) {
            super(name, value, require);
        }

        public MutablePropHolder(String name, String title, String desc, IComplexType value, boolean require) {
            super(name, title, desc, value, require);
        }
        
        public void setMandatory(boolean mandatory) {
            try {
                Field require = PropertyHolder.class.getDeclaredField("require");
                require.setAccessible(true);
                require.set(this, mandatory);
                ((AbstractEditor) model.getEditor(getName())).updateUI();
            } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    
    }
    
    private final static ImageIcon UNKNOWN   = ImageUtils.resize(ImageUtils.getByPath("/images/unavailable.png"), 20, 20);
    private final static ImageIcon CHECKED   = ImageUtils.resize(ImageUtils.getByPath("/images/update.png"), 20, 20);
    private final static ImageIcon UNCHECKED = ImageUtils.resize(ImageUtils.combine(CHECKED, UNKNOWN), 20, 20);
    private class SynkRelease extends EditorCommand {

        public SynkRelease() {
            super(
                UNKNOWN,
                Language.get(Environment.class.getSimpleName(), "release.command@synk"),
                null
            );
            activator = (holders) -> {
                String foundVersion = (String) Environment.this.model.getValue("version");
                Entity usedRelease  = (Entity) Environment.this.model.getValue("release");
                String usedVersion  = usedRelease == null ? null : usedRelease.model.getPID();
                boolean autoRelease = Environment.this.model.getUnsavedValue("autoRelease") == Boolean.TRUE;
                
                button.setEnabled(foundVersion != null);
                button.setIcon(autoRelease ? CHECKED : UNCHECKED);
                
                AbstractEditor releaseEditor = (AbstractEditor) model.getEditor("release");
                releaseEditor.setEditable(!autoRelease || foundVersion == null);
                
                if (foundVersion != null && autoRelease && !foundVersion.equals(usedVersion)) {
                    Entity newValue = findEntity(foundVersion);
                    if (newValue != null) {
                        Environment.this.model.setValue("release", newValue);
                        if (Environment.this.model.getID() != null) {
                            Environment.this.model.commit();
                        }
                        Logger.getLogger().info(
                                "Environment ''{0}'' release has been updated automatically to ''{1}''",
                                Environment.this.model.getPID(), newValue
                        );
                        releaseEditor.updateUI();
                    }
                }
            };
        }

        @Override
        public void execute(PropertyHolder context) {
            Boolean autoRelease = Environment.this.model.getUnsavedValue("autoRelease") == Boolean.TRUE;
            Environment.this.model.setValue("autoRelease", !autoRelease);
            if (Environment.this.model.getID() != null) {
                Environment.this.model.commit();
            }
            activate();
        }
        
        private Entity findEntity(String version) {
            Entity repo  = (Entity) Environment.this.model.getUnsavedValue("repository");
            Entity found = EAS.getEntitiesByClass(Release.class).stream()
                .filter((entity) -> {
                    return entity.getParent().getParent() == repo && entity.model.getPID().equals(version);
                })
                .findFirst().orElse(null);
            if (found == null) {
                EntityRef repoRef = new EntityRef(Repository.class);
                repoRef.valueOf(repo.model.getID().toString());
                found = Entity.newInstance(
                        Release.class, 
                        repoRef, 
                        version
                );
            }
            return found;
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }
        
    }
    
}