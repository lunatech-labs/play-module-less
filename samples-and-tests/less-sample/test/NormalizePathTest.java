import org.junit.Test;

import play.modules.less.DynamicImportsResolver;
import play.test.UnitTest;
import play.exceptions.UnexpectedException;

public class NormalizePathTest extends UnitTest {

    @Test
    public void noChange() {
        assertEquals("/dir1/dir2/file.ext", DynamicImportsResolver.normalizePath("/dir1/dir2/file.ext"));
    }

    @Test
    public void dots() {
        assertEquals("/dir1/dir2/file.ext", DynamicImportsResolver.normalizePath("/dir1/dir2/dir3/../file.ext"));
    }

    @Test
    public void dot() {
        assertEquals("/dir1/dir2/file.ext", DynamicImportsResolver.normalizePath("/dir1/dir2/./file.ext"));
    }
    
    @Test
    public void mix() {
        assertEquals("/dir1/dir2/file.ext", DynamicImportsResolver.normalizePath("/dir1/dir2/./../dir3/../dir2/./file.ext"));
    }
    
    @Test(expected=UnexpectedException.class)
    public void badPath() {
        DynamicImportsResolver.normalizePath("/dir1/../../file.ext");
    }
}
