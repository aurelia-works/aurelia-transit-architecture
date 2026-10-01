package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPanelSpec;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPlateBlock;
import com.aureliatransit.architecture.block.wayfinding.WayfindingSignBlock;
import com.aureliatransit.architecture.client.interactive.PanelDrawer;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.util.Shapes;
import com.aureliatransit.architecture.wayfinding.ResolvedWayfinding;
import com.aureliatransit.architecture.wayfinding.Wayfinding;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingLayout;
import com.aureliatransit.architecture.wayfinding.WayfindingPanelKind;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the panel of every {@link WayfindingData} block: pylon, directional and exit signs, street blade, pictogram
 * sign, and the bus e-paper board. Static-first: the panel is laid out once and replayed. The resolved content (MTR
 * station facts merged with the manual data) and the row length of a joined sign are re-checked once per second and the
 * layout is rebuilt only when one of them, or the stored data, changes. The e-paper board redraws its departures only
 * every 15 to 30 seconds (and once as soon as data first arrives), but still asks the cached provider every second so
 * MTR keeps its platforms in the arrivals poll. Only the owner block of a joined row draws, and single-sided signs
 * draw nothing when seen from behind. No block-entity ticking, no dynamic textures.
 */
public final class WayfindingSignRenderer implements BlockEntityRenderer<WayfindingSignBlockEntity> {

	private static final int RECHECK_TICKS = 20;
	private static final int DEFAULT_ROW = 1;

	private static final class Cached {
		WayfindingData data;
		int rowLength = -1;
		ResolvedWayfinding resolved;
		PanelLayout.Panel panel = PanelLayout.Panel.EMPTY;
		long nextCheck;
		// e-paper only
		long nextRefreshMillis;
		boolean built;
		StationSnapshot snapshot = StationSnapshot.EMPTY;
		boolean showingIdle;
	}

	private final TextRenderer textRenderer;
	private final Map<WayfindingSignBlockEntity, Cached> cache = new WeakHashMap<>();

	public WayfindingSignRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public void render(WayfindingSignBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof WayfindingPlateBlock block) || !block.drawsPanel(state)) {
			return;
		}
		final World world = entity.getWorld();
		if (world == null) {
			return;
		}
		final WayfindingPanelSpec spec = block.spec();
		final Direction facing = state.get(WayfindingPlateBlock.FACING);
		if (!spec.doubleSided()) {
			// Nothing to draw for someone standing behind a single-sided panel.
			final Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			final double dx = camera.x - (entity.getPos().getX() + 0.5);
			final double dz = camera.z - (entity.getPos().getZ() + 0.5);
			if (dx * facing.getOffsetX() + dz * facing.getOffsetZ() < -0.6) {
				return;
			}
		}

		final Cached cached = cache.computeIfAbsent(entity, key -> new Cached());
		update(cached, entity, block, state, spec, world);
		if (cached.panel.isEmpty()) {
			return;
		}

		final boolean epaper = spec.kind() == WayfindingPanelKind.BUS_STOP;
		final int panelLight = !epaper && state.getLuminance() > 0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		final float centerX = spec.panelCenterX(cached.rowLength);

		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(facing)));
		matrices.translate(-0.5, 0, -0.5);
		PanelDrawer.beginFace(matrices, centerX, spec.centerY(), spec.frontZ(), true);
		PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, panelLight);
		PanelDrawer.endFace(matrices);
		if (spec.doubleSided()) {
			PanelDrawer.beginFace(matrices, centerX, spec.centerY(), spec.backZ(), false);
			PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, panelLight);
			PanelDrawer.endFace(matrices);
		}
		matrices.pop();
	}

	private void update(Cached cached, WayfindingSignBlockEntity entity, WayfindingPlateBlock block, BlockState state, WayfindingPanelSpec spec, World world) {
		final WayfindingData data = entity.getWayfinding();
		final long time = world.getTime();
		final boolean epaper = spec.kind() == WayfindingPanelKind.BUS_STOP;
		boolean dirty = cached.data != data;
		if (dirty || time >= cached.nextCheck || time < cached.nextCheck - RECHECK_TICKS * 2L) {
			cached.nextCheck = time + RECHECK_TICKS;
			final int rowLength = block instanceof WayfindingSignBlock sign ? sign.rowLength(world, entity.getPos(), state) : DEFAULT_ROW;
			final ResolvedWayfinding resolved = Wayfinding.resolve(entity.getPos(), data);
			dirty |= cached.rowLength != rowLength || !resolved.equals(cached.resolved);
			cached.data = data;
			cached.rowLength = rowLength;
			cached.resolved = resolved;
			if (epaper) {
				// Ask the (cached) provider every second even though the panel redraws rarely: it keeps the platforms in
				// MTR's arrivals poll and the snapshot cache warm, so a slow refresh never lands on an empty list.
				cached.snapshot = StationData.provider().resolve(entity.getPos(), data.autoStation() ? data.association() : StationAssociation.AUTO, true);
				dirty |= cached.showingIdle && !cached.snapshot.services().isEmpty();
			}
		}
		if (epaper) {
			final long now = System.currentTimeMillis();
			if (dirty || !cached.built || EPaperLayout.due(now, cached.nextRefreshMillis)) {
				rebuildEPaper(cached, entity.getPos(), spec, now);
			}
		} else if (dirty && spec.kind() == WayfindingPanelKind.TERMINAL) {
			cached.panel = TerminalFace.layout(cached.resolved, spec.panelWidth(cached.rowLength), spec.height(), spec.textColor(), s -> textRenderer.getWidth(s));
		} else if (dirty) {
			final float w = spec.panelWidth(cached.rowLength);
			cached.panel = WayfindingLayout.layout(cached.resolved, spec.kind(), w, spec.height(), spec.textColor(), s -> textRenderer.getWidth(s));
		}
	}

	private void rebuildEPaper(Cached cached, BlockPos pos, WayfindingPanelSpec spec, long now) {
		cached.built = true;
		cached.nextRefreshMillis = now + EPaperLayout.refreshIntervalMillis(pos.asLong());
		final StationSnapshot snapshot = cached.snapshot;
		final String manual = cached.resolved.stationName();
		final String header = !manual.isEmpty() ? manual : snapshot.station() != null ? snapshot.station().displayName() : "";
		final float w = spec.panelWidth(cached.rowLength);
		final List<EPaperLayout.Row> rows = EPaperLayout.rows(snapshot.services(), now, EPaperLayout.maxRows(spec.height()));
		cached.showingIdle = rows.isEmpty();
		final String idle = snapshot.station() == null ? "No stop linked" : "No departures currently available";
		cached.panel = EPaperLayout.layout(header, rows, idle, w, spec.height(), s -> textRenderer.getWidth(s));
	}

	@Override
	public boolean rendersOutsideBoundingBox(WayfindingSignBlockEntity entity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 40;
	}
}
