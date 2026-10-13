package com.aureliatransit.architecture.block;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A joined sign's outline drops the posts its model drops, so a row of signs has legs only at its two ends. */
class TextSignJoinTest {

	private static final double PX = 1 / 16.0;

	@BeforeAll
	static void boot() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	private static boolean solid(VoxelShape shape, double x, double y, double z) {
		return shape.getBoundingBoxes().stream().anyMatch(b -> b.contains(x * PX, y * PX, z * PX));
	}

	@Test
	void postsGoWhereTheModelDropsThem() {
		final VoxelShape standing = union(box(0, 7, 6.5, 16, 16, 9.5), box(1, 0, 7, 3, 7, 9), box(13, 0, 7, 15, 7, 9));
		final VoxelShape left = TextSignBlock.joined(standing, true, false);
		assertFalse(solid(left, 2, 3, 8), "left post gone");
		assertTrue(solid(left, 14, 3, 8), "right post kept");
		assertTrue(solid(left, 0.5, 12, 8), "panel kept to the edge");
		final VoxelShape both = TextSignBlock.joined(standing, true, true);
		assertFalse(solid(both, 2, 3, 8) || solid(both, 14, 3, 8), "middle block has no posts");
		final Box panel = both.getBoundingBox();
		assertEquals(0, panel.minX);
		assertEquals(1, panel.maxX);
		assertEquals(7 * PX, panel.minY, 1e-9);

		final VoxelShape hanging = union(box(0, 3, 6.5, 16, 11, 9.5), box(3, 11, 7, 5, 16, 9), box(11, 11, 7, 13, 16, 9));
		final VoxelShape right = TextSignBlock.joined(hanging, false, true);
		assertTrue(solid(right, 4, 13, 8), "left hanger kept");
		assertFalse(solid(right, 12, 13, 8), "right hanger gone");
		assertTrue(solid(TextSignBlock.joined(hanging, false, false), 12, 13, 8), "a lone sign keeps both");
	}
}
