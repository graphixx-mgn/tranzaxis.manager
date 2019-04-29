package manager.nodes;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.editor.AbstractEditor;
import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.mask.DataSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import manager.commands.environment.RunAll;
import manager.commands.environment.RunExplorer;
import manager.commands.environment.RunServer;
import manager.type.SourceType;

public class Environment extends Entity implements INodeListener {

    // General properties
    public final static String PROP_JVM_SERVER   = "jvmServer";
    public final static String PROP_JVM_EXPLORER = "jvmExplorer";
    public final static String PROP_LAYER_URI    = "layerURI";
    public final static String PROP_DATABASE     = "database";
    public final static String PROP_VERSION      = "version";
    public final static String PROP_INSTANCE_ID  = "instanceId";
    public final static String PROP_REPOSITORY   = "repository";
    public final static String PROP_SOURCE_TYPE  = "srcType";
    public final static String PROP_BINARIES     = "binaries";
    public final static String PROP_OFFSHOOT     = "offshoot";
    public final static String PROP_RELEASE      = "release";
    public final static String PROP_USER_NOTE    = "userNote";
    public final static String PROP_AUTO_RELEASE = "autoRelease";

    // Extra properties
    public final static String PROP_STARTER_OPTS  = "starterOpts";
    public final static String PROP_SERVER_OPTS   = "serverOpts";
    public final static String PROP_EXPLORER_OPTS = "explorerOpts";

    private final IDataSupplier<String> layerSupplier = new RowSelector(
            RowSelector.Mode.Value,
            () -> getDataBase(true).getConnectionID(true),
            "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
    ) {
        @Override
        public boolean isReady() {
            return getDataBase(true) != null;
        } 
    };

    private final IDataSupplier<String> versionSupplier = () -> {
        Database database = getDataBase(true);
        String   layerUri = getLayerUri(true);
        if (IComplexType.notNull(database, layerUri) && ServiceRegistry.getInstance().isServiceRegistered(OracleAccessService.class)) {
            IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
            try (ResultSet rs = DAS.select(database.getConnectionID(false), "SELECT VERSION FROM RDX_DDSVERSION WHERE LAYERURI = ?", layerUri);) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    };
    
    private final IDataSupplier<String> instanceSupplier = new RowSelector(
            RowSelector.Mode.Row,
            () -> getDataBase(true).getConnectionID(true),
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
    ) {
        @Override
        public boolean isReady() {
            return getDataBase(true) != null;
        } 
    };
    
    private final DataSetMask layerSelector    = new DataSetMask(null, layerSupplier);
    private final DataSetMask instanceSelector = new DataSetMask("{0} - {1}", instanceSupplier);

    private final Consumer<String> sourceUpdater = propName -> {
        boolean activate = getRepository(true) != null && getSourceType(true).name().toLowerCase().equals(propName);
        model.getEditor(propName).setVisible(activate);
        model.getProperty(propName).setRequired(activate);
    };
    
    public Environment(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/instance.png"), title, null);
        
        // General properties
        model.addUserProp(PROP_JVM_SERVER,   new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp(PROP_JVM_EXPLORER, new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp(PROP_LAYER_URI,    new Str(null),             true,  Access.Select);
        model.addUserProp(PROP_DATABASE,     new EntityRef(Database.class),   false, null);
        model.addDynamicProp(PROP_VERSION,   new Str(null), Access.Select, () -> {
            new Thread(() -> setVersion(getLayerVersion())).start();
            return getVersion();
        });
        model.addUserProp(PROP_INSTANCE_ID,  new ArrStr().setMask(instanceSelector), false, Access.Select);
        model.addUserProp(PROP_REPOSITORY,   new EntityRef(Repository.class), true, Access.Select);
        model.addUserProp(PROP_SOURCE_TYPE,  new Enum(SourceType.None), true, Access.Select);
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
                (entity) -> ((Release) entity).getRepository()  == getRepository(true),
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
        ) {
            @Override
            public IEditorFactory editorFactory() {
                return propHolder -> new EntityRefEditor(propHolder) {
                    @Override
                    protected List<Object> getValues() {
                        Repository repository = getRepository(true);
                        List<Object> values = new LinkedList<>();
                        if (repository != null) {
                            Entity e = Entity.newInstance(ReleaseList.class, repository.toRef(), "title");
                            values.addAll(e.childrenList());
                        }
                        return values;
                    }
                };
            }
        }, true, Access.Select);
        model.addDynamicProp(
                PROP_BINARIES,
                new EntityRef(BinarySource.class), Access.Edit,
                () -> getSourceType(true).getBinarySource(this),
                PROP_REPOSITORY, PROP_SOURCE_TYPE, PROP_OFFSHOOT, PROP_RELEASE
        );
        model.addUserProp(PROP_USER_NOTE,    new Str(null), false, null);
        model.addUserProp(PROP_AUTO_RELEASE, new Bool(false), false, Access.Any);

        // Extra properties
        model.addExtraProp(PROP_STARTER_OPTS,  new ArrStr(Collections.singletonList("-disableHardlinks")), true);
        model.addExtraProp(PROP_SERVER_OPTS,   new ArrStr(Arrays.asList(
                "-switchEasVerChecksOff",
                "-useLocalJobExecutor",
                "-ignoreDdsWarnings",
                "-development",
                "-autostart"
        )), true);
        model.addExtraProp(PROP_EXPLORER_OPTS, new ArrStr(Arrays.asList(
                "-language=en",
                "-development"
        )), true);
        
        // Property settings
        if (getRelease(false) != null) {
            getRelease(false).addNodeListener(this);
        }
        model.addPropertyGroup(Language.get("group@database"), PROP_DATABASE, PROP_INSTANCE_ID, PROP_VERSION);
        model.addPropertyGroup(Language.get("group@binaries"), PROP_REPOSITORY, PROP_SOURCE_TYPE, PROP_OFFSHOOT, PROP_RELEASE);
        
        // Editor settings
        model.getEditor(PROP_LAYER_URI).addCommand(layerSelector);
        model.getEditor(PROP_SOURCE_TYPE).setVisible(getRepository(true) != null);
        model.getEditor(PROP_INSTANCE_ID).setVisible(getDataBase(true) != null);
        model.getEditor(PROP_VERSION).setVisible(getDataBase(true)     != null);
        
        SyncRelease syncRelease = new SyncRelease();
        model.addModelListener(syncRelease);
        model.getEditor(PROP_RELEASE).addCommand(syncRelease);

        sourceUpdater.accept(PROP_OFFSHOOT);
        sourceUpdater.accept(PROP_RELEASE);
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case PROP_DATABASE:
                    layerSelector.activate();
                    instanceSelector.activate();

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
                    syncRelease.activate();
                    break;
                    
                case PROP_REPOSITORY:
                    setOffshoot(null);
                    setRelease(null);
                    model.getEditor(PROP_SOURCE_TYPE).setVisible(newValue != null);
                    sourceUpdater.accept(PROP_OFFSHOOT);
                    sourceUpdater.accept(PROP_RELEASE);
                    syncRelease.activate();
                    break;

                case PROP_SOURCE_TYPE:
                    sourceUpdater.accept(PROP_OFFSHOOT);
                    sourceUpdater.accept(PROP_RELEASE);
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
                        newRelease.addNodeListener(this);
                        if (newRelease.islocked()) {
                            try {
                                getLock().acquire();
                            } catch (InterruptedException e) {
                                //
                            }
                        }
                    }
                    break;
                    
                /*case PROP_OFFSHOOT:
                    if (newValue != null) {
                        setRelease(null);
                        setAutoRelease(false);
                        synkRelease.activate();
                    }
                    model.getProperty(PROP_RELEASE).setRequired(newValue == null);
                    break;*/
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

    public final SourceType getSourceType(boolean unsaved) {
        return (SourceType) (unsaved ? model.getUnsavedValue(PROP_SOURCE_TYPE) : model.getValue(PROP_SOURCE_TYPE));
    }
    
    public final Offshoot getOffshoot(boolean unsaved) {
        return (Offshoot) (unsaved ? model.getUnsavedValue(PROP_OFFSHOOT) : model.getValue(PROP_OFFSHOOT));
    }

    public final BinarySource getBinaries() {
        return (BinarySource) model.getValue(PROP_BINARIES);
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
            model.setValue(PROP_INSTANCE_ID, null);
        } else {
            model.setValue(
                    PROP_INSTANCE_ID, 
                    new LinkedList<String>() {{
                        add(value.toString());
                        add("<?>");
                    }}
            );
        }
    }
    
