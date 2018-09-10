package manager.nodes;

import codex.database.RowSelector;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.mask.DataSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import manager.commands.RunAll;
import manager.commands.RunExplorer;
import manager.commands.RunServer;
import manager.svn.SVN;
import manager.type.WCStatus;
import org.apache.commons.io.IOUtils;

public class Environment extends Entity implements INodeListener {
    
    private final static Pattern PATTERN_DEV_URI = Pattern.compile(".*BaseDevUri=\"([a-z\\.]*)\".*");
    
    private final IDataSupplier<String> instanceSupplier = new RowSelector(
            RowSelector.Mode.Row, () -> {
                return ((Database) model.getUnsavedValue("database")).getConnectionID(true);
            }, 
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
    );
    
    private final IDataSupplier<String> layerSupplier = new RowSelector(
            RowSelector.Mode.Row, () -> {
                return ((Database) model.getUnsavedValue("database")).getConnectionID(true);
            }, 
            "SELECT LAYERURI, VERSION, UPGRADEDATE FROM (\n" +
            "    SELECT MIN(SEQ) AS SEQ, LAYERURI, MIN(VERSION) AS VERSION, MIN(UPGRADEDATE) AS UPGRADEDATE FROM (\n" +
            "        SELECT ROWNUM AS SEQ, LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION \n" +
            "        UNION \n" +
            "        SELECT 1000, ?, NULL, NULL FROM DUAL\n" +
            "    ) \n" +
            "    GROUP BY LAYERURI\n" +
            "    HAVING LAYERURI IS NOT NULL\n" +
            "    ORDER BY SEQ ASC\n" +
            ")",
            () -> {
                return getBaseDevUri((Repository) model.getValue("repository"));
            }
    );
    
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

                    String layerVersion = ((List<String>) model.getUnsavedValue("layerURI")).get(1);
                    if (layerVersion == null || layerVersion.isEmpty())
                        return EntityRef.Match.Unknown;

                    String entityVersion = entity.model.getPID();
                    return entityVersion.equals(layerVersion.substring(0, layerVersion.lastIndexOf("."))) ? EntityRef.Match.Exact : EntityRef.Match.None;
                }
        ), true);
        
        MutablePropHolder release = new MutablePropHolder("release", new EntityRef(
                Release.class, 
                (entity) -> {
                    return entity.getParent().getParent().equals(model.getUnsavedValue("repository"));
                },
                (entity) -> {
                    if (model.getUnsavedValue("layerURI") == null) 
                        return EntityRef.Match.Unknown;

                    String layerVersion = ((List<String>) model.getUnsavedValue("layerURI")).get(1);
                    if (layerVersion == null || layerVersion.isEmpty())
                        return EntityRef.Match.Unknown;

                    String entityVersion = entity.model.getPID();
                    return entityVersion.equals(layerVersion) ? EntityRef.Match.Exact : (
                                entityVersion.substring(0, entityVersion.lastIndexOf(".")).equals(
                                        layerVersion.substring(0, layerVersion.lastIndexOf("."))
                                ) ? EntityRef.Match.About : EntityRef.Match.None
                           );
                }
        ), true);
        
        // Properties
        model.addUserProp("jvmServer",   new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp("jvmExplorer", new ArrStr(new ArrayList<>()),   false, Access.Select);
        
        model.addUserProp("database",    new EntityRef(Database.class),   true,  null);
        model.addUserProp("layerURI",    new ArrStr().setMask(new DataSetMask(
                "{0}", layerSupplier
        )),  true, Access.Select);
        model.addUserProp("instanceId",  new ArrStr().setMask(new DataSetMask(
                "{0} - {1}", instanceSupplier
        )), true, null);
        
        model.addUserProp("repository",  new EntityRef(Repository.class), true,  Access.Select);
       
        model.addUserProp(offshoot, Access.Select);
        model.addUserProp(release, Access.Select);
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
        model.addPropertyGroup(Language.get("group@database"), "database", "layerURI", "instanceId");
        model.addPropertyGroup(Language.get("group@binaries"), "repository", "offshoot", "release");
        
        model.getEditor("release").setVisible(model.getValue("repository")  != null);
        model.getEditor("offshoot").setVisible(model.getValue("repository") != null);
        model.getEditor("layerURI").setVisible(model.getValue("database")   != null);
        model.getEditor("instanceId").setVisible(model.getValue("database") != null);
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case "repository":
                    model.setValue("release",  null);
                    model.setValue("offshoot", null);
                    model.getEditor("release").setVisible(newValue  != null);
                    model.getEditor("offshoot").setVisible(newValue != null);
                    break;
                case "database":
                    model.setValue("layerURI",   null);
                    model.setValue("instanceId", null);
                    model.getEditor("layerURI").setVisible(newValue   != null);
                    model.getEditor("instanceId").setVisible(newValue != null);
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
        addCommand(new RunAll().setGroupId("run"));
        addCommand(new RunServer().setGroupId("run"));
        addCommand(new RunExplorer().setGroupId("run"));
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

    private String[] getBaseDevUri(Repository repo) {
        String devUri = null;
        
        if (repo != null) {
            String repoUrl = repo.model.getValue("repoUrl").toString();
            InputStream in = SVN.readFile(repoUrl, "config/repository.xml", null, null);
            try {
                Matcher m = PATTERN_DEV_URI.matcher(IOUtils.toString(in, Charset.forName("UTF-8")));
                if (m.find()) {
                    devUri = m.group(1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new String[] { devUri };
    }
    
    private class MutablePropHolder extends PropertyHolder {
    
        public MutablePropHolder(String name, IComplexType value, boolean require) {
            super(name, value, require);
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