package codex.utils;

import net.jcip.annotations.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ClassVerificator {

    public static void main(String[] args) {
        File classDir = new File(args[0]);
        Set<Class<?>> allClasses = getClasses(classDir);
        if (allClasses.isEmpty()) return;

        Set<Class<?>> enums   = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();
        Set<Class<?>> ifaces  = new HashSet<>();
        Set<Class<?>> threadSafe = new HashSet<>();

        allClasses.forEach(aClass -> {
            if (!(
                    aClass.isAnnotation() ||
                    aClass.isMemberClass() ||
                    aClass.isAnonymousClass() ||
                    aClass.equals(ClassVerificator.class) ||
                    aClass.isAnnotationPresent(Deprecated.class)
            )) {
                if (aClass.isInterface()) {
                    ifaces.add(aClass);
                } else if (aClass.isEnum()) {
                    enums.add(aClass);
                } else {
                    try {
                        if (aClass.getAnnotation(ThreadSafe.class) != null) threadSafe.add(aClass);
                    } catch (Throwable e) {
                        System.err.println("Error class '"+aClass+"' processing: "+e.getMessage());
                    }
                    classes.add(aClass);
                }
            }
        });
        int safePercent = (threadSafe.size() * 100 / classes.size());
        System.out.println(MessageFormat.format("Class statistic:\n" +
                "Enums:       {0}\n" +
                "Interfaces:  {1}\n" +
                "Classes:     {2}\n" +
                "Thread safe: {3}% ({4} of {5})",

                enums.size(), ifaces.size(), classes.size(),
                safePercent, threadSafe.size(), classes.size()
        ));
        Map<String, List<Class>> byPackage = classes.stream()
                .filter(aClass -> !threadSafe.contains(aClass))
                .collect(Collectors.groupingBy(aClass -> aClass.getPackage().getName()));

        System.out.println(MessageFormat.format(
                "Unchecked classes:\n{0}",
                byPackage.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(entry -> MessageFormat.format(
                            "[{0}]\n{1}",
                            entry.getKey(),
                            entry.getValue().stream()
                                .map(aClass -> " * "+aClass.getTypeName())
                                .sorted()
                                .collect(Collectors.joining("\n"))
                    ))
                    .collect(Collectors.joining("\n"))
        ));
    }

    private static Set<Class<?>> getClasses(File classDir) {
        final Set<Class<?>> allClasses = new HashSet<>();
        if (classDir.exists()) {
            try {
                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{classDir.toURI().toURL()});
                Files.walkFileTree(Paths.get(classDir.getAbsolutePath()), new SimpleFileVisitor<Path>() {

                    private final Predicate<String> fileFilter = fileName ->
                            !fileName.startsWith("codex.xml") &&
                            !fileName.startsWith("schemaorg_apache_xmlbeans") &&
                            fileName.endsWith(".class");

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String fileName = file.toFile().getPath()
                                .substring(classDir.getPath().length()+1)
                                .replace(File.separator, ".");
                        if (fileFilter.test(fileName)) {
                            String className = fileName.substring(0, fileName.length()-6);
                            try {
                                allClasses.add(classLoader.loadClass(className));
                            } catch (ClassNotFoundException e) {
                                throw new Error(e);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return allClasses;
    }
}
