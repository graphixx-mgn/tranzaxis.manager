package units;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.AbstractInstanceUnit;

@plugin.Unit(serviceUri = "http://schemas.radixware.org/systeminstancecontrol.wsdl")
public class InstanceControlService extends AbstractInstanceUnit {
    public InstanceControlService(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/ics.png"), title);
    }

    protected final String loadSettingsQuery() {
        return Language.get(InstanceControlService.class, "load", Language.DEF_LOCALE);
    }

    protected String saveSettingsQuery() {
        return Language.get(InstanceControlService.class, "save", Language.DEF_LOCALE);
    }

}
