package manager.nodes;

import codex.command.EditorCommand;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.mask.DataSetMask;
import codex.mask.IArrMask;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
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
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import manager.commands.environment.RunAll;
import manager.commands.environment.RunExplorer;
import manager.commands.environment.RunServer;

public class Environment extends Entity implements INodeListener {
    
    public final static String PROP_JVM_SERVER   = "jvmServer";
    public final static String PROP_JVM_EXPLORER = "jvmExplorer";
    public final static String PROP_LAYER_URI    = "layerURI";
    public final static String PROP_DATABASE     = "database";
    public final static String PROP_VERSION      = "version";
    public final static String PROP_INSTANCE_ID  = "instanceId";
    public final static String PROP_REPOSITORY   = "repository";
    public final static String PROP_BINARIES     = "binaries";
    public final static String PROP_OFFSHOOT     = "offshoot";
    public final static String PROP_RELEASE      = "release";
    public final static String PROP_USER_NOTE    = "userNote";
    
    public final static String PROP_AUTO_RELEASE = "autoRelease";
    
    private final static IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);

    private final IDataSupplier<String> layerSupplier = new RowSelector(
            RowSelector.Mode.Value, () -> {
                return getDataBase(true).getConnectionID(true);
            }, 
            "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
    ) {
        @Override
        public boolean isReady() {
            return getDataBase(true) != null;
        } 
    };

    private final IDataSupplier<String> versionSupplier = new IDataSupplier<String>() {
        @Override
        public String call() throws Exception {
            Database database = getDataBase(true);
            String   layerUri = getLayerUri(true);
            if (IComplexType.notNull(database, layerUri)) {
                try (ResultSet rs = DAS.select(database.getConnectionID(false), "SELECT VERSION FROM RDX_DDSVERSION WHERE LAYERURI = ?", layerUri);) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
            return null;
        }
    };
    
    private final IDataSupplier<String> instanceSupplier = new RowSelector(
            RowSelector.Mode.Row, () -> {
                return getDataBase(true).getConnectionID(true);
            }, 
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
    ) {
        @Override
        public boolean isReady() {
            return getDataBase(true) != null;
        } 
    };
    
    private final EditorCommand layerSelector = new DataSetMask(null, layerSupplier);
    private final IArrMask instanceSelector = new DataSetMask("{0} - {1}", instanceSupplier);
    
    public Environment(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/instance.png"), title, null);
        
        // Properties
        model.addUserProp(PROP_JVM_SERVER,   new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp(PROP_JVM_EXPLORER, new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp(PROP_LAYER_URI,    new Str(null),                   true,  Access.Select);
        model.addUserProp(PROP_DATABASE,     new EntityRef(Database.class),   false, null);
        model.addDynamicProp(PROP_VERSION,   new Str(null), Access.Select, () -> {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    setVersion(getLayerVersion());
                }
            });
            thread.start();
            return getVersion();
        });
        model.addUserProp(PROP_INSTANCE_ID,  new ArrStr().setMask(instanceSelector), false, Access.Select);
        model.addUserProp(PROP_REPOSITORY,   new EntityRef(Repository.class), true, Access.Select);
        model.addUserProp(PROP_OFFSHOOT,     new EntityRef(
                Offshoot.class, 
                (entity) -> {
                    Offshoot offshoot = (Offshoot) entity;
                    return 
                        offshoot.getRepository()  == getRepository(true) &&
                        offshoot.getBuiltStatus() != null &&
                        offshoot.isWCLoaded();
                },
                (entity) -> {
                    String layerVersion = getVersion();
                    if (layerVersion == null || layerVersion.isEmpty())
                        return EntityRef.Match.Unknown;

                    String offshootVersion = ((Offshoot) entity).getVersion();
                    layerVersion = layerVersion.substring(0, layerVersion.lastIndexOf("."));
                    return offshootVersion.equals(layerVersion) ? EntityRef.Match.Exact : EntityRef.Match.None;
                }
        ), true, Access.Select);
        model.addUserProp(PROP_RELEASE,      new EntityRef(
                Release.class, 
                (entity) -> {
                    return ((Release) entity).getRepository()  == getRepository(true);
                },
                (entity) -> {
                    String layerVersion = getVersion();
                    if (layerVersion == null || layerVersion.isEmpty())
                        return EntityRef.Match.Unknown;

                    String releaseVersion = ((Release) entity).getVersion();
                    return releaseVersion.equals(layerVersion) ? EntityRef.Match.Exact : (
                                releaseVersion.substring(0, releaseVersion.lastIndexOf(".")).equals(
                                        layerVersion.substring(0, layerVersion.lastIndexOf("."))
                                ) ? EntityRef.Match.About : EntityRef.Match.None
                           );
                }
        ), true, Access.Select);
        
        model.addDynamicProp(PROP_BINARIES,  new EntityRef(BinarySource.class), Access.Edit, () -> {
            return IComplexType.coalesce(getOffshoot(true), getRelease(true));
        }, PROP_OFFSHOOT, PROP_RELEASE, PROP_REPOSITORY);
        model.addUserProp(PROP_USER_NOTE,    new Str(null), false, null);
        
        model.addUserProp(PROP_AUTO_RELEASE, new Bool(false), false, Access.Any);
        
        // Property settings
        model.getProperty(PROP_RELEASE).setRequired(getOffshoot(true) == null);
        model.getProperty(PROP_OFFSHOOT).setRequired(getRelease(true) == null);
        if (getRelease(false) != null) {
            getRelease(false).addNodeListener(this);
        }
        
        // Editor settings
        model.addPropertyGroup(Language.get("group@database"), PROP_DATABASE, PROP_INSTANCE_ID, PROP_VERSION);
        model.addPropertyGroup(Language.get("group@binaries"), PROP_REPOSITORY, PROP_OFFSHOOT, PROP_RELEASE);
        model.getEditor(PROP_LAYER_URI).addCommand(layerSelector);
        model.getEditor(PROP_RELEASE).setVisible(getRepository(true)     != null);
        model.getEditor(PROP_OFFSHOOT).setVisible(getRepository(true)    != null);
        model.getEditor(PROP_INSTANCE_ID).setVisible(getRepository(true) != null);
        model.getEditor(PROP_VERSION).setVisible(getRepository(true)     != null);
        
        SynkRelease synkRelease = new SynkRelease();
        model.addModelListener(synkRelease);
        ((AbstractEditor) model.getEditor(PROP_RELEASE)).addCommand(synkRelease);
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case PROP_DATABASE:
                    layerSelector.activate();
                    model.updateDynamicProps(PROP_VERSION);
                    model.getEditor(PROP_INSTANCE_ID).setVisible(newValue != null);
                    model.getEditor(PROP_VERSION).setVisible(newValue     != null);
                    setInstanceId(null);
                    break;
                    
                case PROP_LAYER_URI:
                    model.updateDynamicProps(PROP_VERSION);
                    break;
                    
                case PROP_VERSION:
                    ((AbstractEditor) model.getEditor(PROP_RELEASE)).updateUI();
                    ((AbstractEditor) model.getEditor(PROP_OFFSHOOT)).updateUI();
                    synkRelease.activate();
                    break;
                    
                case PROP_REPOSITORY:
                    setOffshoot(null);
                    setRelease(null);
                    model.getEditor(PROP_RELEASE).setVisible(newValue  != null);
                    model.getEditor(PROP_OFFSHOOT).setVisible(newValue != null);
                    synkRelease.activate();
                    break;
                    
                case PROP_RELEASE:
                    Release oldRelease = (Release) oldValue;
                    Release newRelease = (Release) newValue;
                    if (oldValue != null) {
                        oldRelease.removeNodeListener(this);
                        if (oldRelease.islocked()) {
                            getLock().release();
                        }
                    }
                    if (newValue != null) {
                        setOffshoot(null);
                        newRelease.addNodeListener(this);
                        if (newRelease.islocked()) {
                            try {
                                getLock().acquire();
                            } catch (InterruptedException e) {}
                        }
                    }
                    model.getProperty(PROP_OFFSHOOT).setRequired(newValue == null);
                    break;
                    
                case PROP_OFFSHOOT:
                    if (newValue != null) {
                        setRelease(null);
                        setAutoRelease(false);
                        synkRelease.activate();
                    }
                    model.getProperty(PROP_RELEASE).setRequired(newValue == null);
                    break;
            }
        });
        
        // Commands
        addCommand(new RunAll() {
            @Override
            public void execute(Environment environment, Map<String, IComplexType> map) {
                setVersion(getLayerVersion());
                super.execute(environment, map);
            }
        });
        addCommand(new RunServer() {
            @Override
            public void execute(Environment environment, Map<String, IComplexType> map) {
                setVersion(getLayerVersion());
                super.execute(environment, map);
            }
        });
        addCommand(new RunExplorer() {
            @Override
            public void execute(Environment environment, Map<String, IComplexType> map) {
                setVersion(getLayerVersion());
                super.execute(environment, map);
            }
        });
    }
    
    public final List<String> getJvmServer() {
        return (List<String>) model.getValue(PROP_JVM_SERVER);
    }
    
    public final List<String> getJvmExplorer() {
        return (List<String>) model.getValue(PROP_JVM_EXPLORER);
    }
    
    public String getLayerUri(boolean unsaved) {
        return (String) (unsaved ? model.getUnsavedValue(PROP_LAYER_URI) : model.getValue(PROP_LAYER_URI));
    }
    
    public final Database getDataBase(boolean unsaved) {
        return (Database) (unsaved ? model.getUnsavedValue(PROP_DATABASE) : model.getValue(PROP_DATABASE));
    }
    
    public String getVersion() {
        return (String) model.getValue(PROP_VERSION);
    }
    
    public Integer getInstanceId() {
        List<String> value =  (List<String>) model.getValue(PROP_INSTANCE_ID);
        if (value != null && value.size() > 0) {
            return Integer.valueOf(value.get(0));
        }
        return null;
    }
    
    public final Repository getRepository(boolean unsaved) {
        return (Repository) (unsaved ? model.getUnsavedValue(PROP_REPOSITORY) : model.getValue(PROP_REPOSITORY));
    }
    
    public final BinarySource getBinaries() {
        return (BinarySource) model.getValue(PROP_BINARIES);
    }
    
    public final Offshoot getOffshoot(boolean unsaved) {
        return (Offshoot) (unsaved ? model.getUnsavedValue(PROP_OFFSHOOT) : model.getValue(PROP_OFFSHOOT));
    }
    
    public final Release getRelease(boolean unsaved) {
        return (Release) (unsaved ? model.getUnsavedValue(PROP_RELEASE) : model.getValue(PROP_RELEASE));
    }
    
    public String getUserNote() {
        return (String) model.getValue(PROP_USER_NOTE);
    }
    
    public boolean getAutoRelease(boolean unsaved) {
        return (unsaved ? model.getUnsavedValue(PROP_AUTO_RELEASE) : model.getValue(PROP_AUTO_RELEASE)) == Boolean.TRUE;
    }
    
    public final void setJvmServer(List<String> value) {
        model.setValue(PROP_JVM_SERVER, value);
    }
    
    public final void setJvmExplorer(List<String> value) {
        model.setValue(PROP_JVM_EXPLORER, value);
    }
    
    public final void setLayerUri(String value) {
        model.setValue(PROP_LAYER_URI, value);
    }
    
    public final void setDatabase(Database value) {
        model.setValue(PROP_DATABASE, value);
    }
    
    public final void setVersion(String value) {
        model.setValue(PROP_VERSION, value);
    }
    
    public final void setInstanceId(Integer value) {
        if (value == null) {
            model.setValue(PROP_INSTANCE_ID, value);
        } else {
            model.setValue(
                    PROP_INSTANCE_ID, 
                    new LinkedList() {{
                        add(value.toString());
                        add("<?>");
                    }}
            );
        }
    }
    
    public final void setRepository(Repository value) {
        model.setValue(PROP_REPOSITORY, value);
    }
    
    public final void setOffshoot(Offshoot value) {
        model.setValue(PROP_OFFSHOOT, value);
    }
    
    public final void setRelease(Release value) {
        model.setValue(PROP_RELEASE, value);
    }
    
    public final void setUserNote(String value) {
        model.setValue(PROP_USER_NOTE, value);
    }
    
    public final void setAutoRelease(Boolean value) {
        model.setValue(PROP_AUTO_RELEASE, value);
    }
    
    private String getLayerVersion() {
        try {
            return versionSupplier.call();
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean canStartServer() {
        return IComplexType.notNull(
                getBinaries(),
                getLayerUri(false),
                getInstanceId()
        );
    }
    
    public boolean canStartExplorer() {
        return IComplexType.notNull(
                getBinaries(),
                getLayerUri(false)
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
    
    private final static ImageIcon UNKNOWN   = ImageUtils.resize(ImageUtils.getByPath("/images/unavailable.png"), 20, 20);
    private final static ImageIcon CHECKED   = ImageUtils.resize(ImageUtils.getByPath("/images/update.png"), 20, 20);
    private final static ImageIcon UNCHECKED = ImageUtils.resize(ImageUtils.combine(CHECKED, UNKNOWN), 20, 20);
    
    private class SynkRelease extends EditorCommand implements IModelListener {

        public SynkRelease() {
            super(
                UNKNOWN,
                Language.get(Environment.class.getSimpleName(), "release.command@synk"),
                null
            );
            activator = (holders) -> {
                String  foundVersion = Environment.this.getVersion();
                Release usedRelease  = Environment.this.getRelease(false);
                String  usedVersion  = usedRelease == null ? null : usedRelease.getVersion();
                boolean autoRelease  = Environment.this.getAutoRelease(true);
                
                button.setEnabled(foundVersion != null);
                button.setIcon(autoRelease ? CHECKED : UNCHECKED);
                
                AbstractEditor releaseEditor = (AbstractEditor) model.getEditor(PROP_RELEASE);
                releaseEditor.setEditable(!autoRelease || foundVersion == null);

                if (foundVersion != null && autoRelease && !foundVersion.equals(usedVersion)) {
                    Entity newValue = findEntity(foundVersion);
                    if (newValue != null) {
                        Environment.this.setRelease((Release) newValue);
                        if (Environment.this.getID() != null) {
                            try {
                                Environment.this.model.commit(true);
                            } catch (Exception e) {}
                        }
                        Logger.getLogger().info(
                                "Environment ''{0}'' release has been updated automatically to ''{1}''",
                                Environment.this.getPID(), newValue
                        );
                        releaseEditor.updateUI();
                    }
                }
            };
        }

        @Override
        public void execute(PropertyHolder context) {
            Environment.this.setAutoRelease(!getAutoRelease(true));
            if (Environment.this.getID() != null) {
                try {
                    Environment.this.model.commit(true);
                } catch (Exception e) {}
            }
            activate();
        }
        
        private Entity findEntity(String version) {
            Entity repository  = (Entity) Environment.this.getRepository(true);
            if (repository != null) {
                return Entity.newInstance(Release.class, repository.toRef(), version);
            }
            return null;
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public void modelRestored(EntityModel model, List<String> changes) {
            if (changes.contains(PROP_AUTO_RELEASE)) {
                activate();
            }
        }
        
    }
    
}