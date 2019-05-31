package units;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import plugin.AbstractInstanceUnit;

@plugin.Unit(serviceUri = "http://schemas.tranzaxis.com/crypto.wsdl")
public class CryptoService extends AbstractInstanceUnit {

    public CryptoService(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/crypto.png"), title);
    }
}
