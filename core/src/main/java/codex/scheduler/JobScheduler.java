package codex.scheduler;

import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.type.Enum;
import codex.type.Iconified;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class JobScheduler extends AbstractUnit {

    private static final JobScheduler INSTANCE = new JobScheduler();
    public  static JobScheduler getInstance() {
        return INSTANCE;
    }

    private ExplorerUnit explorer;
    private JobCatalog   jobCatalog = new JobCatalog();

    private JobScheduler() {
        Logger.getLogger().debug("Initialize unit: Task Scheduler");
        try {
            Constructor ctor = ExplorerUnit.class.getDeclaredConstructor(BrowseMode.class);
            ctor.setAccessible(true);
            explorer = (ExplorerUnit) ctor.newInstance(new EmbeddedMode());
            explorer.createViewport();

            Field navigatorField = ExplorerUnit.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);

            Navigator navigator = (Navigator) navigatorField.get(explorer);
            navigator.setModel(new NodeTreeModel(jobCatalog));
        } catch (Exception e) {
            //
        }
        jobCatalog.loadChildren();
    }

    @Override
    public JComponent createViewport() {
        return explorer.getViewport();
    }

    @Override
    public void viewportBound() {
        explorer.viewportBound();
    }


    enum ScheduleKind implements Iconified {

        @Enum.Undefined
        Undefined(null),

        Timer(ImageUtils.getByPath("/images/timer.png")),
        Daily(ImageUtils.getByPath("/images/daily.png"))
        ;

        private final ImageIcon icon;
        private final String    title;
        private final String    format;

        ScheduleKind(ImageIcon icon) {
            this.icon   = icon;
            this.title  = Language.get(JobScheduler.class, "kind@"+name().toLowerCase());
            this.format = Language.get(JobScheduler.class, "kind@"+name().toLowerCase()+"@format");
        }

        String getFormat() {
            return equals(Undefined) ? Language.NOT_FOUND : format;
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }
    }


    enum JobStatus implements Iconified {
        @Enum.Undefined
        Undefined(new ImageIcon()),
        Finished(ImageUtils.getByPath("/images/success.png")),
        Canceled(ImageUtils.getByPath("/images/cancel.png")),
        Failed(ImageUtils.getByPath("/images/stop.png")),
        ;

        private final String    title;
        private final ImageIcon icon;

        JobStatus(ImageIcon icon) {
            this.icon   = icon;
            this.title  = Language.get(JobScheduler.class, "status@"+name().toLowerCase());
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }
    }
}