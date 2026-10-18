package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the audit tells a person to do about each finding: a title for every topic and a next step for every warning and failure. */
class StationAuditAdviceTest {

	private static final String GENERIC = new StationAudit.Check(StationAudit.Topic.SIZE, StationAudit.Status.FAIL, "x").fix();

	private static final List<String> WORDS = List.of(
			"2 tracks, 48 long, named Plain",
			"4 tracks island, 96 long, hamburg, station building, footbridge, named Hall",
			"4 tracks island, 96 long, amsterdam, terminus, hall roof, named Head",
			"3 tracks, 80 long, utrecht, station building, footbridge, escalators, travelators, named Big");

	@Test
	void everyTopicHasADistinctTitle() {
		final Set<String> titles = new HashSet<>();
		for (final StationAudit.Topic topic : StationAudit.Topic.values()) {
			assertFalse(topic.title().isBlank(), topic.name());
			assertTrue(titles.add(topic.title()), "two topics are both called " + topic.title());
		}
		assertEquals("Step-free access", StationAudit.Topic.ACCESS.title());
		assertEquals("Step-free access", new StationAudit.Check(StationAudit.Topic.ACCESS, StationAudit.Status.PASS, "ok").title());
	}

	@Test
	void aPassNeedsNoFixAndEveryWarningAndFailureHasOne() {
		final StationAudit.Check pass = new StationAudit.Check(StationAudit.Topic.ROOF, StationAudit.Status.PASS, "fine", "a fix nobody needs");
		assertEquals("", pass.fix());
		for (final StationAudit.Status status : new StationAudit.Status[]{StationAudit.Status.WARN, StationAudit.Status.FAIL}) {
			assertFalse(new StationAudit.Check(StationAudit.Topic.ROOF, status, "x", null).fix().isBlank());
			assertFalse(new StationAudit.Check(StationAudit.Topic.ROOF, status, "x", "  ").fix().isBlank());
			assertEquals("Raise it.", new StationAudit.Check(StationAudit.Topic.ROOF, status, "x", "Raise it.").fix());
		}
		// real audits of untouched plans: passes carry no fix, and any problem has a next step
		for (final String words : WORDS) {
			for (final StationAudit.Check check : StationAudit.audit(StationPlanner.plan(StationDescriptionParser.parse(words).spec())).checks()) {
				assertEquals(check.status() == StationAudit.Status.PASS, check.fix().isEmpty(), words + " " + check);
			}
		}
	}

	/**
	 * Break the plans by taking each kind of block away in turn, as the audit tests do: whatever the audit then warns or fails about
	 * says what to do, in words written for that finding and not the all-purpose line.
	 */
	@Test
	void everyFindingFromABrokenPlanCarriesItsOwnNextStep() {
		final Set<String> seen = new HashSet<>();
		int problems = 0;
		for (final String words : WORDS) {
			final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
			for (final Role role : Role.values()) {
				final List<PlannedBlock> kept = new ArrayList<>();
				for (final PlannedBlock block : plan.blocks()) {
					if (block.role() != role) {
						kept.add(block);
					}
				}
				if (kept.size() == plan.blocks().size()) {
					continue;
				}
				final StationPlan broken = new StationPlan(plan.spec(), plan.layout(), kept, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(),
						plan.foundationId());
				for (final StationAudit.Check check : StationAudit.audit(broken).problems()) {
					problems++;
					assertFalse(check.fix().isBlank(), words + " without " + role + ": " + check);
					assertNotEquals(GENERIC, check.fix(), words + " without " + role + " got the all-purpose fix: " + check);
					assertFalse(check.fix().contains("{") || check.fix().contains("null"), check.fix());
					seen.add(check.topic() + "/" + check.status());
				}
			}
		}
		assertTrue(problems > 50, "only " + problems + " findings were checked");
		// the breakages reach most topics
		assertTrue(seen.size() >= 8, "only these findings were reached: " + seen);
	}
}
