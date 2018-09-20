package manager.nodes;

import codex.command.EditorCommand;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.mask.DataSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import javax.swing.SwingUtilities;
import manager.commands.RunAll;
import manager.commands.RunExplorer;
import manager.commands.RunServer;
import manager.type.WCStatus;

public class Environment extends Entity implements INodeListener {
    
//    private final static Pattern PATTERN_DEV_URI = Pattern.compile(".*BaseDevUri=\"([a-z\\.]*)\".*");
    private final static IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
    
//    private final IDataSupplier<String> layerSupplier = new RowSelector(
//            RowSelector.Mode.Value, () -> {
//                return ((Database) model.getUnsavedValue("database")).getConnectionID(true);
//            }, 
//            "SELECT LAYERURI, VERSION, UPGRADEDATE FROM (\n" +
//            "    SELECT MIN(SEQ) AS SEQ, LAYERURI, MIN(VERSION) AS VERSION, MIN(UPGRADEDATE) AS UPGRADEDATE FROM (\n" +
//            "        SELECT ROWNUM AS SEQ, LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION \n" +
//            "        UNION \n" +
//            "        SELECT 1000, ?, NULL, NULL FROM DUAL\n" +
//            "    ) \n" +
//            "    GROUP BY LAYERURI\n" +
//            "    HAVING LAYERURI IS NOT NULL\n" +
//            "    ORDER BY SEQ ASC\n" +
//            ")",
//            () -> {
//                return getBaseDevUri((Repository) model.getUnsavedValue("repository"));
//            }
//    );
    
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
                    if (model.getUnsavedValue("version") == null) 
                        return EntityRef.Match.Unknown;

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
                    try {
                        model.setValue("version", versionSupplier.call());
                    } catch (Exception e) {}
                }
            });
            thread.start();
            return null;
        });
        model.addUserProp("instanceId", new ArrStr().setMask(new DataSetMask(
                "{0} - {1}", instanceSupplier
        )), false, Access.Select);
        
        model.addUserProp("repository",  new EntityRef(Repository.class), true,  Access.Select);
        model.addUserProp(offshoot,      Access.Select);
        model.addUserProp(release,       Access.Select);
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
                
        model.getEditor("release").setVisible(model.getValue("repository")  != null);
        model.getEditor("offshoot").setVisible(model.getValue("repository") != null);
        model.getEditor("instanceId").setVisible(model.getValue("database")  != null);
        model.getEditor("version").setVisible(model.getValue("database")     != null);
        
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
                        model.setValue("release", null);
                    }
                    release.setMandatory(newValue == null);
                    break;
            }
        });
        
        // Commands
        addCommand(new RunAll());
        addCommand(new RunServer());
        addCommand(new RunExplorer());
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

//    private String[] getBaseDevUri(Repository repo) {
//        String devUri = null;
//        
//        if (repo != null) {
//            String repoUrl = repo.model.getValue("repoUrl").toString();
//            try {
//                InputStream in = SVN.readFile(repoUrl, "config/repository.xml", repo.getAuthManager());
//                Matcher m = PATTERN_DEV_URI.matcher(IOUtils.toString(in, Charset.forName("UTF-8")));
//                if (m.find()) {
//                    devUri = m.group(1);
//                }
//            } catch (IOException | SVNException e) {
//                e.printStackTrace();
//            }
//        }
//        return new String[] { devUri };
//    }
    
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
    
}