package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.station.world.GuidePages;
import com.aureliatransit.architecture.station.world.GuidePages.Click;
import com.aureliatransit.architecture.station.world.GuidePages.Line;
import com.aureliatransit.architecture.station.world.GuidePages.Page;
import com.aureliatransit.architecture.station.world.StationGuide;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Station Codex: {@link StationGuide}'s pages as an open book, two pages at a time, with a tab per chapter down the
 * right edge, a search box above, turning block showcases, and lines that run a command, put one in the chat box or turn to a
 * page. It reopens on the spread it was left on until the game closes. Nothing here runs while the screen is shut.
 */
public final class CodexScreen extends Screen {

	private static final int PAGE_WIDTH = 132;
	private static final int PAGE_HEIGHT = 160;
	/** The text column: the width and line count the guide's tests fit every page into. */
	private static final int TEXT_WIDTH = 114;
	private static final int LINES = 14;
	private static final int LINE_HEIGHT = 9;
	private static final int MARGIN = (PAGE_WIDTH - TEXT_WIDTH) / 2;
	private static final int TAB_WIDTH = 22;
	private static final int TAB_HEIGHT = 19;
	private static final int SEARCH_HEIGHT = 14;
	private static final int RESULTS = LINES - 3;

	private static final int COVER = 0xFF5A2E1E;
	private static final int PAPER = 0xFFF4ECD8;
	private static final int PAPER_EDGE = 0xFFE4D8BC;
	private static final int TAB = 0xFFE0D3B4;
	private static final int INK = 0x2B1D14;
	private static final int FAINT = 0x8A7A66;
	private static final int LINK = 0x1F4FA8;

	/** The left page of the spread the Codex was last left on. */
	private static int lastSpread;

	private final List<Page> pages = StationGuide.PAGES;
	private final List<Hit> hits = new ArrayList<>();
	private int spread;
	private TextFieldWidget search;

	/** Where a line, a tab or a showcased block was drawn this frame, for clicks and tooltips. */
	private record Hit(int x1, int y1, int x2, int y2, Line line, List<Text> tooltip, int page) {

		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private CodexScreen() {
		super(Text.literal(StationGuide.TITLE));
	}

	public static void open() {
		MinecraftClient.getInstance().setScreen(new CodexScreen());
	}

	private int left() {
		return (width - 2 * PAGE_WIDTH - TAB_WIDTH) / 2;
	}

	private int top() {
		return (height - PAGE_HEIGHT + SEARCH_HEIGHT + 4) / 2;
	}

	@Override
	protected void init() {
		spread = Math.max(0, Math.min(lastSpread, pages.size() - 1)) & ~1;
		final String query = search == null ? "" : search.getText();
		search = new TextFieldWidget(textRenderer, left() + PAGE_WIDTH + MARGIN, top() - SEARCH_HEIGHT - 4, PAGE_WIDTH - 2 * MARGIN,
				SEARCH_HEIGHT, Text.literal("Search"));
		search.setMaxLength(40);
		search.setText(query);
		search.setPlaceholder(Text.literal("Search the Codex").formatted(Formatting.GRAY));
		addDrawableChild(search);
		addDrawableChild(new PageTurnWidget(left() + MARGIN, top() + PAGE_HEIGHT - 17, false, button -> turn(-2), true));
		addDrawableChild(new PageTurnWidget(left() + 2 * PAGE_WIDTH - MARGIN - 23, top() + PAGE_HEIGHT - 17, true, button -> turn(2), true));
	}

	private void turn(int by) {
		jump(spread + by);
	}

