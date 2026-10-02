package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.text.Text;

/** Chat and action-bar lines of the card and fare gate. */
final class FareText {

	static final String PREFIX = "message." + AureliaTransitArchitecture.MOD_ID + ".card.";

	private FareText() {
	}

	static Text balance(int balance) {
		return Text.translatable(PREFIX + "balance", balance);
	}

	static Text of(String key, Object... args) {
		return Text.translatable(PREFIX + key, args);
	}
}
