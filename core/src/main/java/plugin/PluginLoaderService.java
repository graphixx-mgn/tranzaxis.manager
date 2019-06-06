package plugin;

import codex.service.AbstractRemoteService;
import codex.utils.LocaleContextHolder;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PluginLoaderService extends AbstractRemoteService implements IPluginLoaderService {

    public PluginLoaderService() throws RemoteException {}

    @Override
    public List<RemotePackage> getPublishedPackages(Locale locale) throws RemoteException {
        LocaleContextHolder.setLocale(locale);
        return PluginManager.getInstance().getPluginLoader().getPackages().stream()
                .map(RemotePackage::new)
                .collect(Collectors.toList());
    }
}
