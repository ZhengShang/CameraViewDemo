package cn.zhengshang.cameraview;

import org.junit.Test;

import java.util.HashSet;

import cn.zhengshang.base.Size;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SizeTest {

    @Test
    public void testGetters() {
        Size size = new Size(1, 2);
        assertThat(size.getWidth(), is(1));
        assertThat(size.getHeight(), is(2));
    }

    @Test
    public void testToString() {
        Size size = new Size(1, 2);
        assertThat(size.toString(), is("1x2"));
    }

    @Test
    public void testEquals() {
        Size a = new Size(1, 2);
        Size b = new Size(1, 2);
        Size c = new Size(3, 4);
        assertThat(a.equals(b), is(true));
        assertThat(a.equals(c), is(false));
    }

    @Test
    public void testHashCode() {
        int max = 100;
        HashSet<Integer> codes = new HashSet<>();
        for (int x = 1; x <= max; x++) {
            for (int y = 1; y <= max; y++) {
                codes.add(new Size(x, y).hashCode());
            }
        }
        assertThat(codes.size(), is(max * max));
    }

}
