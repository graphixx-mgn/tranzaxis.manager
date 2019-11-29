package plugin;

import codex.component.dialog.Dialog;
import codex.explorer.browser.BrowseMode;
import codex.mask.DateFormat;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.presentation.SelectorPresentation;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.*;
import manager.svn.SVN;
import manager.xml.BranchDocument;
import manager.xml.ReleaseDocument;
import manager.xml.Status;
import manager.xml.Type;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import plugin.command.CommandPlugin;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class ShowVersionInfo extends CommandPlugin<Offshoot> {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    private static String PROP_LAST_RELEASE  = "lastRelease";
    private static String PROP_LAST_OFFSHOOT = "lastOffshoot";
    private static String PROP_RELEASE_STAT  = "releaseStatus";
    private static String PROP_BRANCH_CREATE = "branchCreated";
    private static String PROP_RELEASE_INFO  = "releaseInfo";

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

                    paramModel.addProperty(PROP_BRANCH_CREATE, new AnyType(), false);
                    SVNRevision revision = SVN.getMinimalRevision(offshoot.getRemotePath(), offshoot.getRepository().getAuthManager());
                    Date date = SVN.info(offshoot.getRemotePath(), revision, true, offshoot.getRepository().getAuthManager()).getCommittedDate();
                    paramModel.setValue(PROP_BRANCH_CREATE, MessageFormat.format(
                            "{0} / {1}",
                            String.valueOf(revision.getNumber()),
                            DateFormat.Date.newInstance().getFormat().format(date)
                    ));

                    paramModel.addProperty(PROP_RELEASE_INFO, new codex.type.Map<>(
                            Str.class,
                            new EntityRef<ReleaseInfo>(ReleaseInfo.class){}.getClass(),
                            getReleaseInfos(branch)
                    ), false);
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
        @SuppressWarnings("unchecked")
        public void finished(ParamModel paramModel) {
            SwingUtilities.invokeLater(() -> {
                new Dialog(
                        Dialog.findNearestWindow(),
                        ImageUtils.getByPath(Language.get(ShowVersionInfo.class, "icon")),
                        Language.get(ShowVersionInfo.class, "title"),
                        new JPanel(new BorderLayout()) {{
                            // Common properties
                            EditorPage page = new EditorPage(paramModel);
                            add(page, BorderLayout.NORTH);

                            // Releases
                            if (paramModel.hasProperty(PROP_RELEASE_INFO)) {
                                paramModel.getEditor(PROP_RELEASE_INFO).setVisible(false);
                                Map<String, ReleaseInfo> releases = ((Map<String, ReleaseInfo>) paramModel.getValue(PROP_RELEASE_INFO));
                                if (!releases.isEmpty()) {
                                    ReleaseCatalog catalog = new ReleaseCatalog();
                                    releases.forEach((version, releaseInfo) -> catalog.attach(releaseInfo));

                                    SelectorPresentation releasesView = catalog.getSelectorPresentation();
                                    if (releasesView != null) {
                                        releasesView.setBorder(new TitledBorder(
                                                new LineBorder(Color.GRAY, 1),
                                                Language.get(ShowVersionInfo.class, "releases@title")
                                        ));
                                        add(catalog.getSelectorPresentation(), BorderLayout.CENTER);
                                    }
                                }
                            }
                        }},
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

        private Map<String, ReleaseInfo> getReleaseInfos(BranchDocument branch) throws SVNException {
            Map<String, ReleaseInfo> releases = new LinkedHashMap<>();
            Type.Enum branchType = branch.getBranch().getType();
            if (branchType == Type.OFFSHOOT) {
                String baseRelease = branch.getBranch().getBaseRelease();
                String releaseDir = ReleaseList.class.getAnnotation(RepositoryBranch.Branch.class).remoteDir();
                SVNURL svnUrl = SVNURL.parseURIEncoded(offshoot.getRepository().getRepoUrl())
                        .appendPath(releaseDir, false);

                SVN.list(svnUrl.toString(), offshoot.getRepository().getAuthManager()).stream()
                        .filter(svnDirEntry -> svnDirEntry.getName().startsWith(baseRelease))
                        .sorted(Comparator.comparing(
                                SVNDirEntry::getName,
                                BinarySource.VERSION_SORTER.reversed()
                        ))
                        .forEach(svnDirEntry -> releases.put(
                                svnDirEntry.getName(),
                                new ReleaseInfo(
                                        svnDirEntry.getName(),
                                        new Revision(svnDirEntry.getRevision(), svnDirEntry.getDate())
                                )
                        ));
            }
            return releases;
        }
    }


    private static class ReleaseCatalog extends Catalog {

        private ReleaseCatalog() {
            super(null, null, null, null);
        }

        @Override
        public Class<? extends Entity> getChildClass() {
            return ReleaseInfo.class;
        }

        @Override
        public boolean allowModifyChild() {
            return false;
        }
    }


    private static class ReleaseInfo extends Catalog {
        private final static ImageIcon ICON = ImageUtils.getByPath("/images/release.png");
        private final static String    PROP_REVISION = "revision";

        private ReleaseInfo(String title, Revision revision) {
            super(null, ICON, title, null);

            // Properties
            model.addDynamicProp(PROP_REVISION, new Str(revision.toString()), null, null);
        }
    }


    private class Revision {
        private final long revision;
        private final Date date;

        private Revision(long revision, Date date) {
            this.revision = revision;
            this.date = date;
        }

        @Override
        public String toString() {
            return MessageFormat.format(
                    "{0} / {1}",
                    String.valueOf(revision),
                    DateFormat.Full.newInstance().getFormat().format(date)
            );
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