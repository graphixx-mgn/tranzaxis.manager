package manager.commands.build;

import codex.utils.Language;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.StringJoiner;
import java.util.prefs.Preferences;
import manager.Manager;
import manager.commands.BuildWC;
import manager.type.Locale;
import manager.xml.FilelistDocument;
import manager.xml.ProjectDocument;
import manager.xml.PropertyDocument;
import manager.xml.SubantDocument;
import manager.xml.TargetDocument;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.radixware.kernel.common.repository.Branch;
import org.radixware.kernel.common.repository.Layer;

public class KernelBuilder {
    
    public static void main(String[] args) {
        Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
        if (prefs.get("guiLang", null) != null) {
            Locale localeEnum = Locale.valueOf(prefs.get("guiLang", null));
            java.lang.System.setProperty("user.language", localeEnum.getLocale().getLanguage());
            java.lang.System.setProperty("user.country",  localeEnum.getLocale().getCountry());
        }
        
        try {
            Registry reg = LocateRegistry.getRegistry(BuildWC.RMI_PORT);
            IBuildingNotifier notifier = (IBuildingNotifier) reg.lookup(BuildingNotifier.class.getCanonicalName());
            
            try {
                ProjectDocument projectDoc = ProjectDocument.Factory.newInstance();
                ProjectDocument.Project project = projectDoc.addNewProject();

                project.setName("build");
                project.setBasedir(".");
                project.setDefault(ProjectDocument.Project.Default.DISTRIBUTIVE);

                PropertyDocument.Property property = project.addNewProperty();
                property.setName("modules");
                File localDir = new File(args[1]);

                StringJoiner kernels = new StringJoiner("\n");
                Branch branch = Branch.Factory.loadFromDir(localDir);
                for (Layer layer : branch.getLayers()) {
                    if (layer.getKernel().getDirectory().exists() && !layer.isReadOnly()) {
                        kernels.add(layer.getDirectory().getName()+"/kernel/build.xml");
                    }
                }
                if (kernels.length() == 0) {
                    return;
                }
                property.setValue(kernels.toString());

                TargetDocument.Target targetClean = project.addNewTarget();
                targetClean.setName(ProjectDocument.Project.Default.CLEAN.toString());

                SubantDocument.Subant subAntClean = targetClean.addNewSubant();
                subAntClean.addNewTarget().setName(targetClean.getName());

                FilelistDocument.Filelist files = subAntClean.addNewFilelist();
                files.setDir(".");
                files.setFiles("${modules}");

                TargetDocument.Target targetBuild = project.addNewTarget();
                targetBuild.setName(ProjectDocument.Project.Default.DISTRIBUTIVE.toString());

                SubantDocument.Subant subAntBuild = targetBuild.addNewSubant();
                subAntBuild.addNewTarget().setName(targetBuild.getName());
                subAntBuild.setFilelist((FilelistDocument.Filelist) files.copy());

                File buildFile = new File(localDir.getPath()+File.separator+"build-kernel.xml");
                FileUtils.writeStringToFile(buildFile, projectDoc.toString(), StandardCharsets.UTF_8);

                File logFile = new File(localDir.getPath()+File.separator+"build-kernel.log");
                PrintStream logStream = new PrintStream(logFile);
                BuildLogger consoleLogger = new DefaultLogger() {
                    @Override
                    public void taskStarted(BuildEvent event) {
                        super.taskStarted(event);
                        try {
                            notifier.checkPaused(args[0]);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    
                };
                consoleLogger.setErrorPrintStream(logStream);
                consoleLogger.setOutputPrintStream(logStream);
                consoleLogger.setMessageOutputLevel(Project.MSG_INFO);

                Project ant = new Project();
                ant.setUserProperty("ant.file", buildFile.getAbsolutePath());
                ant.addBuildListener(consoleLogger);

                ProjectHelper projectHelper = ProjectHelper.getProjectHelper();
                ant.addReference("ant.projectHelper", projectHelper);
                projectHelper.parse(ant, buildFile);
                ant.init();

                try {
                    ant.fireBuildStarted();
                    
                    notifier.setStatus(args[0], Language.get(BuildWC.class.getSimpleName(), "command@clean"));
                    ant.executeTarget(targetClean.getName());
                    
                    notifier.setStatus(args[0], Language.get(BuildWC.class.getSimpleName(), "command@distributive"));
                    ant.executeTarget(targetBuild.getName());
                    
                    ant.fireBuildFinished(null);
                } catch (BuildException e) {
                    ant.fireBuildFinished(e);
                    notifier.failed(args[0], e);
                    return;
                }            
            } catch (IOException e) {
                notifier.failed(args[0], e);
                return;
            }
            notifier.finished(args[0]);
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }
    
}
