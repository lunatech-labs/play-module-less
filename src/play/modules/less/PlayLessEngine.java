package play.modules.less;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;
import org.mozilla.javascript.WrappedException;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;

import play.Logger;
import play.cache.Cache;

/**
 * LessEngine wrapper for Play
 */
public class PlayLessEngine {

	LessEngine lessEngine;
	Boolean devMode;
	
    PlayLessEngine(Boolean devMode) {
        lessEngine = new LessEngine();
        this.devMode = devMode; 
    }
    
    /**
     * Get the CSS for this less file either from the cache, or compile it.
     */
    public String get(File lessFile) {
        String cacheKey = "less_" + lessFile.getPath() + lessFile.lastModified();
        String css = (String) Cache.get(cacheKey);
        if(css == null) {
            css = compile(lessFile);
            Cache.set(cacheKey, css);
        }
        return css;
    }

    protected String compile(File lessFile) {
    	try {
			return lessEngine.compile(lessFile);
		} catch (LessException e) {
			return handleException(lessFile, e);
		}
    }
    
    public String handleException(File lessFile, LessException e) {
    	Logger.warn(e, "Less exception");
    	
    	String filename = e.getFilename();
    	List<String> extractList = e.getExtract();
    	String extract = null;
    	if(extractList != null) {
    		extract = extractList.toString();
    	}
    	
    	// LessEngine reports the file as null when it's not an @imported file
    	if(filename == null) {
    		filename = lessFile.getName(); 
    	}
    	
    	// Try to detect missing imports (flaky)
    	if(extract == null && e.getCause() instanceof WrappedException) {
    		WrappedException we = (WrappedException) e.getCause();
    		if(we.getCause() instanceof FileNotFoundException) {
    			FileNotFoundException fnfe = (FileNotFoundException) we.getCause();
    			extract = fnfe.getMessage();
    		}
    	}
    	
    	return formatMessage(filename, e.getLine(), e.getColumn(), extract, e.getErrorType());
    }
    
    public String formatMessage(String filename, int line, int column, String extract, String errorType) {
    	return "body:before {display: block; color: #c00; white-space: pre; font-family: monospace; background: #FDD9E1; border-top: 1px solid pink; border-bottom: 1px solid pink; padding: 10px; content: \"[LESS ERROR] " +
    	String.format("%s:%s: %s (%s)", filename, line, extract, errorType)	+				
    	"\"; }";
    }
}
