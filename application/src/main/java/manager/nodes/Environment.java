package manager.nodes;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.command.ValueProvider;
import codex.database.*;
import codex.editor.*;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.mask.DataSetMask;
import codex.mask.EntityFilter;
import codex.model.*;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.supplier.RowSelector;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import manager.commands.environment.RunAll;
import manager.commands.environment.RunExplorer;
import manager.commands.environment.RunServer;
import manager.type.SourceType;

public class Environment extends Entity implements INodeListener {

    private final static ImageIcon UPDATE    = ImageUtils.getByPath("/images/update.png");
    private final static ImageIcon UNKNOWN   = ImageUtils.getByPath("/images/unavailable.png");
    private final static ImageIcon CHECKED   = ImageUtils.getByPath("/images/lock.png");
    private final static ImageIcon UNCHECKED = ImageUtils.getByPath("/images/unlock.png");

    static {
        CommandRegistry.getInstance().registerCommand(RunAll.class);
        CommandRegistry.getInstance().registerCommand(RunServer.class);
        CommandRegistry.getInstance().registerCommand(RunExplorer.class);
    }

    // General properties
    private final static String PROP_LAYER_URI     = "layerURI";
    private final static String PROP_DATABASE      = "database";
    private final static String PROP_VERSION       = "version";
    private final static String PROP_INSTANCE_ID   = "instanceId";
    private final static String PROP_REPOSITORY    = "repository";
    private final static String PROP_SOURCE_TYPE   = "srcType";
    private final static String PROP_BINARIES      = "binaries";
    private final static String PROP_OFFSHOOT      = "offshoot";
    private final static String PROP_RELEASE       = "release";
    private final static String PROP_USER_NOTE     = "userNote";
    private final static String PROP_AUTO_RELEASE  = "autoRelease";

    // Extra properties
    private final static String PROP_JVM_SERVER    = "jvmServer";
    private final static String PROP_JVM_EXPLORER  = "jvmExplorer";
    private final static String PROP_STARTER_OPTS  = "starterOpts";
    private final static String PROP_SERVER_OPTS   = "serverOpts";
    private final static String PROP_EXPLORER_OPTS = "explorerOpts";
    private final static String PROP_RUN_SERVER    = "runServer";
    private final static String PROP_RUN_EXPLORER  = "runExplorer";

    private final ValueProvider<String> layerSelector = new ValueProvider<>(RowSelector.Single.newInstance(new RowSupplier(
            () -> getDataBase(true).getConnectionID(false),
       "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
        ) {
            @Override
            public boolean ready() {
                return getDataBase(true) != null && getDataBase(true).isConnected() && super.ready();
            }
        }
    ));

    private final DataSetMask instanceSelector = new DataSetMask(RowSelector.Multiple.newInstance(new RowSupplier(
            () -> getDataBase(true).getConnectionID(false),
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
        ) {
            @Override
            public boolean ready() {
                return getDataBase(true) != null && getDataBase(true).isConnected() && super.ready();
            }
        }), "{0} - {1}"
    );

    private final Consumer<String> sourceUpdater = propName -> {
        boolean activate = getRepository(true) != null && getSourceType().name().toLowerCase().equals(propName);
        model.getEditor(propName).setVisible(activate);
        model.getProperty(propName).setRequired(activate);
    };
    
