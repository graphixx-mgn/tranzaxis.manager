package plugin.portconfig.unit;

import codex.type.EntityRef;
import codex.utils.ImageUtils;

@Unit(serviceUri = "http://schemas.radixware.org/aas.wsdl")
public class ArteAccessService extends AbstractInstanceUnit {

    public ArteAccessService(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/aas.png"), title);
    }
}
