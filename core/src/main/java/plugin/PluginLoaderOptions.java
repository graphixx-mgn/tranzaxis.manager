package plugin;

import codex.service.RemoteServiceOptions;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

import javax.swing.*;

public class PluginLoaderOptions extends RemoteServiceOptions {

    public PluginLoaderOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.combine(
                ImageUtils.getByPath("/images/repository.png"),
                ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 20, 20),
                SwingConstants.SOUTH_EAST
        ));
    }
}
