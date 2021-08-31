package plugin;

import codex.command.EntityGroupCommand;
import codex.service.ServiceRegistry;
import codex.task.ITaskExecutorService;
import codex.task.TaskOutput;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.bridj.util.Pair;
import javax.swing.*;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

class DownloadPackages extends EntityGroupCommand<PluginPackage> {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    private final static ImageIcon ICON_LOAD = ImageUtils.combine(
            ImageUtils.getByPath("/images/repository.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 20, 20),
            SwingConstants.SOUTH_EAST
    );

    public DownloadPackages() {
        super(
                "download plugin",
                Language.get("title"),
                ICON_LOAD,
                Language.get("title"),
                packageAdapter -> true
        );
    }

    @Override
    public Kind getKind() {
        return Kind.Admin;
    }

    @Override
    public void execute(List<PluginPackage> context, Map<String, IComplexType> params) {
        TES.quietTask(new Download(context));
    }

    private static class Download extends TaskOutput.Wizard<List<PluginPackage>> {

        private final List<PluginPackage> packages;
        private Download(List<PluginPackage> packages) {
            super(Language.get(DownloadPackages.class, "task@title"), ICON_LOAD);
            this.packages = packages;
        }

        @Override
        public void finished(List<PluginPackage> result) {}

        @Override
        protected List<PluginPackage> process() throws Exception {
            for (PluginPackage pluginPackage : packages) {
                IPluginRegistry.PackageDescriptor oldDescriptor = pluginPackage.getLocal();
                IPluginRegistry.PackageDescriptor newDescriptor = new ImportPackage(pluginPackage.getRemote()).process();
                PluginManager.getInstance().getProvider().reload(oldDescriptor, newDescriptor);
            }
            return packages;
        }
    }

    private static class ImportPackage extends TaskOutput.ExecPhase<IPluginRegistry.PackageDescriptor> {

        private final IPluginRegistry.PackageDescriptor remotePkg;

        ImportPackage(IPluginRegistry.PackageDescriptor remotePkg) {
            super(MessageFormat.format(
                    Language.get(DownloadPackages.class, "process@load"),
                    remotePkg.getId()
            ));
            this.remotePkg = remotePkg;
        }

        @Override
        protected Pair<String, IPluginRegistry.PackageDescriptor> execute() throws Exception {
            String remoteChecksum = remotePkg.checksum();
            MessageDigest digest  = MessageDigest.getInstance("MD5");
            File upgradeFile = new File(
                    PluginProvider.PLUGIN_LOCAL_DIR,
                    MessageFormat.format(
                            "{0}-{1}.jar",
                            remotePkg.getTitle(),
                            remotePkg.getVersion().getNumber()
                    )
            );
            try (
                InputStream inStream   = RemotePluginRegistry.getConnection(remotePkg.getUri()).getInputStream();
                OutputStream outStream = new FileOutputStream(upgradeFile)
            ) {
                byte[] data = new byte[1024];
                long totalRead = 0;
                int bytesRead = inStream.read(data);
                while (bytesRead != -1) {
                    totalRead = totalRead + bytesRead;
                    outStream.write(data, 0, bytesRead);
                    digest.update(data, 0, bytesRead);
                    bytesRead = inStream.read(data);
                }
            } catch (Throwable e) {
                if (upgradeFile.exists()) if (!upgradeFile.delete()) upgradeFile.deleteOnExit();
                throw new IOException(e.getMessage());
            }
            String localChecksum = DatatypeConverter.printHexBinary(digest.digest());
            if (!remoteChecksum.equals(localChecksum)) {
                throw new IOException(Language.get(DownloadPackages.class, "process@check"));
            }
            return new Pair<>(
                    null,
                    LocalPluginRegistry.getDescriptorByUri(upgradeFile.toURI())
            );
        }
    }
}
