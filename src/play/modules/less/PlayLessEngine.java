package play.modules.less;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	Pattern importPattern = Pattern.compile(".*@import\\s*\"(.*?)\".*");
	
    PlayLessEngine(Boolean devMode) {
        lessEngine = new LessEngine();
        this.devMode = devMode; 
    }
    
    /**
     * Get the CSS for this less file either from the cache, or compile it.
     */
    public String get(File lessFile) {
        String cacheKey = "less_" + lessFile.getPath() + lastModifiedRecursive(lessFile);
        String css = Cache.get(cacheKey, String.class);
        if(css == null) {
            css = compile(lessFile);
            Cache.set(cacheKey, css);
        }
        return css;
    }
    
    // TODO: Maybe prevent infinite looping here, in case of an import loop?
    public long lastModifiedRecursive(File lessFile) {
        long lastModified = lessFile.lastModified();
        for(File imported : getImportsFromCacheOrFile(lessFile)) {
            lastModified = Math.max(lastModified, imported.lastModified());
        }
        return lastModified;
    }
    
    protected Set<File> getImportsFromCacheOrFile(File lessFile) {
        String cacheKey = "less_imports_" + lessFile.getPath() + lessFile.lastModified();
        
        @SuppressWarnings("unchecked")
        Set<File> files = Cache.get(cacheKey, Set.class);
        
        if(files == null) {
            try {
                files = getImportsFromFile(lessFile);
                Cache.set(cacheKey, files);
            } catch(IOException e) {
                Logger.error(e, "IOException trying to determine imports in LESS file");
                files = new HashSet<File>();
            }
        }
        return files;
    }
    
    protected Set<File> getImportsFromFile(File lessFile) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(lessFile));

        Set<File> files = new HashSet<File>();
        String line;
        while ((line = r.readLine()) != null) {
          Matcher m = importPattern.matcher(line);
          while (m.find()) {
              File file = new File(lessFile.getParentFile(), m.group(1));
              files.add(file);
              files.addAll(getImportsFromCacheOrFile(file));
          }
        }
        return files;
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
