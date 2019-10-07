package plugin;

import codex.component.dialog.Dialog;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.type.AnyType;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.BinarySource;
import manager.nodes.Offshoot;
import manager.nodes.Release;
import manager.svn.SVN;
import manager.xml.BranchDocument;
import manager.xml.ReleaseDocument;
import manager.xml.Status;
import manager.xml.Type;
import plugin.command.CommandPlugin;
import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.Map;

public class ShowVersionInfo extends CommandPlugin<Offshoot> {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    private static String PROP_LAST_RELEASE  = "lastRelease";
    private static String PROP_LAST_OFFSHOOT = "lastOffshoot";
    private static String PROP_RELEASE_STAT  = "releaseStatus";

    public ShowVersionInfo() {
        super(offshoot -> true);
    }

    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> params) {
        if (!offshoot.getRepository().isRepositoryOnline(true)) return;
        TES.executeTask(new CollectVersionData(offshoot));
    }

    @Override
    public boolean multiContextAllowed() {
        return false;
    }

    @Override
    public Kind getKind() {
        return Kind.Info;
    }

    class CollectVersionData extends AbstractTask<ParamModel> {

        private final Offshoot offshoot;
        CollectVersionData(Offshoot offshoot) {
            super(Language.get("title"));
            this.offshoot = offshoot;
        }

        @Override
        public ParamModel execute() throws Exception {
            ParamModel paramModel = new ParamModel();
            try {
                BranchDocument branch = getBranch(offshoot);
                BinarySource lastRelease = getLastRelease(branch);

                if (lastRelease instanceof Release) {
                    Release release = (Release) lastRelease;

                    paramModel.addProperty(PROP_RELEASE_STAT, new AnyType(), false);
                    paramModel.setValue(PROP_RELEASE_STAT, getReleaseStatus(release));

                    paramModel.addProperty(PROP_LAST_RELEASE, new AnyType(), false);
                    paramModel.setValue(PROP_LAST_RELEASE, lastRelease);

                } else {
                    paramModel.addProperty(PROP_LAST_OFFSHOOT, new AnyType(), false);
                    paramModel.setValue(PROP_LAST_OFFSHOOT, lastRelease);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return paramModel;
        }

        @Override
        public void finished(ParamModel paramModel) {
            SwingUtilities.invokeLater(() -> {
                new Dialog(
                        Dialog.findNearestWindow(),
                        ImageUtils.getByPath(Language.get(ShowVersionInfo.class, "icon")),
                        Language.get(ShowVersionInfo.class, "title"),
                        new EditorPage(paramModel),
                        null,
                        Dialog.Default.BTN_CLOSE.newInstance()
                ) {
                    @Override
                    public Dimension getPreferredSize() {
                        Dimension prefSize = super.getPreferredSize();
                        return new Dimension(650, prefSize.getSize().height);
                    }
                }.setVisible(true);
            });
        }

        private BranchDocument getBranch(Offshoot offshoot) throws Exception {
            InputStream fileStream = SVN.readFile(offshoot.getRemotePath(), "branch.xml", offshoot.getRepository().getAuthManager());
            return BranchDocument.Factory.parse(fileStream);
        }

        private ReleaseDocument getRelease(Release release) throws Exception {
            InputStream fileStream = SVN.readFile(release.getRemotePath(), "release.xml", release.getRepository().getAuthManager());
            return ReleaseDocument.Factory.parse(fileStream);
        }

        private BinarySource getLastRelease(BranchDocument branch) throws Exception {
            Type.Enum branchType = branch.getBranch().getType();
            BinarySource release;
            if (branchType == Type.DEV) {
                release = Entity.newPrototype(Offshoot.class);
                release.setTitle(branch.getBranch().getLastRelease());
            } else {
                release = Entity.newInstance(Release.class, offshoot.getRepository().toRef(), branch.getBranch().getLastRelease());
            }
            return release;
        }

        private ReleaseStatus getReleaseStatus(Release release) throws Exception {
            ReleaseDocument releaseDoc = getRelease(release);
            Status.Enum status = releaseDoc.getRelease().getStatus();
            if (status == Status.EXPIRED) {
                return ReleaseStatus.Expired;
            } else if (status == Status.TEST || status == Status.NEW) {
                return ReleaseStatus.Test;
            } else {
                return ReleaseStatus.Prod;
            }
        }
    }


    enum ReleaseStatus implements Iconified {
        Test,
        Prod,
        Expired;

        private final ImageIcon icon;
        private final String    name;
        ReleaseStatus() {
            icon = ImageUtils.getByPath(Language.get(ShowVersionInfo.class, this.name().toLowerCase()+"@icon"));
            name = Language.get(ShowVersionInfo.class, this.name().toLowerCase()+"@title");
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}