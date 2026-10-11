package dev.anchorlight.stonelib.display;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayAnimatorTest {

    private static Vector3f apply(Transformation t, Vector3f p) {
        Vector3f v = new Vector3f(p);
        new Quaternionf(t.getRightRotation()).transform(v);
        v.mul(t.getScale());
        new Quaternionf(t.getLeftRotation()).transform(v);
        return v.add(t.getTranslation());
    }

    @Test
    void collapsedKeepsThePivotInPlace() {
        Transformation full = new Transformation(new Vector3f(1, 2, 3), new Quaternionf().rotateY(0.7f),
                new Vector3f(2, 0.5f, 4), new Quaternionf().rotateX(0.3f));
        Transformation collapsed = DisplayAnimator.collapsed(full, DisplayAnimator.BLOCK_CENTRE);

        Vector3f before = apply(full, DisplayAnimator.BLOCK_CENTRE);
        Vector3f after = apply(collapsed, DisplayAnimator.BLOCK_CENTRE);
        assertEquals(before.x, after.x, 1e-4);
        assertEquals(before.y, after.y, 1e-4);
        assertEquals(before.z, after.z, 1e-4);
        assertEquals(2 * DisplayAnimator.COLLAPSED_SCALE, collapsed.getScale().x, 1e-6);
    }

    @Test
    void collapsedAboutTheOriginOnlyShrinks() {
        Transformation full = new Transformation(new Vector3f(0, 1, 0), new Quaternionf(), new Vector3f(3, 3, 3), new Quaternionf());
        Transformation collapsed = DisplayAnimator.collapsed(full, DisplayAnimator.ORIGIN);
        assertEquals(new Vector3f(0, 1, 0), collapsed.getTranslation());
    }

    @Test
    void staggerSpreadsStartsEvenlyFromZeroToTheSpread() {
        assertEquals(0, DisplayAnimator.stagger(0, 5, 8));
        assertEquals(4, DisplayAnimator.stagger(2, 5, 8));
        assertEquals(8, DisplayAnimator.stagger(4, 5, 8));
        assertEquals(0, DisplayAnimator.stagger(3, 1, 8));
        assertEquals(0, DisplayAnimator.stagger(3, 5, 0));
    }

    @Test
    void totalTicksCoversTheLastStartPlusItsDuration() {
        assertEquals(19, DisplayAnimator.totalTicks(10, 8));
    }
}
