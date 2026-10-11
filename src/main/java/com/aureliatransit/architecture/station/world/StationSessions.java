package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.Violation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-player state of the station generator and the one tick that drives it: pending plans, previews, running jobs and the
 * last build's undo log. Server thread only. A pending plan and its preview go when the player leaves; a running job does not.
 */
public final class StationSessions {

	/** Ops all jobs together may run in a tick. */
	public static final int OPS_PER_TICK = 256;

	/**
	 * A plan waiting for confirmation. The frame is fixed at planning time; confirm never takes it from the player's position.
	 *
	 * @param railSkipped rail nodes will be left out because MTR is not loaded
	 */
	public record Pending(ParseResult parse, StationSpec spec, StationPlan plan, LocalFrame frame, RegistryKey<World> dimension,
			List<Violation> violations, StationAudit.Result audit, SurveyReport report, boolean railSkipped) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, StationPreview> PREVIEWS = new HashMap<>();
	private static final Map<UUID, StationBuildJob> JOBS = new LinkedHashMap<>();
	private static final Map<UUID, UndoLog> UNDO = new HashMap<>();
	/** Server tick of each player's last wrench cancel, so a held left-click does not repeat it. */
	private static final Map<UUID, Integer> LAST_CANCEL = new HashMap<>();

	private StationSessions() {
	}

	// ---- pending plans and previews --------------------------------------------------------------------------------

	public static void setPending(UUID player, Pending pending) {
		PENDING.put(player, pending);
	}

	public static Pending pending(UUID player) {
		return PENDING.get(player);
	}

	/** Drops the pending plan and its preview; true when there was a plan. */
	public static boolean discard(UUID player) {
		PREVIEWS.remove(player);
		return PENDING.remove(player) != null;
	}

	public static void startPreview(StationPreview preview) {
		PREVIEWS.put(preview.player(), preview);
	}

	public static StationPreview preview(UUID player) {
		return PREVIEWS.get(player);
	}

	public static void stopPreview(UUID player) {
		PREVIEWS.remove(player);
	}

	/** A player left: their pending plan and preview go, a running job keeps going. */
	public static void forgetPlayer(UUID player) {
		discard(player);
		LAST_CANCEL.remove(player);
	}

	/** True (and starts the cooldown) when the player's last wrench cancel is at least {@code cooldown} ticks old. */
	static boolean cancelCooldown(UUID player, int now, int cooldown) {
		final Integer last = LAST_CANCEL.get(player);
		if (last != null && now - last < cooldown && now >= last) {
			return false;
		}
		LAST_CANCEL.put(player, now);
		return true;
	}

	// ---- jobs ------------------------------------------------------------------------------------------------------

	/** Registers a job to be ticked; false when the owner already has one running. */
	public static boolean start(StationBuildJob job) {
		if (JOBS.containsKey(job.owner())) {
			return false;
		}
		JOBS.put(job.owner(), job);
		return true;
	}

	public static StationBuildJob job(UUID player) {
		return JOBS.get(player);
	}

	/** Cancels the player's running job, if any; true when one was cancelled. */
	public static boolean cancelJob(UUID player) {
		final StationBuildJob job = JOBS.get(player);
		if (job == null) {
			return false;
		}
		job.cancel();
		reap(job);
		return true;
	}

	/** The last build's undo log (null when there is nothing to undo). */
	public static UndoLog undoLog(UUID player) {
		return UNDO.get(player);
	}

	/** A chat sender for a player who may be offline by the time a job speaks. */
	public static Consumer<Text> messenger(MinecraftServer server, UUID player) {
		return text -> {
			final ServerPlayerEntity target = server.getPlayerManager().getPlayer(player);
			if (target != null) {
				target.sendMessage(text, false);
			}
		};
	}

	/** Forgets a finished job and keeps what it leaves for undo. */
	private static void reap(StationBuildJob job) {
		if (!job.finished() || JOBS.get(job.owner()) != job) {
			return;
		}
		JOBS.remove(job.owner());
		if (job.mode() == StationBuildJob.Mode.BUILD) {
			if (!job.undoLog().isEmpty()) {
				UNDO.put(job.owner(), job.undoLog());
			}
		} else if (job.undoLog().isEmpty()) {
			UNDO.remove(job.owner());
		}
	}

	public static void clear() {
		PENDING.clear();
		PREVIEWS.clear();
		JOBS.clear();
		UNDO.clear();
		LAST_CANCEL.clear();
	}

	// ---- the one tick ----------------------------------------------------------------------------------------------

	/** Registered once by {@link StationSystems}; returns at once when nothing is active. */
	static void tick(MinecraftServer server) {
		if (JOBS.isEmpty() && PREVIEWS.isEmpty()) {
			return;
		}
		if (!JOBS.isEmpty()) {
			tickJobs();
		}
		if (!PREVIEWS.isEmpty()) {
			PREVIEWS.values().removeIf(preview -> !preview.tick(server));
		}
	}

	private static void tickJobs() {
		int budget = OPS_PER_TICK;
		final List<StationBuildJob> served = new ArrayList<>();
		boolean starved = false;
		for (final StationBuildJob job : new ArrayList<>(JOBS.values())) {
			if (budget <= 0) {
				starved = true;
				break;
			}
			budget -= job.step(Math.min(StationBuildJob.OPS_PER_TICK, budget));
			served.add(job);
		}
		for (final StationBuildJob job : served) {
			reap(job);
		}
		if (starved) {
			// the jobs that ran go to the back of the queue so the others get the next tick
			for (final StationBuildJob job : served) {
				if (JOBS.get(job.owner()) == job) {
					JOBS.remove(job.owner());
					JOBS.put(job.owner(), job);
				}
			}
		}
	}
}
