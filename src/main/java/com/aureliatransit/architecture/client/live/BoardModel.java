package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.wayfinding.MessageRotation;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/**
 * Pre-computed draw list of one display: flat rectangles, text strings and scrolling lines in "virtual units" (1 unit =
 * 1/8 of a default font line; origin top-left of the screen, y down). Rebuilt at most about once a second by
 * {@link BoardBuilder}, then replayed every frame without any layout work. Arrays are reused between rebuilds.
 */
final class BoardModel {

	/**
	 * Depth layers of rectangles. Overlapping rectangles must not share a plane: coplanar quads z-fight, which showed as
	 * route chips and row bands flickering against the background as the camera moved.
	 */
	static final int LAYER_BACKGROUND = 0;
	static final int LAYER_BAND = 1;
	static final int LAYER_CHIP = 2;
	static final int LAYERS = 3;
	/**
	 * In front of the text: covers the ends of a scrolling line, so letters slide in and out at the edge of their window
	 * instead of popping. Text on {@link #TEXT_TOP} is drawn in front of the masks.
	 */
	static final int LAYER_MASK = 3;
	static final int TEXT_NORMAL = 0;
	static final int TEXT_TOP = 1;

	/** The lettering of a board. */
	enum Font {
		/** Minecraft's own font. */
		DEFAULT(Style.DEFAULT_FONT_ID),
		/** Amber dot-matrix: round LEDs on a fixed pitch, never squeezed. */
		LED(AureliaTransitArchitecture.id("led")),
		/** A clean sans for the colour screens. */
		SANS(AureliaTransitArchitecture.id("sans")),
		SANS_BOLD(AureliaTransitArchitecture.id("sans_bold"));

		final Style style;

		Font(Identifier id) {
			this.style = Style.EMPTY.withFont(id);
		}

		OrderedText ordered(String text) {
			return Text.literal(text).setStyle(style).asOrderedText();
		}
	}

	/** Blocks per virtual unit. */
	float scale;
	/** Virtual width and height of the screen area. */
	float width;
	float height;
	/** True when a marquee is active and the model must be rebuilt at the faster marquee cadence. */
	boolean marquee;
	/** True while the message strip scrolls: rebuilt at {@link MessageRotation#STEP_MILLIS} instead of the marquee cadence. */
	boolean stripScroll;
	/** True when the board shows a clock with seconds: rebuilt on each second. */
	boolean seconds;

	int rectCount;
	float[] rx = new float[32];
	float[] ry = new float[32];
	float[] rw = new float[32];
	float[] rh = new float[32];
	int[] rc = new int[32];
	int[] rl = new int[32];

	int textCount;
	OrderedText[] ts = new OrderedText[32];
	float[] tx = new float[32];
	float[] ty = new float[32];
	float[] tk = new float[32];
	float[] tsx = new float[32];
	int[] tc = new int[32];
	int[] tl = new int[32];

	/**
	 * Lines that scroll right to left through a window, moved by the renderer every frame: they enter at the right edge,
	 * leave at the left and start again. {@code sa} holds each character's left edge (unscaled font units, one more entry
	 * for the end), so the renderer draws only the characters inside the window.
	 */
	int scrollCount;
	String[] ss = new String[4];
	Font[] sf = new Font[4];
	float[][] sa = new float[4][];
	float[] sx0 = new float[4];
	float[] sx1 = new float[4];
	float[] sy = new float[4];
	float[] sk = new float[4];
	int[] sc = new int[4];
	/** Virtual units per second. */
	float[] sv = new float[4];
	/** True to move in whole font pixels, as an LED board steps column by column. */
	boolean[] sq = new boolean[4];
	/** The window last drawn for each scrolling line and its text, so a frame that shows the same characters reuses it. */
	int[] sFirst = new int[4];
	int[] sEnd = new int[4];
	OrderedText[] sVisible = new OrderedText[4];

	void reset() {
		rectCount = 0;
		textCount = 0;
		scrollCount = 0;
		marquee = false;
		stripScroll = false;
		seconds = false;
		Arrays.fill(ts, null);
		Arrays.fill(ss, null);
		Arrays.fill(sa, null);
		Arrays.fill(sVisible, null);
	}

