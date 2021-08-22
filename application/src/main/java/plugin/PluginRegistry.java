package plugin;

import codex.model.ICatalog;
import codex.model.PolyMorph;

abstract class PluginRegistry extends PolyMorph implements ICatalog {

    PluginRegistry(String title) {
        super(null, title);
    }
}
