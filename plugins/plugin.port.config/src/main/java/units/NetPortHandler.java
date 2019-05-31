package units;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import plugin.AbstractInstanceUnit;

@plugin.Unit(serviceUri = "http://schemas.radixware.org/netporthandler.wsdl")
public class NetPortHandler extends AbstractInstanceUnit {

    public NetPortHandler(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/nph.png"), title);
    }
}
