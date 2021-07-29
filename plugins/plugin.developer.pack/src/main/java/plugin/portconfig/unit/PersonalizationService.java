package plugin.portconfig.unit;

import codex.type.EntityRef;
import codex.utils.ImageUtils;

@Unit(serviceUri = "http://schemas.tranzaxis.com/cryptopersonalization.wsdl")
public class PersonalizationService extends AbstractInstanceUnit {

    public PersonalizationService(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/perso.png"), title);
    }
}
