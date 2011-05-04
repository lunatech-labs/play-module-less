package play.modules.less;

import java.util.HashMap;
import java.util.Map;

import play.Play;
import play.mvc.Scope;
import play.mvc.Http.Request;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

public abstract class DynamicFileCreator {

    public static VirtualFile getTemplateFile(String templatePath) {
        for (VirtualFile vf : Play.templatesPath) {
            if (vf != null) {
                VirtualFile tf = vf.child(templatePath);
                if (tf.exists()) {
                    return tf;
                }
            }
        }
        return null;
    }

    public static String renderTemplate(String path, Map<String, Object> args) {
        Template template = TemplateLoader.load(path);
        Map<String, Object> allArgs = new HashMap<String, Object>(args);
        allArgs.put("request", Request.current());
        allArgs.put("session", Scope.Session.current());
        allArgs.put("params", Scope.Params.current());

        return template.render(args);
    }

}