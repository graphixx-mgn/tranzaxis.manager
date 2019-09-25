package units;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import plugin.AbstractInstanceUnit;

@plugin.Unit(serviceUri = "http://schemas.radixware.org/nethub.wsdl")
public class NetHub extends AbstractInstanceUnit {

    public NetHub(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/hub.png"), title);
    }
}
