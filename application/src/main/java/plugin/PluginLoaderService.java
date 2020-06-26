package plugin;

import codex.log.Logger;
import codex.model.Entity;
import codex.service.AbstractRemoteService;
import codex.service.RemoteServiceControl;
import codex.utils.LocaleContextHolder;
import manager.upgrade.stream.RemoteInputStream;
import manager.upgrade.stream.RemoteInputStreamServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PluginLoaderService extends AbstractRemoteService<PluginLoaderOptions, RemoteServiceControl> implements IPluginLoaderService {

    private final List<IPublicationListener> listeners = new LinkedList<>();

    public PluginLoaderService() throws RemoteException {}

    @Override
    public List<RemotePackage> getPublishedPackages(Locale locale) throws RemoteException {
        LocaleContextHolder.setLocale(locale);
        return PluginManager.getInstance().getPluginLoader().getPackages().stream()
                .filter(pluginPackage -> Entity.newInstance(PackageView.class, null, pluginPackage.getTitle()).isPublished())
                .map(RemotePackage::new)
                .collect(Collectors.toList());
    }

    @Override
    synchronized public void packagePublicationChanged(final RemotePackage remotePackage, boolean published) throws RemoteException {
        new LinkedList<>(listeners).forEach(listener -> listener.publicationEvent(remotePackage, published));
    }

    @Override
    public String getPackageFileChecksum(String pluginId, String pluginVersion) throws RemoteException {
        PluginPackage pluginPackage = PluginManager.getInstance().getPluginLoader().getPackageById(pluginId);
        if (pluginPackage.getVersion().equals(pluginVersion)) {
            try {
                return pluginPackage.getCheckSum();
            } catch (Exception e) {
                throw new RemoteException("Unable to get package checksum", e);
            }
        } else {
            throw new RemoteException("Version mismatch");
        }
    }

    @Override
    public RemoteInputStream getPackageFileStream(String pluginId, String pluginVersion) throws RemoteException {
        PluginPackage pluginPackage = PluginManager.getInstance().getPluginLoader().getPackageById(pluginId);
        if (pluginPackage.getVersion().equals(pluginVersion)) {
            File jar = new File(pluginPackage.getUrl().getFile());
            try {
                InputStream in = new FileInputStream(jar);
                return new RemoteInputStream(new RemoteInputStreamServer(in));
            } catch (IOException e) {
                Logger.getLogger().error("Error", e);
                throw new RemoteException(e.getMessage());
            }
        } else {
            throw new RemoteException("Version mismatch");
        }
    }

    synchronized void addPublicationListener(IPublicationListener listener) {
        listeners.add(listener);
    }

    synchronized void removePublicationListener(IPublicationListener listener) {
        listeners.remove(listener);
    }

}
