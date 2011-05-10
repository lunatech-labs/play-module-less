package play.modules.less;

import java.util.HashMap;
import java.util.Map;

import play.Play;
import play.cache.Cache;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.vfs.VirtualFile;

public class ThemeCreator extends AbstractDynamicLessCreator {
    public static final String THEMES_DIR = "themes/";
    public static final String THEME_TOKEN = "// Theme: ";
    private static final String CACHE_LIFETIME = "3650d";

    public static LessBlob getLess(String filePath) {
        // Get the theme from the request
        String theme = Request.current().params.get("theme");
        theme = theme == null ? "" : theme;
        boolean isDefault = theme.length() == 0;

        // Check the cache for the dynamic file
        String cacheKey = "less-cache-" + theme + "-" + filePath;
        String content = (String) Cache.get(cacheKey);
        if (content != null && !Play.mode.isDev()) {
            return new LessBlob(theme, content);
        }

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

        // Generate the dynamic content from the template
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("theme", theme);
        String dynamicContent = renderTemplate(templatePath, args);

        // Save it to the cache
        Cache.safeSet(cacheKey, dynamicContent, CACHE_LIFETIME);

        return new LessBlob(theme, dynamicContent);
    }

    public static boolean themesDefined() {
        VirtualFile themesDir = VirtualFile.open("app/views/" + THEMES_DIR);
        if (!themesDir.exists()) {
            return false;
        }
        return themesDir.getRealFile().list().length > 0;
    }
}
