package plugin.portconfig.unit;

import codex.database.IDatabaseAccessService;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Database;
import manager.nodes.Environment;
import org.atteo.classindex.IndexSubclasses;
import plugin.portconfig.AccessPoint;
import javax.swing.*;
import java.sql.ResultSet;
import java.sql.SQLException;

@IndexSubclasses
@Unit(serviceUri = "")
public class AbstractInstanceUnit extends AccessPoint {

    private final static IDatabaseAccessService DAS = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class);

    public AbstractInstanceUnit(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/unit.png"), title);
    }

    public AbstractInstanceUnit(EntityRef owner, ImageIcon icon, String title) {
        super(owner, icon, title);
    }

    protected String loadSettingsQuery() {
        return Language.get(AbstractInstanceUnit.class, "load", Language.DEF_LOCALE);
    }

    protected String saveSettingsQuery() {
        return Language.get(AbstractInstanceUnit.class, "save", Language.DEF_LOCALE);
    }

    @Override
    protected AccessPointSettings loadSettings() {
        if (getOwner() != null) {
            Database database = getEnvironment().getDataBase(true);
            try (ResultSet rs = DAS.select(
                    database.getConnectionID(false),
                    loadSettingsQuery(),
                    Integer.valueOf(getPID())
            )) {
                if (rs.next()) {
                    return new AccessPointSettings(
                            rs.getString("TITLE"),
                            rs.getString("ADDRESS"),
                            rs.getBoolean("USE")
                    );
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return new AccessPointSettings(null, null, null);
    }

    @Override
    protected void saveSettings() {
        Database database = getEnvironment().getDataBase(true);
        try {
            DAS.update(
                    database.getConnectionID(false),
                    saveSettingsQuery(),
                    getAddress(true),
                    Integer.valueOf(getPID())
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Environment getEnvironment() {
        return (Environment) getOwner();
    }
}