    public final void setRepository(Repository value) {
        model.setValue(PROP_REPOSITORY, value);
    }

    public final void setSourceType(SourceType value) {
        model.setValue(PROP_SOURCE_TYPE, value);
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
            } catch (InterruptedException e) {
                //
            }
        }
    }
    
    private final static ImageIcon UNKNOWN   = ImageUtils.resize(ImageUtils.getByPath("/images/unavailable.png"), 20, 20);
    private final static ImageIcon CHECKED   = ImageUtils.resize(ImageUtils.getByPath("/images/update.png"), 20, 20);
    private final static ImageIcon UNCHECKED = ImageUtils.resize(ImageUtils.combine(CHECKED, UNKNOWN), 20, 20);
    
    private class SyncRelease extends EditorCommand implements IModelListener {

        SyncRelease() {
            super(
                UNKNOWN,
                Language.get(Environment.class, "release.command@synk"),
                null
            );
            activator = holder -> {
                String  foundVersion = Environment.this.getVersion();
                Release usedRelease  = Environment.this.getRelease(true);
                String  usedVersion  = usedRelease == null ? null : usedRelease.getVersion();
                boolean autoRelease  = Environment.this.getAutoRelease(true);

                AbstractEditor releaseEditor = (AbstractEditor) model.getEditor(PROP_RELEASE);
                releaseEditor.setEditable(!autoRelease || foundVersion == null);

                if (foundVersion != null && autoRelease && !foundVersion.equals(usedVersion)) {
                    Entity newValue = findEntity(foundVersion);
                    if (newValue != null) {
                        Environment.this.setRelease((Release) newValue);
                        if (model.getChanges().equals(Collections.singletonList(PROP_RELEASE))) {
                            try {
                                Environment.this.model.commit(true);
                            } catch (Exception e) {
                                //
                            }
                        }
                        Logger.getLogger().info(
                                "Environment ''{0}'' release has been updated automatically to ''{1}''",
                                Environment.this.getPID(), newValue
                        );
                        releaseEditor.updateUI();
                    }
                }
                return new CommandStatus(
                        foundVersion != null,
                        autoRelease ? CHECKED : UNCHECKED
                );
            };
        }

        @Override
        public void execute(PropertyHolder context) {
            Environment.this.setAutoRelease(!getAutoRelease(true));
            if (getID() != null) {
                if (model.getChanges().equals(Collections.singletonList(PROP_AUTO_RELEASE))) {
                    try {
                        Environment.this.model.commit(true);
                    } catch (Exception e) {/**/}
                }
            }
            activate();
        }
        
        private Entity findEntity(String version) {
            Entity repository  = Environment.this.getRepository(true);
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