import org.junit.Test;

import play.modules.less.DynamicImportsResolver;
import play.test.UnitTest;
import play.exceptions.UnexpectedException;

public class RelativePathTest extends UnitTest {

    @Test
    public void noChange() {
        assertEquals("file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1/dir2/file.ext"));
    }

    @Test
    public void oneUp() {
        assertEquals("../file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1/file.ext"));
    }

    @Test
    public void twoUp() {
        assertEquals("../../file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/file.ext"));
    }

    @Test
    public void oneDown() {
        assertEquals("dir3/file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1/dir2/dir3/file.ext"));
    }

    @Test
    public void twoDown() {
        assertEquals("dir3/dir4/file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1/dir2/dir3/dir4/file.ext"));
    }

    @Test
    public void oneDownOneUp() {
        assertEquals("../dir2b/file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1/dir2b/file.ext"));
    }
    
    @Test
    public void oneDownTwoUp() {
        assertEquals("../dir2b/dir3b/file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1/dir2b/dir3b/file.ext"));
    }

    @Test
    public void twoDownOneUp() {
        assertEquals("../../dir1b/file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1b/file.ext"));
    }

    @Test
    public void twoDownTwoUp() {
        assertEquals("../../dir1b/dir2b/file.ext", DynamicImportsResolver.getRelativePath("/dir1/dir2/file.ext", "/dir1b/dir2b/file.ext"));
    }
}
