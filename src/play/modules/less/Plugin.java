package play.modules.less;

import java.io.PrintStream;
import java.util.Date;

import play.Play;
import play.PlayPlugin;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.utils.Utils;
import play.vfs.VirtualFile;

public class Plugin extends PlayPlugin {
    PlayLessEngine playLessEngine;
    boolean useEtag = true;

    @Override
    public void onLoad() {
        playLessEngine = new PlayLessEngine(Play.mode == Play.Mode.DEV);
        useEtag = Play.configuration.getProperty("http.useETag", "true").equals("true");
    }

    @Override
    public boolean serveStatic(VirtualFile file, Request request, Response response) {
        if (file.getName().endsWith(".less")) {
            response.contentType = "text/css";
            try {
                handleResponse(file, request, response);
            } catch (Exception e) {
                response.status = 500;
                response.print("Bugger, the LESS processing failed:,\n");
                e.printStackTrace(new PrintStream(response.out));
            }
            return true;
        }
        return false;
    }

    private void handleResponse(VirtualFile file, Request request, Response response) {
        long lastModified = playLessEngine.lastModifiedRecursive(file.getRealFile());
        final String etag = "\"" + lastModified + "-" + file.hashCode() + "\"";

        if (!request.isModified(etag, lastModified)) {
            handleNotModified(request, response, etag);
        } else {
            handleOk(request, response, file, etag, lastModified);
        }
    }

    private void handleNotModified(Request request, Response response, String etag) {
        if (request.method.equals("GET")) {
            response.status = Http.StatusCode.NOT_MODIFIED;
        }
        if (useEtag) {
            response.setHeader("ETag", etag);
        }
    }

    private void handleOk(Request request, Response response, VirtualFile file, String etag, long lastModified) {
        response.status = 200;
        response.print(playLessEngine.get(file.getRealFile()));
        response.setHeader("Last-Modified", Utils.getHttpDateFormatter().format(new Date(lastModified)));
        if (useEtag) {
            response.setHeader("ETag", etag);
        }
    }
}