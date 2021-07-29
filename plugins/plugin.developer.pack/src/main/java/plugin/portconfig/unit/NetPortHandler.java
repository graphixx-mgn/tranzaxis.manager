package plugin.portconfig.unit;

import codex.type.EntityRef;
import codex.utils.ImageUtils;

@Unit(serviceUri = "http://schemas.radixware.org/netporthandler.wsdl")
public class NetPortHandler extends AbstractInstanceUnit {

    public NetPortHandler(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/nph.png"), title);
    }
}
