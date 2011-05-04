package play.modules.less;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.parsing.UrlEncodedParser;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.templates.JavaExtensions;
import play.vfs.VirtualFile;

public class DynamicImportsResolver {
    public static final String DYNAMIC_EXT = ".play.less";
    Method createMethod;
    private boolean isDev;

    public DynamicImportsResolver(boolean isDev) {
        this.isDev = isDev;

        // Find the user-defined dynamic importer class
        List<ApplicationClass> classes = Play.classes
                .getAnnotatedClasses(LessDynamicFileCreator.class);

        // If there's more than one, it's an error
        if (classes.size() > 1) {
            String msg = "Only one class may have the @LessDynamicFileCreator annotation. Found "
                    + classes.size() + ":\n";
            for (ApplicationClass clazz : classes) {
                msg += clazz.name + "\n";
            }
            throw new UnexpectedException(msg);
        }

        // If the user has defined their own Dynamic File Creator, use the
        // factory method in that class to create files dynamically
        if (classes.size() == 1) {
            ApplicationClass applicationClass = classes.get(0);
            Class<?> clazz = applicationClass.javaClass;

            // Find the method that creates the dynamic import content
            try {
                createMethod = clazz.getMethod("createDynamicFile", String.class);
                int modifiers = createMethod.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                    throw new RuntimeException("");
                }
            } catch (Exception e) {
                throw new UnexpectedException("The class " + clazz.getCanonicalName()
                        + " with annotation @LessDynamicFileCreator must implement\n"
                        + "public static void createDynamicFile(String)");
            }

            return;
        }

        // If the user has not defined their own Dynamic File Creator, check if
        // the user has defined any files in the themes directory. If so, use
        // the ThemeCreator
        if (ThemeCreator.themesDefined()) {
            try {
                createMethod = ThemeCreator.class.getMethod("createDynamicFile", String.class);
            } catch (Exception e) {
                // Should never happen
                throw new UnexpectedException(e);
            }
        }
    }

    /**
     * Creates the files on the file system that are imported dynamically
     */
    public void createDynamicImports(VirtualFile file) {
        // Check if there are any dynamic imports to resolve
        if (createMethod == null && !isDev) {
            return;
        }

        Set<String> imports = new HashSet<String>();
        Set<String> dynamicImports = new HashSet<String>();
        getDynamicImports(file, imports, dynamicImports);

        if (createMethod == null && dynamicImports.size() > 0) {
            throw new UnexpectedException("Found @import statement with dynamic import "
                    + "(an @import file with the extension '" + DYNAMIC_EXT + "'):\n"
                    + dynamicImports.toArray()[0] + "\n"
                    + "but did not find class with annotation @LessDynamicFileCreator. "
                    + "Create a class with the @LessDynamicFileCreator annotation.");
        }

        // Play does not parse the query string into request parameters for
        // static files, so we do it here manually
        parseQueryString();

        try {
            for (String filePath : dynamicImports) {
                createMethod.invoke(null, filePath);
            }
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    static Pattern singleCommentPattern = Pattern.compile("//.*$", Pattern.MULTILINE);
    static Pattern multiCommentPattern = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    static Pattern importPattern = Pattern.compile("@import\\s+\"(.*)\"");

    private static void getDynamicImports(VirtualFile file, Set<String> imports,
            Set<String> dynamicImports) {

        // Remove comments
        String content = file.contentAsString().replace("", "");
        content = singleCommentPattern.matcher(content).replaceAll("");
        content = multiCommentPattern.matcher(content).replaceAll("");

        // Find all import statements
        Matcher matcher = importPattern.matcher(content);
        while (matcher.find()) {
            String fileName = matcher.group(1);
            String path = file.getRealFile().getParent() + "/" + fileName;
            path = normalizePath(path);

            // If it's a dynamic import, add it to the list
            if (fileName.endsWith(DYNAMIC_EXT)) {
                dynamicImports.add(path);
            } else if (!imports.contains(path)) {
                // If it's a normal import, check the imported file for more
                // imports
                VirtualFile virtualFile = VirtualFile.open(path);
                if (!virtualFile.exists()) {
                    throw new UnexpectedException("Could not find import " + path);
                }
                getDynamicImports(virtualFile, imports, dynamicImports);
            }
        }
    }

    public static String normalizePath(String path) {
        Stack<String> normalized = new Stack<String>();
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.equals("..")) {
                try {
                    normalized.pop();
                } catch (EmptyStackException e) {
                    throw new UnexpectedException("Bad path in import statement: " + path);
                }
            } else if (part.length() > 0 && !part.equals(".")) {
                normalized.push(part);
            }
        }

        return (path.charAt(0) == '/' ? "/" : "") + JavaExtensions.join(normalized, "/");
    }

    public static void parseQueryString() {
        Request request = Request.current();
        Map<String, String[]> map = UrlEncodedParser.parse(request.querystring);
        for (String key : map.keySet()) {
            request.params.put(key, map.get(key));
        }
    }
}
