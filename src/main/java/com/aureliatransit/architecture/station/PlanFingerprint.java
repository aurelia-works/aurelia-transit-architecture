package com.aureliatransit.architecture.station;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * A short stable name for a plan: the same blocks in the same places give the same fingerprint, any difference in a block's
 * position, id, facing, state or sign text gives another. Station Studio shows it next to the seed, and a schematic export carries
 * it, so two people can tell whether they are looking at the same station.
 *
 * <p>The first {@value #HEX_CHARS} hex digits of a SHA-256 over the blocks sorted by (u, y, v), one line each:
 * {@code u,y,v,blockId,facing,state,text}. Local coordinates are used (the studio's x is u and its z is -v), so the fingerprint does
 * not depend on where or which way the station is built. Roles are not part of it: they decide how a block is chosen, not what
 * the world gets.
 */
public final class PlanFingerprint {

	/** Hex digits kept: 64 bits, enough that a collision among all the stations anyone plans is a non-event. */
	public static final int HEX_CHARS = 16;

	private static final Comparator<PlannedBlock> ORDER = Comparator.comparingInt(PlannedBlock::u).thenComparingInt(PlannedBlock::y)
			.thenComparingInt(PlannedBlock::v).thenComparing(PlannedBlock::blockId).thenComparing(PlannedBlock::state)
			.thenComparing(b -> b.front().ordinal()).thenComparing(PlannedBlock::text);

	private PlanFingerprint() {
	}

	public static String of(StationPlan plan) {
		return of(plan.blocks());
	}

	public static String of(List<PlannedBlock> blocks) {
		final List<PlannedBlock> sorted = new ArrayList<>(blocks);
		sorted.sort(ORDER);
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
		}
		final StringBuilder line = new StringBuilder();
		for (final PlannedBlock block : sorted) {
			line.setLength(0);
			line.append(block.u()).append(',').append(block.y()).append(',').append(block.v()).append(',').append(block.blockId()).append(',')
					.append(block.front().name()).append(',').append(block.state()).append(',').append(block.text().replace('\n', ' ')).append('\n');
			digest.update(line.toString().getBytes(StandardCharsets.UTF_8));
		}
		return HexFormat.of().formatHex(digest.digest()).substring(0, HEX_CHARS);
	}
}
