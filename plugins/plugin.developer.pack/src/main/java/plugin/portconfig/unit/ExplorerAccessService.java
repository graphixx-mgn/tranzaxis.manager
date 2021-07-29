package plugin.portconfig.unit;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

@Unit(serviceUri = "http://schemas.radixware.org/eas.wsdl")
public class ExplorerAccessService extends AbstractInstanceUnit {

    public ExplorerAccessService(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/eas.png"), title);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
}
