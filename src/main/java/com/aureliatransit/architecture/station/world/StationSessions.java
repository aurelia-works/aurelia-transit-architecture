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
 * Per-player state of the station generator and the one tick that drives it: pending plans, previews, site tasks, running
 * jobs and the last build's undo log. Server thread only. A pending plan, its preview and a site task go when the player
 * leaves; a running job does not.
 */
public final class StationSessions {

	/**
	 * Server time all jobs together may use in a tick: a fifth of the 50 ms tick. A placement costs about 10 µs, so this is
	 * roughly a thousand blocks a tick; pacing by count alone (64 a tick) kept a 6000-block station waiting 5 s on an idle
	 * server.
	 */
	public static final long NANOS_PER_TICK = 10_000_000L;
	/** Ops all jobs together may run in a tick, however fast they go. */
	public static final int OPS_PER_TICK = 4096;

	/**
	 * A plan waiting for confirmation. The frame is fixed at planning time; confirm never takes it from the player's position.
	 *
	 * @param railSkipped rail nodes will be left out because MTR is not loaded
	 */
	public record Pending(ParseResult parse, StationSpec spec, StationPlan plan, LocalFrame frame, RegistryKey<World> dimension,
			List<Violation> violations, StationAudit.Result audit, SurveyReport report, boolean railSkipped) {
	}

	/**
	 * Work for a player that reads a site over as many ticks as it needs (a survey, a rebuild's look at its own cells), so the
	 * chunks it loads can unload between ticks. It does whatever comes next itself once it is done.
	 */
	public interface Task {

		/** Works until done or {@code deadline} ({@link System#nanoTime}) has passed; true when done. */
		boolean step(long deadline);

		/** Called off before it is done: let go of what it holds. */
		void cancel();
	}

	/** The station a player's last confirmed build was planned as, for {@code /ata_station verify}; kept after the pending plan goes. */
	public record Built(StationPlan plan, StationSpec spec, LocalFrame frame, RegistryKey<World> dimension, boolean railSkipped) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, StationPreview> PREVIEWS = new HashMap<>();
	private static final Map<UUID, Built> BUILT = new HashMap<>();
	private static final Map<UUID, StationBuildJob> JOBS = new LinkedHashMap<>();
	private static final Map<UUID, Task> TASKS = new LinkedHashMap<>();
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

	public static void setBuilt(UUID player, Pending pending) {
		BUILT.put(player, new Built(pending.plan(), pending.spec(), pending.frame(), pending.dimension(), pending.railSkipped()));
	}

	/** The plan of the player's last confirmed build; null when there is none. */
	public static Built built(UUID player) {
		return BUILT.get(player);
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

	/** A player left: their pending plan, preview and site task go, a running job keeps going. */
	public static void forgetPlayer(UUID player) {
		cancelTask(player);
		discard(player);
		BUILT.remove(player);
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

	// ---- site tasks ------------------------------------------------------------------------------------------------

	/**
	 * Runs a player's task for up to a tick's time budget now, so a small site is done at once, and leaves the rest to the
	 * following ticks. Calls off a task the player already has.
	 */
	public static void startTask(UUID player, Task task) {
		final Task old = TASKS.remove(player);
		if (old != null && old != task) {
			old.cancel();
		}
		TASKS.put(player, task);
		if (task.step(System.nanoTime() + NANOS_PER_TICK) && TASKS.get(player) == task) {
			TASKS.remove(player);
		}
	}

	/** The player's unfinished task, or null. */
	public static Task task(UUID player) {
		return TASKS.get(player);
	}

	/** Calls off the player's unfinished task; true when there was one. */
	public static boolean cancelTask(UUID player) {
		final Task task = TASKS.remove(player);
		if (task == null) {
			return false;
		}
		task.cancel();
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
		if (job.mode() == StationBuildJob.Mode.REBUILD) {
			// the rebuild's log took over the last build's cells, kept or not, so it replaces that log even when empty
			UNDO.put(job.owner(), job.undoLog());
		} else if (job.mode() == StationBuildJob.Mode.BUILD) {
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
		BUILT.clear();
		JOBS.clear();
		TASKS.values().forEach(Task::cancel);
		TASKS.clear();
		UNDO.clear();
		LAST_CANCEL.clear();
	}

	// ---- the one tick ----------------------------------------------------------------------------------------------

	/** Registered once by {@link StationSystems}; returns at once when nothing is active. */
	static void tick(MinecraftServer server) {
		if (JOBS.isEmpty() && PREVIEWS.isEmpty() && TASKS.isEmpty()) {
			return;
		}
		if (!TASKS.isEmpty()) {
			tickTasks();
		}
		if (!JOBS.isEmpty()) {
			tickJobs();
		}
		if (!PREVIEWS.isEmpty()) {
			PREVIEWS.values().removeIf(preview -> !preview.tick(server));
		}
	}

	/** The tasks share one tick's time budget, in turn; a task done hands over to whatever it started next. */
	private static void tickTasks() {
		final long deadline = System.nanoTime() + NANOS_PER_TICK;
		for (final Map.Entry<UUID, Task> entry : new ArrayList<>(TASKS.entrySet())) {
			if (System.nanoTime() >= deadline) {
				break;
			}
			final Task task = entry.getValue();
			if (TASKS.get(entry.getKey()) == task && task.step(deadline) && TASKS.get(entry.getKey()) == task) {
				TASKS.remove(entry.getKey());
			}
		}
	}

	private static void tickJobs() {
		final List<StationBuildJob> served = new ArrayList<>(JOBS.values());
		final boolean starved = runBatches(served, NANOS_PER_TICK, OPS_PER_TICK);
		for (final StationBuildJob job : served) {
			reap(job);
		}
		if (starved && served.size() > 1) {
			// the first job goes to the back of the queue so the others start the next tick
			final StationBuildJob first = served.get(0);
			if (JOBS.get(first.owner()) == first) {
				JOBS.remove(first.owner());
				JOBS.put(first.owner(), first);
			}
		}
	}

	/**
	 * One tick's work: runs the jobs in batches, taking turns, until {@code nanos} of server time or {@code ops} ops are used. A
	 * job that did less than a full batch (finished, stopped or waiting for a chunk) is not asked again, so a wait still counts
	 * once per tick. True if the budget ran out with work left. Public for the game tests.
	 */
	public static boolean runBatches(List<StationBuildJob> jobs, long nanos, int ops) {
		final long deadline = System.nanoTime() + nanos;
		int budget = ops;
		final List<StationBuildJob> active = new ArrayList<>(jobs);
		while (!active.isEmpty()) {
			for (final java.util.Iterator<StationBuildJob> it = active.iterator(); it.hasNext(); ) {
				if (budget <= 0 || System.nanoTime() >= deadline) {
					return true;
				}
				final StationBuildJob job = it.next();
				final int ask = Math.min(StationBuildJob.OPS_PER_TICK, budget);
				final int used = job.step(ask);
				budget -= used;
				if (used < ask) {
					it.remove();
				}
			}
		}
		return false;
	}
}
