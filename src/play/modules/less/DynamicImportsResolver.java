package play.modules.less;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
    static Pattern singleCommentPattern = Pattern.compile("//.*$", Pattern.MULTILINE);
    static Pattern multiCommentPattern = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    static Pattern importPattern = Pattern.compile("@import\\s+\"(.*)\"");

    Method createMethod;
    private boolean isDev;

    public DynamicImportsResolver(boolean isDev) {
        this.isDev = isDev;

        findCreateMethod();
        if (createMethod != null) {
            clearCache();
        }
    }

    private void findCreateMethod() {
        // Find the user-defined dynamic importer class
        List<ApplicationClass> classes = Play.classes.getAnnotatedClasses(DynamicLessCreator.class);

        // If there's more than one, it's an error
        if (classes.size() > 1) {
            String msg = "Only one class may have the @DynamicLessCreator annotation. Found "
                    + classes.size() + ":\n";
            for (ApplicationClass clazz : classes) {
                msg += clazz.name + "\n";
            }
            throw new UnexpectedException(msg);
        }

        // If the user has defined their own Dynamic Less Creator, use the
        // factory method in that class to create files dynamically
        if (classes.size() == 1) {
            ApplicationClass applicationClass = classes.get(0);
            Class<?> clazz = applicationClass.javaClass;

            // Find the method that creates the dynamic import content
            try {
                createMethod = clazz.getMethod("getLess", String.class);
                int modifiers = createMethod.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
                        || (!createMethod.getReturnType().isInstance(LessBlob.class))) {
                    throw new RuntimeException("");
                }
            } catch (Exception e) {
                throw new UnexpectedException("The class " + clazz.getCanonicalName()
                        + " with annotation @DynamicLessCreator must implement\n"
                        + "public static String getLess(String)");
            }

            return;
        }

        // If the user has not defined their own Dynamic Less Creator, check if
        // the user has defined any files in the themes directory. If so, use
        // the ThemeCreator
        if (ThemeCreator.themesDefined()) {
            try {
                createMethod = ThemeCreator.class.getMethod("getLess", String.class);
            } catch (Exception e) {
                // Should never happen
                throw new UnexpectedException(e);
            }
        }
    }

    private void clearCache() {
        File cacheDir = getCacheDir().getRealFile();
        deleteFile(cacheDir, cacheDir);
    }

    private void deleteFile(File originalFile, File file) {
        if (!file.exists()) {
            return;
        }

        // Sanity check
        String absolutePath = file.getAbsolutePath();
        if (absolutePath.length() < 10) {
            return;
        }

        // More sanity checking
        if (!absolutePath.startsWith(originalFile.getAbsolutePath())) {
            return;
        }

        // Delete children
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            for (File child : children) {
                deleteFile(originalFile, child);
            }
        }

        // Delete the file
        file.delete();
    }

    private File getRoot() {
        String stylesheetsDir = "/public/stylesheets";
        return Play.getFile(stylesheetsDir);
    }

    private VirtualFile getCacheDir() {
        return VirtualFile.open(getRoot().getAbsoluteFile() + "/play-less");
    }

    public File resolveImports(File file) {
        // Check if there are any dynamic imports to resolve
        if (createMethod == null && !isDev) {
            return null;
        }

        // Play does not parse the query string into request parameters for
        // static files, so we do it here manually, in case it's needed by the
        // dynamic file creators
        parseQueryString();

        // Create the output directory if it doesn't already exist
        createDir(getCacheDir().getRealFile().getAbsolutePath());

        Set<String> imports = new HashSet<String>();
        Set<String> dynamicImports = new HashSet<String>();
        return resolveImports(VirtualFile.open(file), imports, dynamicImports);
    }

    private File resolveImports(VirtualFile file, Set<String> imports, Set<String> dynamicImports) {

        // Remove comments
        String content = file.contentAsString();
        content = singleCommentPattern.matcher(content).replaceAll("");
        content = multiCommentPattern.matcher(content).replaceAll("");
        String parentNewPath = getNewPath(file.getRealFile().getAbsolutePath());

        // Find all import statements
        Matcher matcher = importPattern.matcher(content);
        int startIndex = 0;
        while (matcher.find(startIndex)) {
            String relativePath = matcher.group(1);
            String path = file.getRealFile().getParent() + "/" + relativePath;
            path = normalizePath(path);
            String newPath = getNewPath(path);

            // If it's a dynamic import, add it to the list
            if (relativePath.endsWith(DYNAMIC_EXT)) {
                if (createMethod == null) {
                    String msg = "Found @import statement with dynamic import " + "'"
                            + relativePath + "' in file '" + file.getRealFile().getAbsolutePath()
                            + "' " + "but did not find class with annotation @DynamicLessCreator. "
                            + "Create a class with the @DynamicLessCreator annotation.";
                    throw new UnexpectedException(msg);
                }

                if (!dynamicImports.contains(newPath)) {
                    try {
                        LessBlob importContent = (LessBlob) createMethod.invoke(null, path);
                        newPath = getPathWithKey(newPath, importContent.key);
                        writeContent(newPath, importContent.content);
                        dynamicImports.add(newPath);
                    } catch (Exception e) {
                        // Should never happen
                        throw new UnexpectedException(e);
                    }
                }
            } else {
                if (!imports.contains(newPath)) {
                    // If it's a normal import, check the imported file for more
                    // imports
                    VirtualFile virtualFile = VirtualFile.open(path);
                    if (!virtualFile.exists()) {
                        throw new UnexpectedException("Could not find import " + path);
                    }
                    resolveImports(virtualFile, imports, dynamicImports);
                }
            }

            // Replace the import statement with the new path
            String newImport = "@import \"" + getRelativePath(parentNewPath, newPath) + "\"";
            int start = matcher.start();
            content = content.substring(0, start) + newImport + content.substring(matcher.end());
            matcher.reset(content);
            startIndex = start + newImport.length();
        }

        VirtualFile newFile = writeContent(parentNewPath, content);
        imports.add(parentNewPath);
        return newFile.getRealFile();
    }

    private VirtualFile writeContent(String newPath, String importContent) {
        createParentDir(newPath);
        VirtualFile file = VirtualFile.open(newPath);
        if (!file.exists() || isDev) {
            file.write(importContent);
        }
        return file;
    }

    private String getNewPath(String path) {
        String rootPath = getRoot().getAbsolutePath();
        if (!path.startsWith(rootPath)) {
            throw new UnexpectedException("All imports must be inside the directory '" + rootPath
                    + "'. Found import that references file outside the root path: '" + path + "'");
        }

        return getCacheDir().getRealFile().getAbsolutePath() + path.substring(rootPath.length());
    }

    private String getPathWithKey(String newPath, String key) {
        int end = newPath.lastIndexOf(DYNAMIC_EXT);
        if (end == -1 || key.length() == 0) {
            return newPath;
        }

        return newPath.substring(0, end) + "-" + key + DYNAMIC_EXT;
    }

    public static String getRelativePath(String fromPath, String toPath) {
        String[] fromPartsWithName = fromPath.split("/");
        String[] fromParts = new String[fromPartsWithName.length - 1];
        System.arraycopy(fromPartsWithName, 0, fromParts, 0, fromParts.length);

        String[] toPartsWithName = toPath.split("/");
        String[] toParts = new String[toPartsWithName.length - 1];
        System.arraycopy(toPartsWithName, 0, toParts, 0, toParts.length);

        int diffIndex = getDifferenceIndex(fromParts, toParts);
        List<String> relativePath = new ArrayList<String>(fromParts.length + toParts.length);
        for (int i = diffIndex; i < fromParts.length; i++) {
            relativePath.add("..");
        }
        for (int i = diffIndex; i < toParts.length; i++) {
            relativePath.add(toParts[i]);
        }
        relativePath.add(toPartsWithName[toPartsWithName.length - 1]);

        return JavaExtensions.join(relativePath, "/");
    }

    private static int getDifferenceIndex(String[] fromParts, String[] toParts) {
        for (int i = 0; i < fromParts.length; i++) {
            if (i > toParts.length - 1) {
                return i;
            }
            if (!fromParts[i].equals(toParts[i])) {
                return i;
            }
        }
        return fromParts.length;
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

    private void createDir(String path) {
        File f = new File(path);
        if (!f.exists() && !f.mkdirs()) {
            throw new UnexpectedException("Could not create directory at " + path);
        }
    }

    private void createParentDir(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return;
        }

        String dirPath = path.substring(0, lastSlash);
        File f = new File(dirPath);
        if (!f.exists() && !f.mkdirs()) {
            throw new UnexpectedException("Could not create directory for " + path);
        }
    }

    public static void parseQueryString() {
        Request request = Request.current();
        Map<String, String[]> map = UrlEncodedParser.parse(request.querystring);
        for (String key : map.keySet()) {
            request.params.put(key, map.get(key));
        }
    }
}
