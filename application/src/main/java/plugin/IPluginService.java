package plugin;

import codex.service.IService;
import java.util.Collection;

public interface IPluginService extends IService {

    @Override
    default String getTitle() {
        return "Plugin Provider Service";
    }

    Collection<PluginPackage> getPackages();
    void readPackages();

    void addListener(IPluginServiceListener listener);
    void removeListener(IPluginServiceListener listener);

    interface IPluginServiceListener {
        void packageRegistered(int index, PluginPackage pluginPackage);
        void packageUnregistered(int index, PluginPackage pluginPackage);
    }
}
