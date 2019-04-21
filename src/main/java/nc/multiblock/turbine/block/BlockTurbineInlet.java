package nc.multiblock.turbine.block;

import nc.multiblock.turbine.tile.TileTurbineInlet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockTurbineInlet extends BlockTurbinePartBase {

	public BlockTurbineInlet() {
		super("turbine_inlet");
	}

	@Override
	public TileEntity createNewTileEntity(World world, int metadata) {
		return new TileTurbineInlet();
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (player == null) return false;
		if (hand != EnumHand.MAIN_HAND || player.isSneaking()) return false;
		return rightClickOnPart(world, pos, player, hand, facing);
	}
}
