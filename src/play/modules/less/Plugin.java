package play.modules.less;

import java.io.PrintStream;

import play.Play;
import play.PlayPlugin;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.vfs.VirtualFile;


public class Plugin extends PlayPlugin {
    PlayLessEngine playLessEngine;
    
    @Override
    public void onApplicationStart() {
        playLessEngine = new PlayLessEngine(Play.mode == Play.Mode.DEV);
    }

    @Override
    public boolean serveStatic(VirtualFile file, Request request, Response response) {
    	if(file.getName().endsWith(".less")) {
    		response.contentType = "text/css";
    		try {
    		    // Compile the less file into CSS
                String css = playLessEngine.compile(file.getRealFile());
                response.status = 200;
                if(Play.mode == Play.Mode.PROD) {
                    response.cacheFor(Play.configuration.getProperty("http.cacheControl", "3600") + "s");
                }
                response.print(css);
            } catch(Exception e) {
                if(Play.mode.isDev()) {
                    throw new UnexpectedException(e);
                }
                
                response.status = 500;
                response.print("Bugger, the LESS processing failed:,\n");
                e.printStackTrace(new PrintStream(response.out));
            }
            return true;
        }

        return super.serveStatic(file, request, response);
    }
}