	/** Shows the spread holding 0-based {@code page}, and closes the search. */
	private void jump(int page) {
		spread = Math.max(0, Math.min(page, pages.size() - 1)) & ~1;
		lastSpread = spread;
		if (search != null && !search.getText().isEmpty()) {
			search.setText("");
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		hits.clear();
		final int left = left();
		final int top = top();
		// cover, two pages and the spine
		context.fill(left - 4, top - 4, left + 2 * PAGE_WIDTH + 4, top + PAGE_HEIGHT + 4, COVER);
		context.fill(left, top, left + 2 * PAGE_WIDTH, top + PAGE_HEIGHT, PAPER);
		context.fill(left + PAGE_WIDTH - 2, top, left + PAGE_WIDTH + 2, top + PAGE_HEIGHT, PAPER_EDGE);
		tabs(context, left + 2 * PAGE_WIDTH + 4, top + 4, mouseX, mouseY);

		final String query = search.getText().strip();
		if (query.isEmpty()) {
			page(context, spread, left, top, mouseX, mouseY);
		} else {
			results(context, query, left, top, mouseX, mouseY);
		}
		page(context, spread + 1, left + PAGE_WIDTH, top, mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);

		for (final Hit hit : hits) {
			if (hit.contains(mouseX, mouseY) && hit.tooltip() != null) {
				context.drawTooltip(textRenderer, hit.tooltip(), mouseX, mouseY);
				break;
			}
		}
	}

	/** The contents tab, then one tab per chapter with its icon; the open chapter's tab joins the page. */
	private void tabs(DrawContext context, int x, int y, int mouseX, int mouseY) {
		final int open = GuidePages.chapterAt(StationGuide.CHAPTERS, spread);
		final int[] first = GuidePages.firstPages(StationGuide.CHAPTERS);
		for (int i = -1; i < StationGuide.CHAPTERS.size(); i++) {
			final int ty = y + (i + 1) * (TAB_HEIGHT + 2);
			final boolean here = i == open;
			context.fill(x - (here ? 4 : 0), ty, x + TAB_WIDTH, ty + TAB_HEIGHT, COVER);
			context.fill(x - (here ? 4 : 0), ty + 1, x + TAB_WIDTH - 1, ty + TAB_HEIGHT - 1, here ? PAPER : TAB);
			final String icon = i < 0 ? "minecraft:book" : StationGuide.CHAPTERS.get(i).icon();
			context.drawItem(new ItemStack(Registries.ITEM.get(new Identifier(icon))), x + 3, ty + 2);
			final String title = i < 0 ? "Contents" : StationGuide.CHAPTERS.get(i).title();
			hits.add(new Hit(x, ty, x + TAB_WIDTH, ty + TAB_HEIGHT, null, List.of(Text.literal(title)), i < 0 ? 0 : first[i] - 1));
		}
	}

	private void page(DrawContext context, int index, int x, int top, int mouseX, int mouseY) {
		if (index >= pages.size()) {
			return;
		}
		final Page page = pages.get(index);
		draw(context, page, x, top, mouseX, mouseY);
		final String number = Integer.toString(index + 1);
		context.drawText(textRenderer, number, x + (PAGE_WIDTH - textRenderer.getWidth(number)) / 2, top + PAGE_HEIGHT - 13, FAINT, false);
		if (!page.blocks().isEmpty()) {
			showcase(context, page.blocks(), x + MARGIN, top + MARGIN + (LINES - GuidePages.SHOWCASE_LINES) * LINE_HEIGHT, mouseX, mouseY);
		}
	}

	/** The search results as a page of links, best first. */
	private void results(DrawContext context, String query, int x, int top, int mouseX, int mouseY) {
		final List<Line> lines = new ArrayList<>();
		final List<Integer> found = GuidePages.search(pages, query, I18n::translate, RESULTS);
		lines.add(Line.text(found.isEmpty() ? "Nothing found." : "Click one to turn to it."));
		for (final int i : found) {
			final Page page = pages.get(i);
			lines.add(new Line((i + 1) + "  " + page.heading(), false, Click.PAGE, Integer.toString(i + 1), "Page " + (i + 1)));
		}
		draw(context, new Page("Search", lines), x, top, mouseX, mouseY);
	}

	/** A page's heading and lines, wrapped to the text column; clickable lines are blue, underlined under the mouse. */
	private void draw(DrawContext context, Page page, int x, int top, int mouseX, int mouseY) {
		final int textX = x + MARGIN;
		int y = top + MARGIN;
		for (final OrderedText row : textRenderer.wrapLines(Text.literal(page.heading()).formatted(Formatting.BOLD), TEXT_WIDTH)) {
			context.drawText(textRenderer, row, textX, y, INK, false);
			y += LINE_HEIGHT;
		}
		y += LINE_HEIGHT;
		for (final Line line : page.lines()) {
			final String shown = line.key() ? I18n.translate(line.text()) : line.text();
			final List<OrderedText> rows = new ArrayList<>();
			final boolean link = line.click() != Click.NONE;
			int height = 0;
			for (final String paragraph : shown.split("\n", -1)) {
				height += paragraph.isEmpty() ? 1 : textRenderer.wrapLines(Text.literal(paragraph), TEXT_WIDTH).size();
			}
			final boolean hovered = link && mouseX >= textX && mouseX < textX + TEXT_WIDTH && mouseY >= y && mouseY < y + height * LINE_HEIGHT;
			final Style style = link ? Style.EMPTY.withUnderline(hovered) : Style.EMPTY;
			for (final String paragraph : shown.split("\n", -1)) {
				if (paragraph.isEmpty()) {
					rows.add(OrderedText.EMPTY);
				} else {
					rows.addAll(textRenderer.wrapLines(Text.literal(paragraph).setStyle(style), TEXT_WIDTH));
				}
			}
			if (link) {
				hits.add(new Hit(textX, y, textX + TEXT_WIDTH, y + rows.size() * LINE_HEIGHT, line, tooltip(line), -1));
			}
			for (final OrderedText row : rows) {
				context.drawText(textRenderer, row, textX, y, link ? LINK : INK, false);
				y += LINE_HEIGHT;
			}
		}
	}

	private static List<Text> tooltip(Line line) {
		final List<Text> tooltip = new ArrayList<>();
		if (line.hover() != null) {
			tooltip.add(Text.literal(line.hover()));
		}
		tooltip.add(Text.literal(switch (line.click()) {
			case RUN -> "Click to run";
			case CHAT -> "Click to put in chat";
			default -> "Click to turn there";
		}).formatted(Formatting.GRAY));
		return tooltip;
	}

	/** Up to three blocks turning slowly side by side, each named on hover. */
	private void showcase(DrawContext context, List<String> blocks, int x, int y, int mouseX, int mouseY) {
		final int count = Math.min(3, blocks.size());
		final int cell = TEXT_WIDTH / count;
		final float angle = (System.currentTimeMillis() % 9000L) / 9000f * 360f;
		for (int i = 0; i < count; i++) {
			final BlockState state = Registries.BLOCK.get(new Identifier(blocks.get(i))).getDefaultState();
			final int cx = x + i * cell + cell / 2;
			final int cy = y + GuidePages.SHOWCASE_LINES * LINE_HEIGHT / 2;
			block(context, state, cx, cy, angle + i * 40f);
			hits.add(new Hit(cx - cell / 2, y, cx + cell / 2, y + GuidePages.SHOWCASE_LINES * LINE_HEIGHT, null,
					List.of(state.getBlock().getName()), -1));
		}
	}

	/** One block's model, about 24 px across, tilted towards the reader and turned by {@code angle}. */
	private void block(DrawContext context, BlockState state, int cx, int cy, float angle) {
		if (state.getRenderType() != BlockRenderType.MODEL) {
			final MatrixStack matrices = context.getMatrices();
			matrices.push();
			matrices.translate(cx - 16, cy - 16, 0);
			matrices.scale(2, 2, 1);
			context.drawItem(new ItemStack(state.getBlock()), 0, 0);
			matrices.pop();
			return;
		}
		final MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(cx, cy, 150);
		matrices.scale(20, -20, 20);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
		matrices.translate(-0.5, -0.5, -0.5);
		DiffuseLighting.enableGuiDepthLighting();
		client.getBlockRenderManager().renderBlockAsEntity(state, matrices, context.getVertexConsumers(),
				LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
		context.draw();
		DiffuseLighting.disableGuiDepthLighting();
		matrices.pop();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (final Hit hit : hits) {
			if (!hit.contains(mouseX, mouseY)) {
				continue;
			}
			if (hit.line() == null) {
				if (hit.page() < 0) {
					continue;
				}
				jump(hit.page());
				return true;
			}
			return act(hit.line());
		}
		return false;
	}

	private boolean act(Line line) {
		switch (line.click()) {
			case RUN -> {
				close();
				if (client.player != null) {
					client.player.networkHandler.sendChatCommand(line.value().substring(1));
				}
			}
			case CHAT -> client.setScreen(new ChatScreen(line.value()));
			case PAGE -> jump(Integer.parseInt(line.value()) - 1);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!search.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
				turn(-2);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
				turn(2);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		turn(amount > 0 ? -2 : 2);
		return true;
	}

	@Override
	public void removed() {
		lastSpread = spread;
	}
}
