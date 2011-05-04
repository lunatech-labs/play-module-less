package play.modules.less;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import play.Logger;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.vfs.VirtualFile;

public class ThemeCreator extends DynamicFileCreator {
    public static final String THEMES_DIR = "themes/";
    public static final String THEME_TOKEN = "// Theme: ";

    public static void createDynamicFile(String filePath) {
        VirtualFile dynamicFile = VirtualFile.open(filePath);

        String theme = Request.current().params.get("theme");
        theme = theme == null ? "" : theme;
        boolean isDefault = theme.length() == 0;

        // Get the file name from the file path
        String name = filePath.substring(filePath.lastIndexOf("/") + 1);
        // Remove the extension
        name = name.substring(0, name.lastIndexOf(DynamicImportsResolver.DYNAMIC_EXT));

        String themePart = "";
        if (!isDefault) {
            themePart = theme + ".";
        }

        // Get the path to the template file
        String templatePath = THEMES_DIR + name + "." + themePart + "less.html";
        VirtualFile templateFile = getTemplateFile(templatePath);
        if (templateFile == null || !templateFile.exists()) {
            String msg = "Could not find template for dynamic import '" + name + "'.\n";
            msg += "You must create a template file 'app/views/" + templatePath + "'.";
            if (isDefault) {
                msg += " If you want to customize the theme, use the #{less.theme} tag.";
            }
            throw new UnexpectedException(msg);
        }

        // If the template file has not changed since the dynamic file was
        // generated, then there's no need to regenerate the dynamic file
        if (dynamicFile.exists() && theme.equals(getTheme(dynamicFile))
                && (templateFile.getRealFile().lastModified() < dynamicFile.lastModified())) {
            Logger.trace("Template file %s not modified since dynamic file %s generated",
                    templatePath, filePath);
            return;
        }

        if (!dynamicFile.exists()) {
            Logger.trace("Dynamic file %s does not yet exist, generating", filePath);
        } else {
            Logger.trace("Template file %s has been modified since dynamic file %s generated, "
                    + "generating dynamic file again", templatePath, filePath);
        }

        // Generate the dynamic file from the template
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("theme", theme);
        String dynamicContent = renderTemplate(templatePath, args);
        dynamicFile.write(THEME_TOKEN + theme + "\n" + dynamicContent);

        Logger.trace("Completed generation of dynamic file %s", filePath);
    }

    public static boolean themesDefined() {
        VirtualFile themesDir = VirtualFile.open("app/views/" + THEMES_DIR);
        if (!themesDir.exists()) {
            return false;
        }
        return themesDir.getRealFile().list().length > 0;
    }

    public static String getTheme(VirtualFile dynamicFile) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(dynamicFile.inputstream()));
        try {
            String line = reader.readLine();
            if (line.startsWith(THEME_TOKEN)) {
                return line.substring(THEME_TOKEN.length());
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        return null;
    }
}