    public Environment(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/instance.png"), title, null);

        // General properties
        model.addUserProp(PROP_LAYER_URI,    new Str(null),             true,  Access.Select);
        model.addUserProp(PROP_DATABASE,     new EntityRef<>(Database.class),   false, null);
        model.addDynamicProp(PROP_VERSION,   new Str(null), Access.Select, () -> {
            new Thread(() -> SwingUtilities.invokeLater(() -> setVersion(getLayerVersion()))).start();
            return getVersion();
        });
        model.addUserProp(PROP_INSTANCE_ID,  new ArrStr().setMask(instanceSelector), false, Access.Select);
        model.addUserProp(PROP_REPOSITORY,   new EntityRef<>(Repository.class), true, Access.Select);
        model.addUserProp(PROP_SOURCE_TYPE,  new Enum<>(SourceType.None), true, Access.Select);
        model.addUserProp(PROP_OFFSHOOT,     new EntityRef<>(Offshoot.class).setMask(
                new EntityFilter<>(
                        offshoot ->
                            offshoot.getRepository() == getRepository(true) &&
                            offshoot.getBuiltStatus() != null &&
                            offshoot.isWCLoaded(),
                        offshoot -> {
                            String layerVersion = getVersion();
                            if (layerVersion == null || layerVersion.isEmpty())
                                return EntityFilter.Match.Unknown;
                            String offshootVersion = offshoot.getVersion();
                            layerVersion = layerVersion.substring(0, layerVersion.lastIndexOf("."));
                            return offshootVersion.equals(layerVersion) ? EntityFilter.Match.Exact : EntityFilter.Match.None;
                })
        ), true, Access.Select);

        model.addUserProp(PROP_RELEASE, new EntityRef<Release>(Release.class) {
            @Override
            public IEditorFactory<EntityRef<Release>, Release> editorFactory() {
                return propHolder -> new EntityRefEditor<Release>(propHolder) {
                    @Override
                    protected List<Release> getValues() {
                        Repository repository = getRepository(true);
                        List<Release> values = new LinkedList<>();
                        if (repository != null) {
                            newInstance(ReleaseList.class, repository.toRef(), null).childrenList().forEach(iNode -> {
                                values.add((Release) iNode);
                            });
                        }
                        return values;
                    }
                };
            }
        }.setMask(
                new EntityFilter<>(
                        release -> release.getRepository()  == getRepository(true),
                        release -> {
                            String layerVersion = getVersion();
                            if (layerVersion == null || layerVersion.isEmpty())
                                return EntityFilter.Match.Unknown;
                            String releaseVersion = release.getVersion();
                            return releaseVersion.equals(layerVersion) ? EntityFilter.Match.Exact : (
                                    releaseVersion.substring(0, releaseVersion.lastIndexOf(".")).equals(
                                            layerVersion.substring(0, layerVersion.lastIndexOf("."))
                                    ) ? EntityFilter.Match.About : EntityFilter.Match.None
                            );
                        })
        ), true, Access.Select);
        model.addDynamicProp(
                PROP_BINARIES,
                new EntityRef<>(BinarySource.class), Access.Edit,
                () -> getSourceType().getBinarySource(this),
                PROP_REPOSITORY, PROP_SOURCE_TYPE, PROP_OFFSHOOT, PROP_RELEASE
        );
        model.addUserProp(PROP_USER_NOTE,    new Str(null), false, null);
        model.addUserProp(PROP_AUTO_RELEASE, new Bool(false), false, Access.Any);

        // Extra properties
        model.addExtraProp(PROP_JVM_SERVER,    new ArrStr(),false);
        model.addExtraProp(PROP_JVM_EXPLORER,  new ArrStr(),false);
        model.addExtraProp(PROP_STARTER_OPTS,  new ArrStr(), true);
        model.addExtraProp(PROP_SERVER_OPTS,   new ArrStr(), true);
        model.addExtraProp(PROP_EXPLORER_OPTS, new ArrStr(), true);

        for (String runProp : Arrays.asList(PROP_RUN_SERVER, PROP_RUN_EXPLORER)) {
            model.addDynamicProp(
                    runProp,
                    new AnyType() {
                        @Override
                        public IEditorFactory<AnyType, Object> editorFactory() { return TextView::new; }
                    },
                    Access.Extra,
                    () -> {
                        List<String> cmdList = null;
                        switch (runProp) {
                            case PROP_RUN_SERVER   : cmdList = getServerCommand(false); break;
                            case PROP_RUN_EXPLORER : cmdList = getExplorerCommand(false); break;
                        }
                        return cmdList == null || cmdList.isEmpty() ? null : String.join(" ", cmdList);
                    },
                    PROP_JVM_SERVER, PROP_JVM_EXPLORER,
                    PROP_LAYER_URI,
                    PROP_DATABASE, PROP_INSTANCE_ID,
                    PROP_BINARIES, PROP_OFFSHOOT, PROP_RELEASE,
                    PROP_STARTER_OPTS, PROP_SERVER_OPTS, PROP_EXPLORER_OPTS
            );
        }
        
        // Property settings
        if (getRelease(false) != null) {
            getRelease(false).addNodeListener(this);
        }
        model.addPropertyGroup(Language.get("group@database"), PROP_DATABASE, PROP_INSTANCE_ID, PROP_VERSION);
        model.addPropertyGroup(Language.get("group@binaries"), PROP_REPOSITORY, PROP_SOURCE_TYPE, PROP_OFFSHOOT, PROP_RELEASE);
        model.addPropertyGroup(Language.get(EnvironmentRoot.class,"group@jvm"), PROP_JVM_SERVER, PROP_JVM_EXPLORER);
        model.addPropertyGroup(Language.get(EnvironmentRoot.class,"group@app"), PROP_STARTER_OPTS, PROP_SERVER_OPTS, PROP_EXPLORER_OPTS);
        model.addPropertyGroup(Language.get("group@commands"), PROP_RUN_SERVER, PROP_RUN_EXPLORER);
        
        // Editor settings
        model.getEditor(PROP_LAYER_URI).addCommand(layerSelector);
        model.getEditor(PROP_SOURCE_TYPE).setVisible(getRepository(true) != null);
        model.getEditor(PROP_INSTANCE_ID).setVisible(getDataBase(true)   != null);
        model.getEditor(PROP_VERSION).setVisible(getDataBase(true)       != null);

        // Editor commands
        SyncRelease syncRelease = new SyncRelease();
        model.addModelListener(syncRelease);

        model.getEditor(PROP_VERSION).addCommand(new UpdateVersion());
        model.getEditor(PROP_RUN_SERVER).addCommand(new CopyToClipboard());
        model.getEditor(PROP_RUN_EXPLORER).addCommand(new CopyToClipboard());
        model.getEditor(PROP_RELEASE).addCommand(syncRelease);
        
        // Handlers
        IPropertyChangeListener dbListener = (name, oldValue, newValue) -> {
            if (name.equals(Database.PROP_CONN_STAT)) {
                layerSelector.activate();
                instanceSelector.activate();
                model.updateDynamicProps(PROP_VERSION);
            }
        };
        if (getDataBase(false) != null) {
            getDataBase(false).model.addChangeListener(dbListener);
        }
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case PROP_DATABASE:
                    if (oldValue != null) {
                        ((Database) oldValue).model.removeChangeListener(dbListener);
                    }
                    if (newValue != null) {
                        ((Database) newValue).model.addChangeListener(dbListener);
                    }

                    layerSelector.activate();
                    instanceSelector.activate();

                    model.updateDynamicProps(PROP_VERSION);
                    model.getEditor(PROP_INSTANCE_ID).setVisible(newValue != null);
                    model.getEditor(PROP_VERSION).setVisible(newValue     != null);

                    model.setValue(PROP_INSTANCE_ID, null);
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
                    model.setValue(PROP_OFFSHOOT, null);
                    model.setValue(PROP_RELEASE, null);
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
            }
        });

        sourceUpdater.accept(PROP_OFFSHOOT);
        sourceUpdater.accept(PROP_RELEASE);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        EnvironmentRoot envRoot = (EnvironmentRoot) parent;
        model.updateDynamicProps(PROP_RUN_SERVER, PROP_RUN_EXPLORER);
        envRoot.model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(EnvironmentRoot.PROP_JVM_SOURCE)) {
                    Environment.this.model.updateDynamicProps(PROP_RUN_SERVER, PROP_RUN_EXPLORER);
                }
            }
        });
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

    public String getLayerUri(boolean unsaved) {
        return (String) (unsaved ? model.getUnsavedValue(PROP_LAYER_URI) : model.getValue(PROP_LAYER_URI));
    }

    public final Database getDataBase(boolean unsaved) {
        return (Database) (unsaved ? model.getUnsavedValue(PROP_DATABASE) : model.getValue(PROP_DATABASE));
    }

    public String getVersion() {
        return (String) model.getValue(PROP_VERSION);
    }

    public String getLayerVersion() {
        Database database = getDataBase(true);
        String   layerUri = getLayerUri(true);
        if (IComplexType.notNull(database, layerUri)) {
            IDatabaseAccessService DAS = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class);
            try (ResultSet rs = DAS.select(database.getConnectionID(false), "SELECT VERSION FROM RDX_DDSVERSION WHERE LAYERURI = ?", layerUri)) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            } catch (SQLException e) {
                if (e.getErrorCode() != 0)
                    Logger.getLogger().warn("Database query failed: {0}", e.getMessage());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
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

    public final Offshoot getOffshoot(boolean unsaved) {
        return (Offshoot) (unsaved ? model.getUnsavedValue(PROP_OFFSHOOT) : model.getValue(PROP_OFFSHOOT));
    }

    public final BinarySource getBinaries() {
        return (BinarySource) model.getValue(PROP_BINARIES);
    }

    public final Release getRelease(boolean unsaved) {
        return (Release) (unsaved ? model.getUnsavedValue(PROP_RELEASE) : model.getValue(PROP_RELEASE));
    }

    @SuppressWarnings("unchecked")
    public final List<String> getStarterFlags(boolean unsaved) {
        return (List<String>) (unsaved ? model.getUnsavedValue(PROP_STARTER_OPTS) : model.getValue(PROP_STARTER_OPTS));
    }

    @SuppressWarnings("unchecked")
    public final List<String> getServerFlags(boolean unsaved) {
        return (List<String>) (unsaved ? model.getUnsavedValue(PROP_SERVER_OPTS) : model.getValue(PROP_SERVER_OPTS));
    }

    @SuppressWarnings("unchecked")
    public final List<String> getExplorerFlags(boolean unsaved) {
        return (List<String>) (unsaved ? model.getUnsavedValue(PROP_EXPLORER_OPTS) : model.getValue(PROP_EXPLORER_OPTS));
    }

    public List<String> getServerCommand(boolean addSplash) {
        Database db = getDataBase(true);
        EnvironmentRoot envRoot = (EnvironmentRoot) getParent();
        return envRoot != null && canStartServer() ? new LinkedList<String>() {{
            final String javaPath = envRoot.getJvmSource().get(EnvironmentRoot.PROP_JVM_PATH);
            add(javaPath == null ? "java" : javaPath);
            addAll(getJvmServer());
            add("-jar");
            add(getBinaries().getStarterPath());
            add("-workDir="+getBinaries().getLocalPath());
            add("-topLayerUri=" + getLayerUri(true));
            if (addSplash) {
                add("-showSplashScreen=Server: "+Environment.this+" ("+getBinaries().getPID()+")");
            }
            addAll(getStarterFlags(true));
            add("org.radixware.kernel.server.Server");
            add("-dbUrl");
            add("jdbc:oracle:thin:@" + db.getDatabaseUrl(false));
            add("-user");
            add(db.getDatabaseUser(false));
            add("-pwd");
            add(db.getDatabasePassword(false));
            add("-dbSchema");
            add(db.getDatabaseUser(false));
            add("-instance");
            add(getInstanceId().toString());
            addAll(getServerFlags(true));
            }} : Collections.emptyList();
    }

    public List<String> getExplorerCommand(boolean addSplash) {
        EnvironmentRoot envRoot = (EnvironmentRoot) getParent();
        return envRoot != null && canStartExplorer() ? new LinkedList<String>() {{
            final String javaPath = envRoot.getJvmSource().get(EnvironmentRoot.PROP_JVM_PATH);
            add(javaPath == null ? "java" : javaPath);
            addAll(getJvmExplorer());
            add("-jar");
            add(getBinaries().getStarterPath());
            add("-workDir="+getBinaries().getLocalPath());
            add("-topLayerUri="+getLayerUri(true));
            if (addSplash) {
                add("-showSplashScreen=Explorer: "+Environment.this+" ("+getBinaries().getPID()+")");
            }
            addAll(getStarterFlags(true));
            add("org.radixware.kernel.explorer.Explorer");
            addAll(getExplorerFlags(true));
        }} : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> getJvmServer() {
        return (List<String>) model.getValue(PROP_JVM_SERVER);
    }

    @SuppressWarnings("unchecked")
    private List<String> getJvmExplorer() {
        return (List<String>) model.getValue(PROP_JVM_EXPLORER);
    }

    private SourceType getSourceType() {
        return (SourceType) model.getUnsavedValue(PROP_SOURCE_TYPE);
    }
    
    private boolean getAutoRelease() {
        return model.getUnsavedValue(PROP_AUTO_RELEASE) == Boolean.TRUE;
    }
    
    public final void setVersion(String value) {
        model.setValue(PROP_VERSION, value);
    }
    
    private void setAutoRelease(Boolean value) {
        model.setValue(PROP_AUTO_RELEASE, value);
    }
    
    public boolean canStartServer() {
        return IComplexType.notNull(
                getBinaries(),
                getDataBase(true),
                getLayerUri(false),
                getInstanceId()
        ) && getDataBase(true).isConnected();
    }
    
    public boolean canStartExplorer() {
        return IComplexType.notNull(
                getBinaries(),
                getLayerUri(false)
        );
    }

    
    private class SyncRelease extends EditorCommand<EntityRef<Release>, Release> implements IModelListener {

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
                boolean autoRelease  = Environment.this.getAutoRelease();

                AbstractEditor releaseEditor = (AbstractEditor) model.getEditor(PROP_RELEASE);
                releaseEditor.setEditable(!autoRelease || foundVersion == null);

                if (foundVersion != null && autoRelease && !foundVersion.equals(usedVersion)) {
                    Entity newValue = findEntity(foundVersion);
                    if (newValue != null) {
                        model.setValue(PROP_RELEASE, (Release) newValue);
                        if (model.getChanges().equals(Collections.singletonList(PROP_RELEASE))) {
                            try {
                                Environment.this.model.commit(true);
                                Logger.getLogger().info(
                                        "Environment ''{0}'' release has been updated automatically to ''{1}''",
                                        Environment.this.getPID(), newValue
                                );
                            } catch (Exception e) {
                                //
                            }
                        }
                        releaseEditor.updateUI();
                    }
                }
                return new CommandStatus(
                        foundVersion != null,
                        autoRelease ? CHECKED : UNCHECKED
                );
            };
        }

        private Entity findEntity(String version) {
            Entity repository  = Environment.this.getRepository(true);
            if (repository != null) {
                return Entity.newInstance(Release.class, repository.toRef(), version);
            }
            return null;
        }

        @Override
        public void execute(PropertyHolder<EntityRef<Release>, Release> context) {
            Environment.this.setAutoRelease(!getAutoRelease());
            if (getID() != null) {
                if (model.getChanges().equals(Collections.singletonList(PROP_AUTO_RELEASE))) {
                    try {
                        Environment.this.model.commit(true);
                    } catch (Exception e) {/**/}
                }
            }
            activate();
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


    private static class CopyToClipboard extends EditorCommand<AnyType, Object> implements IModelListener {
        private final static ImageIcon CLIPBOARD = ImageUtils.getByPath("/images/paste.png");

        CopyToClipboard() {
            super(
                    CLIPBOARD,
                    Language.get(Environment.class, "command.copy"),
                    holder -> holder.getPropValue().getValue() != null
            );
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public void execute(PropertyHolder<AnyType, Object> context) {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(
                            new StringSelection((String) context.getPropValue().getValue()),
                            null
                    );
        }
    }

    private class UpdateVersion extends EditorCommand<AnyType, Object> implements IModelListener {

        UpdateVersion() {
            super(
                    UPDATE,
                    Language.get(Environment.class, "version.update"),
                    null
            );
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public void execute(PropertyHolder<AnyType, Object> context) {
            Environment.this.model.updateDynamicProps(PROP_VERSION);
        }
    }

}