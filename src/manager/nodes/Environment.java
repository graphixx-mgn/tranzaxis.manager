package manager.nodes;

import codex.database.RowSelector;
import codex.mask.DataSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.util.ArrayList;
import manager.commands.RunExplorer;
import manager.commands.RunServer;

public class Environment extends Entity {
    
    private final IDataSupplier<String> instanceSupplier = new RowSelector(
            RowSelector.Mode.Row, () -> {
                return ((Database) model.getUnsavedValue("database")).getConnectionID();
            }, 
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
    );
    
    public Environment(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/instance.png"), title, null);
        
        // Properties
        model.addUserProp("jvmServer",   new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp("jvmExplorer", new ArrStr(new ArrayList<>()),   false, Access.Select);
        model.addUserProp("database",    new EntityRef(Database.class),   true,  null);
        model.addUserProp("instanceId",  new ArrStr().setMask(new DataSetMask(
                "{0} - {1}", instanceSupplier
        )), true, null);
        model.addUserProp("repository",  new EntityRef(Repository.class), true,  null);
        model.addUserProp("offshoot",    new EntityRef(Offshoot.class, (entity) -> {
            return 
                    entity.getParent().getParent().equals(model.getUnsavedValue("repository")) && 
                    entity.model.getValue("built") != null;
        }), true, null);
        model.addUserProp("userNote",    new Str(null), false, null);
        
        // Editor settings
        model.getEditor("offshoot").setEditable(model.getValue("repository") != null);
        model.getEditor("instanceId").setEditable(model.getValue("database") != null);
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case "repository":
                    model.setValue("offshoot", null);
                    model.getEditor("offshoot").setEditable(newValue != null);
                    break;
                case "database":
                    model.getEditor("instanceId").setEditable(newValue != null);
                    break;
            }
        });
        
        // Commands
        addCommand(new RunServer());
        addCommand(new RunExplorer());
    }
    
}