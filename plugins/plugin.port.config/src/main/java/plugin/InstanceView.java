package plugin;

import codex.database.IDatabaseAccessService;
import codex.explorer.tree.NodeTreeModel;
import codex.model.Catalog;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.utils.Language;
import manager.nodes.Database;
import manager.nodes.Environment;
import org.atteo.classindex.ClassIndex;
import units.InstanceControlService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class InstanceView extends Catalog {

    private final static IDatabaseAccessService DAS = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class);
    private final static Map<String, Class<? extends AbstractInstanceUnit>>  UNITS = StreamSupport.stream(
            ClassIndex.getSubclasses(AbstractInstanceUnit.class, AbstractInstanceUnit.class.getClassLoader()).spliterator(),
            false
            ).collect(Collectors.toMap(
                unitClass -> unitClass.getAnnotation(Unit.class).serviceUri(),
                unitClass -> unitClass
            ));

    public InstanceView(EntityRef environment, String PID) {
        super(environment, null, PID, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return InstanceControlService.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    public void loadChildren() {
        NodeTreeModel treeModel = new NodeTreeModel(this);
        StreamSupport.stream(treeModel.spliterator(), false).forEach(parentNode -> {
            parentNode.childrenList().forEach(parentNode::delete);
        });

        Database database = getEnvironment().getDataBase(true);
        Integer  instance = getEnvironment().getInstanceId();

        Entity ics = Entity.newInstance(getChildClass(), getOwner().toRef(), String.valueOf(instance));
        insert(ics);

        try (ResultSet rs = DAS.select(
                database.getConnectionID(false),
                Language.get(InstanceView.class, "select", Locale.US),
                instance
        )) {
            while (rs.next()) {
                String id  = rs.getString("ID");
                String uri = rs.getString("URI").replaceAll("(.*)#.*", "$1");

                Class<? extends AbstractInstanceUnit> unitClass = getUnitClass(uri);
                AbstractInstanceUnit unit = Entity.newInstance(unitClass, getOwner().toRef(), id);
                if (unit.getUsed()) {
                    ics.insert(unit);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Class<? extends AbstractInstanceUnit> getUnitClass(String uri) {
        return UNITS.getOrDefault(uri, AbstractInstanceUnit.class);
    }

    private Environment getEnvironment() {
        return (Environment) getOwner();
    }

}
