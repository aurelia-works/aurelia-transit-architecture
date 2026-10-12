package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.live.DisplayKind;
import com.aureliatransit.architecture.live.PidsBlock;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.aureliatransit.architecture.util.Shapes.box;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Turned and nudged placement of screens and signs: placement, joins, shapes, rotation and mirroring. */
class FreePlacementTest {

	private static PidsBlock pids;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		pids = new PidsBlock(AbstractBlock.Settings.create().nonOpaque(), DisplayKind.PIDS, true, 6.5F, 3, box(0, 0, 6, 16, 16, 10));
	}

	/** The front direction in degrees clockwise from north, seen from above. */
	private static float front(BlockState state) {
		final Direction facing = state.get(FacingShapedBlock.FACING);
		return (facing.asRotation() + 180F + FreePlacement.degrees(state)) % 360F;
	}

	@Test
	void oldStatesLoadSquareOn() {
		final BlockState state = pids.getDefaultState();
		assertEquals(0, state.get(FreePlacement.ANGLE));
		assertFalse(FreePlacement.moved(state));
		assertEquals(FreePlacement.Nudge.NONE, state.get(FreePlacement.OFFSET));
		assertEquals(Direction.NORTH, state.get(FacingShapedBlock.FACING));
	}

	@Test
	void sneakPlacingFacesThePlayerInSixteenSteps() {
		final Set<Float> fronts = new HashSet<>();
		for (int step = 0; step < 16; step++) {
			final float yaw = step * 22.5F + 3F; // a little off, so it has to round
			final BlockState state = FreePlacement.place(pids.getDefaultState(), yaw, Vec3d.ZERO, Direction.UP, true);
			// the front points back at the player: opposite the way they look (yaw 0 looks south, +90 west)
			final float looking = (yaw + 180F) % 360F; // clockwise from north
			final float expected = Math.round(((looking + 180F) % 360F) / 22.5F) * 22.5F % 360F;
			assertEquals(expected, front(state), 0.01F, "yaw " + yaw);
			fronts.add(front(state));
		}
		assertEquals(16, fronts.size());
	}

	@Test
	void aTurnedScreenStandsAlone() {
		final BlockState square = pids.getDefaultState();
		final BlockState turned = square.with(FreePlacement.ANGLE, 1);
		assertTrue(FreePlacement.joinable(square, square));
		assertFalse(FreePlacement.joinable(square, turned));
		assertFalse(FreePlacement.joinable(turned, turned));
	}

	@Test
	void aTurnedShapeStaysInsideItsBlock() {
		for (final Direction facing : Direction.Type.HORIZONTAL) {
			for (int angle = 0; angle < 4; angle++) {
				final BlockState state = pids.getDefaultState().with(FacingShapedBlock.FACING, facing).with(FreePlacement.ANGLE, angle);
				final Box bounds = pids.getOutlineShape(state, null, null, null).getBoundingBox();
				assertTrue(bounds.minX >= 0 && bounds.minZ >= 0 && bounds.maxX <= 1 && bounds.maxZ <= 1, facing + " " + angle + ": " + bounds);
				assertFalse(pids.getCollisionShape(state, null, null, null).isEmpty());
			}
		}
		// a 45 degree screen fills a square across the middle
		final Box diagonal = pids.getOutlineShape(pids.getDefaultState().with(FreePlacement.ANGLE, 2), null, null, null).getBoundingBox();
		assertTrue(diagonal.maxX - diagonal.minX > 0.8 && diagonal.maxZ - diagonal.minZ > 0.8, diagonal.toString());
	}

	@Test
	void aTurnedPanelIsAStaircaseNotABigSquare() {
		final BlockState diagonal = pids.getDefaultState().with(FreePlacement.ANGLE, 2);
		final double[] volume = {0};
		pids.getCollisionShape(diagonal, null, null, null).forEachBox((x0, y0, z0, x1, y1, z1) -> volume[0] += (x1 - x0) * (y1 - y0) * (z1 - z0));
		// the panel itself is a quarter of the block; a single bounding box would be most of it
		assertTrue(volume[0] < 0.5, "volume " + volume[0]);
	}

	@Test
	void clicksNudgeHalfABlockOrFlush() {
		// a screen facing north (front to the north), placed on a floor: the viewer stands north, so their left is east (+x)
		final BlockState square = pids.getDefaultState();
		assertEquals(FreePlacement.Nudge.NONE, nudgeOf(square, 0.1, 0.05));
		assertEquals(FreePlacement.Nudge.FLUSH_LEFT, nudgeOf(square, 0.25, 0));
		assertEquals(FreePlacement.Nudge.LEFT, nudgeOf(square, 0.45, 0.1));
		assertEquals(FreePlacement.Nudge.RIGHT, nudgeOf(square, -0.45, 0));
		// north is -z, the front
		assertEquals(FreePlacement.Nudge.FRONT, nudgeOf(square, 0, -0.45));
		assertEquals(FreePlacement.Nudge.FLUSH_BACK, nudgeOf(square, 0.05, 0.3));
		// a click on a wall to the south only counts sideways
		assertEquals(FreePlacement.Nudge.NONE, FreePlacement.place(square, 180F, new Vec3d(0.1, 0.3, 0.5), Direction.NORTH, true).get(FreePlacement.OFFSET));
	}

	private static FreePlacement.Nudge nudgeOf(BlockState square, double x, double z) {
		// yaw 180 looks north; a block facing where the player looks then faces north
		final BlockState placed = FreePlacement.place(square, 180F, new Vec3d(x, -0.5, z), Direction.UP, false);
		assertEquals(0, placed.get(FreePlacement.ANGLE));
		return placed.get(FreePlacement.OFFSET);
	}

	@Test
	void nudgesMoveTheShapeHalfABlockOrToTheEdge() {
		// the hanging screen is 6..10 px deep, facing north (front -z)
		final BlockState north = pids.getDefaultState();
		Box box = pids.getOutlineShape(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.FRONT), null, null, null).getBoundingBox();
		assertEquals(6 / 16.0 - 0.5, box.minZ, 1e-9);
		box = pids.getOutlineShape(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.FLUSH_BACK), null, null, null).getBoundingBox();
		assertEquals(1, box.maxZ, 1e-9);
		box = pids.getOutlineShape(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.FLUSH_FRONT), null, null, null).getBoundingBox();
		assertEquals(0, box.minZ, 1e-9);
		// left of a viewer looking at a north front is east
		box = pids.getOutlineShape(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.LEFT), null, null, null).getBoundingBox();
		assertEquals(0.5, box.minX, 1e-9);
		assertEquals(new Vec3d(0.5, 0, 0), FreePlacement.offset(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.LEFT)));
		// a full-width screen is already flush with its sides
		assertEquals(Vec3d.ZERO, FreePlacement.offset(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.FLUSH_RIGHT)));
		// collision follows the outline
		box = pids.getCollisionShape(north.with(FreePlacement.OFFSET, FreePlacement.Nudge.FLUSH_BACK), null, null, null).getBoundingBox();
		assertEquals(1, box.maxZ, 1e-9);
	}

	@Test
	void statesShareTheirShapes() {
		final BlockState a = pids.getDefaultState().with(FreePlacement.ANGLE, 1).with(FreePlacement.OFFSET, FreePlacement.Nudge.BACK);
		final BlockState b = a.with(PidsBlock.UP, true);
		assertTrue(pids.getOutlineShape(a, null, null, null) == pids.getOutlineShape(b, null, null, null));
	}

	@Test
	void nudgedAlikeScreensJoinOthersDoNot() {
		final BlockState left = pids.getDefaultState().with(FreePlacement.OFFSET, FreePlacement.Nudge.LEFT);
		assertTrue(FreePlacement.joinable(left, left));
		assertFalse(FreePlacement.joinable(left, pids.getDefaultState()));
	}

	@Test
	void rotatingAndMirroringKeepTheDirection() {
		for (int step = 0; step < 16; step++) {
			final BlockState state = pids.getDefaultState().with(FacingShapedBlock.FACING, Direction.fromHorizontal(step / 4)).with(FreePlacement.ANGLE, step % 4);
			final float before = front(state);
			assertEquals((before + 90F) % 360F, front(state.rotate(BlockRotation.CLOCKWISE_90)), 0.01F);
			// left-right mirror (across the north-south line): east and west swap, x' = -x
			final float mirrored = front(state.mirror(BlockMirror.FRONT_BACK));
			assertEquals((360F - before) % 360F, mirrored, 0.01F, "step " + step);
		}
		final BlockState left = pids.getDefaultState().with(FreePlacement.OFFSET, FreePlacement.Nudge.FLUSH_LEFT);
		assertEquals(FreePlacement.Nudge.FLUSH_RIGHT, left.mirror(BlockMirror.FRONT_BACK).get(FreePlacement.OFFSET));
		assertEquals(FreePlacement.Nudge.FLUSH_LEFT, left.rotate(BlockRotation.CLOCKWISE_90).get(FreePlacement.OFFSET));
		final BlockState front = pids.getDefaultState().with(FreePlacement.OFFSET, FreePlacement.Nudge.FRONT);
		assertEquals(FreePlacement.Nudge.FRONT, front.mirror(BlockMirror.LEFT_RIGHT).get(FreePlacement.OFFSET));
	}
}