	void rect(float x, float y, float w, float h, int argb, int layer) {
		if (rectCount == rx.length) {
			final int n = rectCount * 2;
			rx = Arrays.copyOf(rx, n);
			ry = Arrays.copyOf(ry, n);
			rw = Arrays.copyOf(rw, n);
			rh = Arrays.copyOf(rh, n);
			rc = Arrays.copyOf(rc, n);
			rl = Arrays.copyOf(rl, n);
		}
		rx[rectCount] = x;
		ry[rectCount] = y;
		rw[rectCount] = w;
		rh[rectCount] = h;
		rc[rectCount] = argb;
		rl[rectCount] = layer;
		rectCount++;
	}

	/**
	 * @param k  uniform text scale (1 = normal 8-unit line)
	 * @param sx extra horizontal squeeze (1 = none)
	 */
	void text(String text, float x, float y, float k, float sx, int argb) {
		text(text, Font.DEFAULT, x, y, k, sx, argb, TEXT_NORMAL);
	}

	void text(String text, Font font, float x, float y, float k, float sx, int argb, int layer) {
		if (text.isEmpty()) {
			return;
		}
		if (textCount == ts.length) {
			final int n = textCount * 2;
			ts = Arrays.copyOf(ts, n);
			tx = Arrays.copyOf(tx, n);
			ty = Arrays.copyOf(ty, n);
			tk = Arrays.copyOf(tk, n);
			tsx = Arrays.copyOf(tsx, n);
			tc = Arrays.copyOf(tc, n);
			tl = Arrays.copyOf(tl, n);
		}
		ts[textCount] = font.ordered(text);
		tx[textCount] = x;
		ty[textCount] = y;
		tk[textCount] = k;
		tsx[textCount] = sx;
		tc[textCount] = argb;
		tl[textCount] = layer;
		textCount++;
	}

	/**
	 * A line scrolling through the window {@code x0..x1}. The caller masks the strips just outside the window, which must
	 * lie on the board and be at least as wide as the widest character.
	 */
	void scroll(String text, Font font, float[] advances, float x0, float x1, float y, float k, int argb, float speed, boolean stepped) {
		if (text.isEmpty()) {
			return;
		}
		if (scrollCount == ss.length) {
			final int n = scrollCount * 2;
			ss = Arrays.copyOf(ss, n);
			sf = Arrays.copyOf(sf, n);
			sa = Arrays.copyOf(sa, n);
			sx0 = Arrays.copyOf(sx0, n);
			sx1 = Arrays.copyOf(sx1, n);
			sy = Arrays.copyOf(sy, n);
			sk = Arrays.copyOf(sk, n);
			sc = Arrays.copyOf(sc, n);
			sv = Arrays.copyOf(sv, n);
			sq = Arrays.copyOf(sq, n);
			sFirst = Arrays.copyOf(sFirst, n);
			sEnd = Arrays.copyOf(sEnd, n);
			sVisible = Arrays.copyOf(sVisible, n);
		}
		ss[scrollCount] = text;
		sf[scrollCount] = font;
		sa[scrollCount] = advances;
		sx0[scrollCount] = x0;
		sx1[scrollCount] = x1;
		sy[scrollCount] = y;
		sk[scrollCount] = k;
		sc[scrollCount] = argb;
		sv[scrollCount] = speed;
		sq[scrollCount] = stepped;
		sVisible[scrollCount] = null;
		scrollCount++;
	}

	/** Characters {@code first..end} of scrolling line {@code i} as drawable text, rebuilt only when the window moves. */
	OrderedText visible(int i, int first, int end) {
		if (sVisible[i] == null || sFirst[i] != first || sEnd[i] != end) {
			sVisible[i] = sf[i].ordered(ss[i].substring(first, end));
			sFirst[i] = first;
			sEnd[i] = end;
		}
		return sVisible[i];
	}

	/**
	 * Where scrolling line {@code i} starts at {@code now} (virtual x of its first character). The line enters at the
	 * window's right edge and has left at its left edge one cycle later; the phase is wall-clock time, so a rebuild never
	 * makes it jump.
	 */
	float scrollStart(int i, long now) {
		final float width = sa[i][sa[i].length - 1] * sk[i];
		final float cycle = width + (sx1[i] - sx0[i]);
		final long cycleMillis = Math.max(1, (long) (cycle / sv[i] * 1000));
		float offset = (now % cycleMillis) * sv[i] / 1000F;
		if (sq[i]) {
			offset = (float) Math.floor(offset / sk[i]) * sk[i];
		}
		return sx1[i] - offset;
	}
}
