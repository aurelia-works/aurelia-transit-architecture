package com.aureliatransit.architecture.station;

import java.util.List;

/**
 * Ready-made descriptions behind the wrench screen's preset buttons, one per building look, laid out like the station each look
 * is after: the through stations (Amsterdam Centraal, Munchen Ost, Tokyo, Berlin Hbf, Beijing South, Kuala
 * Lumpur, Teddington, Surbiton, Coventry) with their building beside the tracks, and the termini (St Pancras, Antwerp,
 * Frankfurt, Grand Central, Zurich, Paris, Milan, West Kowloon, Hua Lamphong, CSMT) as they are, scaled to at most 10 tracks
 * and 300 blocks (Jah, 10-20: the geography was wrong, with Tokyo, Berlin and Beijing built as termini and Amsterdam Centraal
 * as a two-track halt). They are plain descriptions, so the parser, the design rules and the station checks treat them like
 * typed text; the tests keep every preset free of warnings, ignored words, rule breaks and failed checks.
 */
public final class StationPresets {

	/**
	 * @param key         stable id; the button label is {@code screen.aurelia_transit_architecture.station_wrench.preset.<key>}
	 * @param building    the building look the description must give
	 * @param description the text put in the wrench
	 */
	public record Preset(String key, BuildingStyle building, String description) {
	}

	public static final List<Preset> ALL = List.of(
			// the real stations: their halls, platforms and operators (see RealStation), not looks after them
			new Preset("utrecht", BuildingStyle.NONE, "utrecht centraal"),
			new Preset("hamburg_hbf", BuildingStyle.SANDSTONE, "hamburg hauptbahnhof"),
			new Preset("amsterdam", BuildingStyle.AMSTERDAM,
					"eight track amsterdam station, four island platforms, 240 long, train shed, station building with footbridge, named Amsterdam Centraal"),
			new Preset("st_pancras", BuildingStyle.GOTHIC_BRICK,
					"eight track st pancras terminus, four island platforms, 200 long, train shed, named St Pancras"),
			new Preset("antwerp", BuildingStyle.ANTWERP,
					"eight track antwerp terminus, four island platforms, 180 long, train shed, named Antwerpen Centraal"),
			new Preset("frankfurt", BuildingStyle.SANDSTONE,
					"ten track frankfurt terminus, five island platforms, 240 long, train shed, named Frankfurt Hbf"),
			new Preset("munich", BuildingStyle.CONCRETE,
					"six track munich station, three island platforms, 210 long, station building with footbridge, named Munchen Ost"),
			new Preset("grand_central", BuildingStyle.BEAUX_ARTS,
					"eight track grand central terminus, four island platforms, 200 long, named Grand Central"),
			new Preset("tokyo", BuildingStyle.TOKYO,
					"ten track tokyo station, five island platforms, 300 long, station building with footbridge, named Tokyo"),
			new Preset("zurich", BuildingStyle.ZURICH,
					"eight track zurich terminus, four island platforms, 200 long, train shed, named Zurich HB"),
			new Preset("paris", BuildingStyle.PARIS,
					"eight track paris terminus, four island platforms, 200 long, train shed, named Paris Nord"),
			new Preset("milan", BuildingStyle.MILAN,
					"ten track milan terminus, five island platforms, 240 long, train shed, named Milano Centrale"),
			new Preset("berlin", BuildingStyle.BERLIN,
					"six track berlin station, three island platforms, 300 long, train shed, station building with footbridge, named Berlin Hbf"),
			new Preset("hong_kong", BuildingStyle.HONG_KONG,
					"eight track hong kong terminus, four island platforms, 200 long, named West Kowloon"),
			new Preset("beijing", BuildingStyle.BEIJING,
					"ten track beijing station, five island platforms, 300 long, train shed, station building with mezzanine, named Beijing South"),
			new Preset("bangkok", BuildingStyle.BANGKOK,
					"ten track bangkok terminus, five island platforms, 200 long, train shed, named Krung Thep"),
			new Preset("kuala_lumpur", BuildingStyle.KUALA_LUMPUR,
					"four track kuala lumpur station, two island platforms, 160 long, station building with footbridge, named Kuala Lumpur"),
			// KL Sentral's lower level (research 10-20): KTM Komuter on three islands, KLIA Ekspres and KLIA Transit beside
			// them, under a modern glass and steel hall; ten tracks on five islands is the honest fit (no tracks under the hall)
			new Preset("kl_sentral", BuildingStyle.MODERN,
					"ten track modern station, five island platforms, 190 long, glass canopy, grand station building with footbridge, escalators, named KL Sentral"),
			// SEPTA's two Market-Frankford Line termini (research 10-20), at ground level (the generator builds no viaduct):
			// Frankford's one island under a canopy with the transit centre at its end, and 69th Street's island and side
			// platform in front of its brick hall; the bus loops beside them are not built
			new Preset("frankford", BuildingStyle.MODERN,
					"two track concrete terminus, island platform, 130 long, canopy, city station building, escalators, named Frankford"),
			new Preset("sixty_ninth_street", BuildingStyle.AMSTERDAM,
					"three track brick terminus, island and side platforms, 110 long, canopy, city station building, escalators, named 69th Street"),
			new Preset("mumbai", BuildingStyle.MUMBAI,
					"ten track mumbai terminus, five island platforms, 280 long, train shed, named Mumbai CSMT"),
			// British suburban stations (Enviro, 10-20), laid out as the real ones: Teddington's two side platforms and
			// footbridge, Surbiton's two islands, Coventry's island and side platform under a covered overbridge
			new Preset("teddington", BuildingStyle.VICTORIAN,
					"two track teddington station, side platforms, 200 long, small station building with footbridge, named Teddington"),
			new Preset("surbiton", BuildingStyle.ART_DECO,
					"four track surbiton station, two island platforms, 250 long, city station building with footbridge, named Surbiton"),
			new Preset("coventry", BuildingStyle.MODERNIST,
					"three track coventry station, island and side platforms, 250 long, city station building with footbridge, named Coventry"),
			// an underground station (Enviro, 10-20): Berlin's U8 at Rosenthaler Platz, one island in an orange-tiled box with
			// stairs at both ends straight up to the street
			new Preset("rosenthaler_platz", BuildingStyle.NONE,
					"two track berlin underground station, island platform, 130 long, named Rosenthaler Platz"),
			// deep stations (research 10-20, docs/research): Angel is a bored tube 27 m down (40 blocks; 27 m would round to 41) with escalators only ("no ramps": no lift);
			// Canary Wharf and Vijzelgracht are cut-and-cover boxes (24 m and 26 m, 36 and 39 blocks) with lifts. Lengths are the
			// most the generator builds (Canary Wharf's 265 m is capped at 300 blocks); Vijzelgracht's 125 m island is built 150 long
			// (a 188-long island box fails the ground check, still open)
			new Preset("angel", BuildingStyle.NONE,
					"2 track deep tube station, 40 blocks deep, 120 long, no ramps, tfl signs, named Angel"),
			new Preset("canary_wharf", BuildingStyle.NONE,
					"2 track cut and cover box station, 24 m deep, 200 long, tfl signs, named Canary Wharf"),
			new Preset("vijzelgracht", BuildingStyle.NONE,
					"2 track cut and cover box station, island platform, 26 m deep, 150 long, gvb signs, named Vijzelgracht"),
			new Preset("modern", BuildingStyle.MODERN,
					"four track modern station, two island platforms, 96 long, glass canopy, station building with footbridge"));

	private StationPresets() {
	}
}
