package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.transit.StationAssociation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The shared Auto/Manual station picker as a stand-alone screen, for blocks (wayfinding signs, terminals, e-paper
 * boards) whose own editor has no room for it. It hands the chosen {@link StationAssociation} back to {@code onDone}
 * and returns to {@code parent}; nothing is sent to the server here.
 */
public final class StationPickerScreen extends LiveConfigScreen {

	private final Screen parent;
	private final Consumer<StationAssociation> onDone;
	private final BooleanSupplier valid;

	public StationPickerScreen(Screen parent, BlockPos pos, StationAssociation initial, BooleanSupplier valid, Consumer<StationAssociation> onDone) {
		super(Text.translatable("screen.aurelia_transit_architecture.wf_station_source"), pos, initial);
		this.parent = parent;
		this.onDone = onDone;
		this.valid = valid;
	}

	@Override
	protected void init() {
		buildAssociationWidgets();
		addDone();
	}

	@Override
	protected void save() {
		onDone.accept(association());
	}

	@Override
	protected boolean stillValid() {
		return valid.getAsBoolean();
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
