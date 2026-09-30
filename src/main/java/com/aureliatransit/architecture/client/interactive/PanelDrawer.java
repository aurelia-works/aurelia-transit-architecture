package com.aureliatransit.architecture.client.interactive;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.text.PanelLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws pure {@link PanelLayout.Panel}s onto a model face in the world. Shared by sign, information and clock
 * renderers so there is exactly one implementation of "text and coloured rectangles on a block face".
 *
 * <p>Usage: {@link #beginFace} with the matrices positioned at the block (already rotated to its facing), draw, then
 * {@link #endFace}. Inside a face, x points to the viewer's right, y up and z towards the viewer; units are model pixels
 * for {@link #fill} and layout coordinates.
 */
public final class PanelDrawer {

	private static final Identifier FILL_TEXTURE = AureliaTransitArchitecture.id("textures/block/panel_fill.png");
	private static final float SURFACE_OFFSET = 0.004F;
	private static final float TEXT_LIFT = 0.0015F;
	/** Vertical offset (font units) from a glyph row's top to its visual centre. */
	private static final float GLYPH_CENTER = 4.0F;

	private PanelDrawer() {
	}

	/**
	 * Moves to the centre of a panel on a face. {@code front} faces are the model's north side (viewed from -Z), the
	 * others the south side.
	 */
	public static void beginFace(MatrixStack matrices, float centerXPx, float centerYPx, float zPx, boolean front) {
		matrices.push();
		matrices.translate(centerXPx / 16, centerYPx / 16, zPx / 16 + (front ? -SURFACE_OFFSET : SURFACE_OFFSET));
		if (front) {
			// The model front faces north (-Z); text and quads are built facing +Z.
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
		}
	}

	public static void endFace(MatrixStack matrices) {
		matrices.pop();
	}

	/**
	 * Draws a whole panel (rectangles, then text) at the current face position.
	 */
	public static void panel(MatrixStack matrices, VertexConsumerProvider consumers, TextRenderer textRenderer, PanelLayout.Panel panel, int light) {
		if (!panel.rects().isEmpty()) {
			final VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FILL_TEXTURE));
			for (final PanelLayout.Rect rect : panel.rects()) {
				quad(matrices, buffer, rect.cx(), rect.cy(), rect.w(), rect.h(), rect.argb(), 0, light);
			}
		}
		for (final PanelLayout.Label label : panel.labels()) {
			text(matrices, consumers, textRenderer, label, light);
		}
	}

	public static void text(MatrixStack matrices, VertexConsumerProvider consumers, TextRenderer textRenderer, PanelLayout.Label label, int light) {
		matrices.push();
		matrices.translate(label.cx() / 16, label.cy() / 16, TEXT_LIFT);
		final float scale = label.scale() / 16;
		matrices.scale(scale, -scale, scale);
		textRenderer.draw(label.text(), -label.widthUnits() / 2F, -GLYPH_CENTER, label.argb(), false, matrices.peek().getPositionMatrix(), consumers,
				TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
		matrices.pop();
	}

	/**
	 * A filled rectangle centred on (cx, cy) in model pixels, lifted {@code lift} blocks off the face.
	 */
	public static void fill(MatrixStack matrices, VertexConsumerProvider consumers, float cx, float cy, float w, float h, int argb, float lift, int light) {
		quad(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FILL_TEXTURE)), cx, cy, w, h, argb, lift, light);
	}

	private static void quad(MatrixStack matrices, VertexConsumer buffer, float cx, float cy, float w, float h, int argb, float lift, int light) {
		final MatrixStack.Entry entry = matrices.peek();
		final Matrix4f position = entry.getPositionMatrix();
		final Matrix3f normal = entry.getNormalMatrix();
		final float x1 = (cx - w / 2) / 16;
		final float x2 = (cx + w / 2) / 16;
		final float y1 = (cy - h / 2) / 16;
		final float y2 = (cy + h / 2) / 16;
		final int a = (argb >>> 24) & 0xFF;
		final int r = (argb >> 16) & 0xFF;
		final int g = (argb >> 8) & 0xFF;
		final int b = argb & 0xFF;
		vertex(buffer, position, normal, x1, y1, lift, r, g, b, a, light);
		vertex(buffer, position, normal, x2, y1, lift, r, g, b, a, light);
		vertex(buffer, position, normal, x2, y2, lift, r, g, b, a, light);
		vertex(buffer, position, normal, x1, y2, lift, r, g, b, a, light);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x, float y, float z, int r, int g, int b, int a, int light) {
		buffer.vertex(position, x, y, z).color(r, g, b, a).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0, 0, 1).next();
	}
}
