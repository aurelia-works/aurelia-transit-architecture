package com.aureliatransit.architecture.station.world;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a build wrote, in order: the first state each cell had, the last it was left with, and the one-shot lists undo takes. */
class UndoLogTest {

	private static final BlockPos A = new BlockPos(1, 2, 3);
	private static final BlockPos B = new BlockPos(4, 5, 6);

	private static BlockState air;
	private static BlockState stone;
	private static BlockState dirt;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		air = Blocks.AIR.getDefaultState();
		stone = Blocks.STONE.getDefaultState();
		dirt = Blocks.DIRT.getDefaultState();
	}

	private static UndoLog log() {
		return new UndoLog(World.OVERWORLD);
	}

	@Test
	void startsEmptyAndRemembersItsDimension() {
		final UndoLog log = log();
		assertTrue(log.isEmpty());
		assertEquals(0, log.size());
		assertSame(World.OVERWORLD, log.dimension());
	}

	@Test
	void undoWalksTheEntriesBackwards() {
		final UndoLog log = log();
		log.add(new UndoLog.Entry(A, air, stone));
		log.add(new UndoLog.Entry(B, dirt, stone));
		assertEquals(2, log.size());
		assertEquals(B, log.last().pos());
		log.removeLast();
		assertEquals(A, log.last().pos());
		log.removeLast();
		assertTrue(log.isEmpty());
	}

	@Test
	void aCellOverwrittenTwiceKeepsItsFirstPreviousAndItsLastPlacedState() {
		final UndoLog log = log();
		log.add(new UndoLog.Entry(A, dirt, stone));
		log.add(new UndoLog.Entry(B, air, stone));
		log.add(new UndoLog.Entry(A, stone, air));
		final Map<BlockPos, UndoLog.Entry> cells = log.cells();
		assertEquals(2, cells.size());
		assertSame(dirt, cells.get(A).previous(), "the build found dirt there first");
		assertSame(air, cells.get(A).placed(), "and left air there last");
		assertSame(air, cells.get(B).previous());
		assertEquals(List.of(A, B), List.copyOf(cells.keySet()), "cells keep the order they were first written");
	}

	@Test
	void theLastKeyWrittenForACellWinsAndMovesItToTheEnd() {
		final UndoLog log = log();
		log.add(new UndoLog.Entry(A, air, stone), "stone");
		log.add(new UndoLog.Entry(B, air, stone), "stone");
		log.add(new UndoLog.Entry(A, stone, dirt), "dirt");
		assertEquals(List.of(B, A), List.copyOf(log.keys().keySet()));
		assertEquals("dirt", log.keys().get(A));
	}

	@Test
	void soilIsRememberedOnceAndCarriedToARebuild() {
		final UndoLog first = log();
		first.coverSoil(A, Blocks.GRASS_BLOCK.getDefaultState());
		first.coverSoil(A, dirt);
		assertSame(Blocks.GRASS_BLOCK.getDefaultState(), first.coveredSoil(A), "the first cover is the original soil");
		final UndoLog rebuild = log();
		rebuild.coverSoil(B, Blocks.MYCELIUM.getDefaultState());
		rebuild.coverSoil(A, Blocks.PODZOL.getDefaultState());
		rebuild.copySoil(first);
		assertSame(Blocks.PODZOL.getDefaultState(), rebuild.coveredSoil(A), "a rebuild's own soil is not overwritten");
		assertSame(Blocks.MYCELIUM.getDefaultState(), rebuild.coveredSoil(B));
	}

	@Test
	void liftsAreTakenOnce() {
		final UndoLog log = log();
		log.addLift(A);
		log.addLift(B);
		assertEquals(2, log.lifts());
		assertEquals(List.of(A, B), log.takeLifts());
		assertEquals(0, log.lifts());
		assertTrue(log.takeLifts().isEmpty());
	}

	@Test
	void stationsAreTakenOnceAndLaterOnesAreRefused() {
		final UndoLog log = log();
		assertTrue(log.addStation(7));
		assertTrue(log.addStation(8));
		assertEquals(2, log.stations());
		assertEquals(List.of(7L, 8L), log.takeStations());
		assertEquals(0, log.stations());
		assertFalse(log.addStation(9), "after undo took them, a late answer from MTR is deleted by the caller");
		assertEquals(0, log.stations());
	}
}
