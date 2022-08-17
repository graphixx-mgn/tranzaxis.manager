package plugin.versioninfo;

import codex.component.dialog.Dialog;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.presentation.SelectorPresentation;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.CancelException;
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
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShowVersionInfo extends CommandPlugin<Offshoot> {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    private static final String PROP_LAST_RELEASE  = "lastRelease";
    private static final String PROP_LAST_OFFSHOOT = "lastOffshoot";
    private static final String PROP_RELEASE_STAT  = "releaseStatus";
    private static final String PROP_BRANCH_CREATE = "branchCreated";
    private static final String PROP_RELEASE_INFO  = "releaseInfo";
    private static final String PARAM_RELEASE_INFO = "releaseRevisions";

    public ShowVersionInfo() {
        super(offshoot -> true);
        setParameters(
            new PropertyHolder<>(PARAM_RELEASE_INFO, new Bool(false), false)
        );
    }

    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> params) {
        if (!offshoot.getRepository().isRepositoryOnline(true)) return;
        TES.executeTask(new CollectVersionData(
                offshoot,
                params.get(PARAM_RELEASE_INFO).getValue() == Boolean.TRUE
        ));
    }

    @Override
    public boolean multiContextAllowed() {
        return false;
    }

    @Override
    public Kind getKind() {
        return Kind.Admin;
    }

    static class CollectVersionData extends AbstractTask<ParamModel> {

        private final Offshoot offshoot;
        private final boolean  loadReleaseRev;

        CollectVersionData(Offshoot offshoot, boolean loadReleaseRev) {
            super(MessageFormat.format(Language.get("title"), offshoot.getPID()));
            this.offshoot = offshoot;
            this.loadReleaseRev = loadReleaseRev;
        }

        @Override
        public ParamModel execute() {
            ParamModel paramModel = new ParamModel();
            try {
                setProgress(0, Language.get(ShowVersionInfo.class, "progress@file"));
                BranchDocument branch = getBranch(offshoot);
                setProgress(0, Language.get(ShowVersionInfo.class, "progress@last"));
                BinarySource lastRelease = getLastRelease(branch);

                if (lastRelease instanceof Release) {
                    Map<String, ReleaseInfo> releaseInfos = getReleaseInfos(branch);
                    List<ReleaseStatus> statuses = releaseInfos.values().stream()
                            .map(releaseInfo -> releaseInfo.status)
                            .collect(Collectors.toList());

                    paramModel.addProperty(PROP_RELEASE_STAT, new AnyType(
                            statuses.isEmpty() ? getReleaseStatus((Release) lastRelease) : (
                                statuses.contains(ReleaseStatus.Expired) ? ReleaseStatus.Expired : (
                                        statuses.contains(ReleaseStatus.Prod) ? ReleaseStatus.Prod : ReleaseStatus.Test
                                )
                            )
                    ), false);
                    paramModel.addProperty(PROP_LAST_RELEASE, new AnyType(lastRelease), false);

                    setProgress(100, Language.get(ShowVersionInfo.class, "progress@rev"));

                    paramModel.addProperty(PROP_BRANCH_CREATE, new AnyType(), false);
                    SVNRevision revision = SVN.getMinimalRevision(offshoot.getRemotePath(), offshoot.getRepository().getAuthManager());
                    Date date = SVN.info(offshoot.getRemotePath(), revision, true, offshoot.getRepository().getAuthManager()).getCommittedDate();
                    paramModel.setValue(PROP_BRANCH_CREATE, MessageFormat.format(
                            "{0} / {1}",
                            String.valueOf(revision.getNumber()),
                            Offshoot.DATE_FORMAT.format(date)
                    ));

                    paramModel.addProperty(
                            PROP_RELEASE_INFO,
                            new codex.type.Map<>(new Str(), new EntityRef<>(ReleaseInfo.class), releaseInfos),
                            false
                    );
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
            if (getStatus() == codex.task.Status.FINISHED) {
                SwingUtilities.invokeLater(() -> new Dialog(
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
                }.setVisible(true));
            }
        }

        private BranchDocument getBranch(Offshoot offshoot) throws Exception {
            InputStream fileStream = SVN.readFile(offshoot.getRemotePath(), "branch.xml", offshoot.getRepository().getAuthManager());
            return BranchDocument.Factory.parse(fileStream);
        }

        private ReleaseDocument getRelease(Release release) throws Exception {
            InputStream fileStream = SVN.readFile(release.getRemotePath(), "release.xml", release.getRepository().getAuthManager());
            return ReleaseDocument.Factory.parse(fileStream);
        }

        private BinarySource getLastRelease(BranchDocument branch) {
            Type.Enum branchType = branch.getBranch().getType();
            BinarySource release;
            if (branch.getBranch().getLastRelease() == null) {
                return Entity.newInstance(Release.class, offshoot.getRepository().toRef(), branch.getBranch().getBaseRelease());
            } else {
                if (branchType == Type.DEV) {
                    release = Entity.newPrototype(Offshoot.class);
                    release.setTitle(branch.getBranch().getLastRelease());
                } else {
                    release = Entity.newInstance(Release.class, offshoot.getRepository().toRef(), branch.getBranch().getLastRelease());
                }
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
            } else if (status == Status.INVALID) {
                return ReleaseStatus.Invalid;
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

                List<SVNDirEntry> entryList = SVN.list(svnUrl.toString(), offshoot.getRepository().getAuthManager()).stream()
                        .filter(svnDirEntry -> svnDirEntry.getName().startsWith(baseRelease))
                        .sorted(Comparator.comparing(
                                SVNDirEntry::getName,
                                BinarySource.VERSION_SORTER.reversed()
                        ))
                        .collect(Collectors.toList());

                for (SVNDirEntry svnDirEntry : entryList) {
                    if (isCancelled()) {
                        throw new CancelException();
                    }
                    final String releaseName = svnDirEntry.getName();
                    try {
                        setProgress(
                                (entryList.indexOf(svnDirEntry) + 1) * 100 / entryList.size(),
                                MessageFormat.format(
                                        Language.get(ShowVersionInfo.class, "progress@release"),
                                        releaseName
                                )
                        );
                        ReleaseStatus status = getReleaseStatus(Entity.newInstance(Release.class, offshoot.getRepository().toRef(), svnDirEntry.getName()));

                        if (loadReleaseRev) {
                            SVNRevision revision = SVN.getMinimalRevision(svnDirEntry.getURL().toString(), offshoot.getRepository().getAuthManager());
                            Date date = SVN.info(svnDirEntry.getURL().toString(), revision, true, offshoot.getRepository().getAuthManager()).getCommittedDate();
                            releases.put(
                                    releaseName,
                                    new ReleaseInfo(releaseName, status, new Revision(revision.getNumber(), date))
                            );
                        } else {
                            releases.put(
                                    releaseName,
                                    new ReleaseInfo(releaseName, status, null)
                            );
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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
        private final static String    PROP_STATUS   = "status";
        private final static String    PROP_REVISION = "revision";

        private final ReleaseStatus status;

        private ReleaseInfo(String title, ReleaseStatus status) {
            this(title, status, null);
        }

        private ReleaseInfo(String title, ReleaseStatus status, Revision revision) {
            super(null, ICON, title, null);
            this.status = status;

            // Properties
            model.addDynamicProp(PROP_STATUS, new AnyType(), null, () -> status);

            if (revision != null) {
                model.addDynamicProp(PROP_REVISION, new Str(revision.toString()), null, null);
            }
        }
    }


    private static class Revision {
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
                    Offshoot.DATE_FORMAT.format(date)
            );
        }
    }


    enum ReleaseStatus implements Iconified {
        Invalid,
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