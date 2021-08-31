package plugin.portconfig.unit;

import codex.type.EntityRef;
import codex.utils.ImageUtils;

@Unit(serviceUri = "http://schemas.radixware.org/nethub.wsdl")
public class NetHub extends AbstractInstanceUnit {

    public NetHub(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/hub.png"), title);
    }
}
