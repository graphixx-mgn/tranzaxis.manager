package plugin.portconfig.unit;

import codex.type.EntityRef;
import codex.utils.ImageUtils;

@Unit(serviceUri = "http://schemas.tranzaxis.com/tran.wsdl")
public class RegularTransactionProtocol extends AbstractInstanceUnit {

    public RegularTransactionProtocol(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/rtp.png"), title);
    }
}
