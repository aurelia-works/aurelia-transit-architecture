package com.aureliatransit.architecture.registry;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block item with optional usage hints shown in the tooltip.
 */
public class DescribedBlockItem extends BlockItem {

	private final List<String> tooltipKeys;

	public DescribedBlockItem(Block block, Settings settings, List<String> tooltipKeys) {
		super(block, settings);
		this.tooltipKeys = tooltipKeys;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		for (final String key : tooltipKeys) {
			tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
		}
	}
}
