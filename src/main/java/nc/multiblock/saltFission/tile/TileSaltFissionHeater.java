package nc.multiblock.saltFission.tile;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import nc.ModCheck;
import nc.config.NCConfig;
import nc.multiblock.cuboidal.CuboidalPartPositionType;
import nc.multiblock.saltFission.SaltFissionHeaterSetting;
import nc.multiblock.saltFission.SaltFissionReactor;
import nc.recipe.AbstractRecipeHandler;
import nc.recipe.NCRecipes;
import nc.recipe.ProcessorRecipe;
import nc.recipe.ProcessorRecipeHandler;
import nc.recipe.RecipeInfo;
import nc.recipe.RecipeMatchResult;
import nc.recipe.ingredient.IFluidIngredient;
import nc.tile.fluid.ITileFluid;
import nc.tile.internal.fluid.FluidConnection;
import nc.tile.internal.fluid.FluidTileWrapper;
import nc.tile.internal.fluid.GasTileWrapper;
import nc.tile.internal.fluid.Tank;
import nc.tile.internal.fluid.TankSorption;
import nc.tile.passive.ITilePassive;
import nc.tile.processor.IFluidProcessor;
import nc.util.BlockPosHelper;
import nc.util.FluidStackHelper;
import nc.util.GasHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class TileSaltFissionHeater extends TileSaltFissionPartBase implements IFluidProcessor, ITileFluid {
	
	private final @Nonnull List<Tank> tanks = Lists.newArrayList(new Tank(FluidStackHelper.INGOT_BLOCK_VOLUME*2, NCRecipes.coolant_heater_valid_fluids.get(0)), new Tank(FluidStackHelper.INGOT_BLOCK_VOLUME*4, new ArrayList<String>()));
	
	private @Nonnull FluidConnection[] fluidConnections = ITileFluid.fluidConnectionAll(Lists.newArrayList(TankSorption.IN, TankSorption.OUT));
	
	private @Nonnull FluidTileWrapper[] fluidSides;
	
	private @Nonnull GasTileWrapper gasWrapper;
	
	private @Nonnull SaltFissionHeaterSetting[] heaterSettings = new SaltFissionHeaterSetting[] {SaltFissionHeaterSetting.DEFAULT, SaltFissionHeaterSetting.DEFAULT, SaltFissionHeaterSetting.DEFAULT, SaltFissionHeaterSetting.DEFAULT, SaltFissionHeaterSetting.DEFAULT, SaltFissionHeaterSetting.DEFAULT};
	
	public final int fluidInputSize = 1, fluidOutputSize = 1;
	
	private double baseProcessCooling;
	public final int baseProcessTime = 20;
	public double reactorCoolingEfficiency; // Based on the reactor efficiency, but with heat/cooling taken into account
	public boolean checked = false, isInValidPosition;
	
	private int fluidToHold;
	
	public double time;
	public boolean isProcessing, canProcessInputs;
	
	public final NCRecipes.Type recipeType = NCRecipes.Type.COOLANT_HEATER;
	protected RecipeInfo<ProcessorRecipe> recipeInfo, cachedRecipeInfo;
	
	protected int heaterCount;
	
	public TileSaltFissionHeater() {
		super(CuboidalPartPositionType.INTERIOR);
		fluidSides = ITileFluid.getDefaultFluidSides(this);
		gasWrapper = new GasTileWrapper(this);
	}
	
	@Override
	public void onMachineAssembled(SaltFissionReactor controller) {
		doStandardNullControllerResponse(controller);
		super.onMachineAssembled(controller);
		if (getWorld().isRemote) return;
	}
	
	@Override
	public void onMachineBroken() {
		super.onMachineBroken();
		if (getWorld().isRemote) return;
		//getWorld().setBlockState(getPos(), getWorld().getBlockState(getPos()), 2);
	}
	
	public void checkIsInValidPosition(short heaterCheckCount) {
		String coolantName = getCoolantName();
				
		if (coolantName.equals("nullFluid")) {
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("nak") && heaterCheckCount == 0) {
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isVessel(dir) || isModerator(dir)) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("redstone_nak") && heaterCheckCount == 0) {
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isVessel(dir)) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}

		else if (coolantName.equals("quartz_nak") && heaterCheckCount == 0) {
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isModerator(dir)) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("gold_nak") && heaterCheckCount == 1) {
			boolean nak = false;
			boolean redstone = false;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (!nak) if (isHeaterWithCoolant(dir, "nak")) nak = true;
				if (!redstone) if (isHeaterWithCoolant(dir, "redstone_nak")) redstone = true;
				if (nak && redstone) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("glowstone_nak") && heaterCheckCount == 0) {
			short moderator = 0;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isModerator(dir)) moderator++;
				if (moderator >= 2) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("lapis_nak") && heaterCheckCount == 0) {
			boolean vessel = false;
			boolean wall = false;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (!vessel) if (isVessel(dir)) vessel = true;
				if (!wall) if (isWall(dir)) wall = true;
				if (vessel && wall) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("diamond_nak") && heaterCheckCount == 1) {
			boolean nak = false;
			boolean quartz = false;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (!nak) if (isHeaterWithCoolant(dir, "nak")) nak = true;
				if (!quartz) if (isHeaterWithCoolant(dir, "quartz_nak")) quartz = true;
				if (nak && quartz) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("liquidhelium_nak") && heaterCheckCount == 1) {
			boolean wall = false;
			short redstone = 0;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (!wall) if (isWall(dir)) wall = true;
				if (isHeaterWithCoolant(dir, "redstone_nak")) redstone++;
				if (redstone > 1) {
					isInValidPosition = false;
					checked = true;
					return;
				}
			}
			isInValidPosition = wall && redstone == 1;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("ender_nak") && heaterCheckCount == 0) {
			short vertices = 0;
			posList: for (EnumFacing[] vertexDirList : BlockPosHelper.vertexDirList()) {
				for (EnumFacing dir : vertexDirList) if (!isWall(dir)) continue posList;
				vertices++;
				if (vertices > 1) {
					isInValidPosition = false;
					checked = true;
					return;
				}
			}
			isInValidPosition = vertices == 1;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("cryotheum_nak") && heaterCheckCount == 0) {
			short vessel = 0;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isVessel(dir)) vessel++;
				if (vessel >= 2) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("iron_nak") && heaterCheckCount == 2) {
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isHeaterWithCoolant(dir, "gold_nak")) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("emerald_nak") && heaterCheckCount == 0) {
			boolean vessel = false;
			boolean moderator = false;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (!vessel) if (isVessel(dir)) vessel = true;
				if (!moderator) if (isModerator(dir)) moderator = true;
				if (vessel && moderator) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("copper_nak") && heaterCheckCount == 1) {
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (isHeaterWithCoolant(dir, "glowstone_nak")) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("tin_nak") && heaterCheckCount == 1) {
			posList: for (EnumFacing[] axialsDirList : BlockPosHelper.axialsDirList()) {
				for (EnumFacing dir : axialsDirList) if (!isHeaterWithCoolant(dir, "lapis_nak")) continue posList;
				isInValidPosition = true;
				checked = true;
				return;
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		else if (coolantName.equals("magnesium_nak") && heaterCheckCount == 0) {
			boolean moderator = false;
			boolean wall = false;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (!moderator) if (isModerator(dir)) moderator = true;
				if (!wall) if (isWall(dir)) wall = true;
				if (moderator && wall) {
					isInValidPosition = true;
					checked = true;
					return;
				}
			}
			isInValidPosition = false;
			checked = true;
			return;
		}
		
		isInValidPosition = false;
	}
	
	public String getCoolantName() {
		return tanks.get(0).getFluidName();
	}
	
	private boolean isWall(EnumFacing dir) {
		TileEntity tile = world.getTileEntity(pos.offset(dir));
		if (!(tile instanceof TileSaltFissionPartBase)) return false;
		TileSaltFissionPartBase part = (TileSaltFissionPartBase) tile;
		return part.getPartPositionType() == CuboidalPartPositionType.WALL;
	}
	
	private boolean isHeaterWithCoolant(EnumFacing dir, String name) {
		TileEntity tile = world.getTileEntity(pos.offset(dir));
		if (!(tile instanceof TileSaltFissionHeater)) return false;
		TileSaltFissionHeater heater = (TileSaltFissionHeater) tile;
		return heater.tanks.get(0).getFluidName().equals(name) && heater.isInValidPosition;
	}
	
	private boolean isModerator(EnumFacing dir) {
		TileEntity tile = world.getTileEntity(pos.offset(dir));
		if (!(tile instanceof TileSaltFissionModerator)) return false;
		TileSaltFissionModerator moderator = (TileSaltFissionModerator) tile;
		return moderator.isInValidPosition;
	}
	
	private boolean isVessel(EnumFacing dir) {
		if (!isMultiblockAssembled()) return false;
		TileEntity tile = world.getTileEntity(pos.offset(dir));
		if (!(tile instanceof TileSaltFissionVessel)) return false;
		TileSaltFissionVessel vessel = (TileSaltFissionVessel) tile;
		return vessel.canProcessInputs;
	}
	
	// Ticking
	
	@Override
	public void onAdded() {
		super.onAdded();
		if (!world.isRemote) {
			refreshRecipe();
			refreshActivity();
			isProcessing = isProcessing();
		}
	}
	
	@Override
	public void update() {
		super.update();
		updateHeater();
	}
	
	public void updateHeater() {
		if (!world.isRemote) {
			setIsReactorOn();
			boolean wasProcessing = isProcessing;
			isProcessing = isProcessing();
			boolean shouldUpdate = false;
			tickHeater();
			if (isProcessing) process();
			if (wasProcessing != isProcessing) {
				shouldUpdate = true;
			}
			if (heaterCount == 0) {
				pushFluid();
				refreshRecipe();
				refreshActivity();
			}
			if (shouldUpdate) markDirty();
		}
	}
	
	public void tickHeater() {
		heaterCount++; heaterCount %= NCConfig.machine_update_rate / 2;
	}
	
	@Override
	public void refreshRecipe() {
		RecipeMatchResult matchResult = recipeInfo == null ? RecipeMatchResult.FAIL : recipeInfo.getRecipe().matchInputs(new ArrayList<ItemStack>(), getFluidInputs());
		if (!matchResult.matches()) {
			/** Temporary caching while looking for recipe map solution */
			matchResult = cachedRecipeInfo == null ? RecipeMatchResult.FAIL : cachedRecipeInfo.getRecipe().matchInputs(new ArrayList<ItemStack>(), getFluidInputs());
			if (matchResult.matches()) {
				recipeInfo = new RecipeInfo(cachedRecipeInfo.getRecipe(), matchResult);
			}
			else {
				recipeInfo = getRecipeHandler().getRecipeInfoFromInputs(new ArrayList<ItemStack>(), getFluidInputs());
			}
			if (recipeInfo != null) {
				cachedRecipeInfo = recipeInfo;
			}
		}
		else recipeInfo = new RecipeInfo(recipeInfo.getRecipe(), matchResult);
	}
	
	@Override
	public void refreshActivity() {
		canProcessInputs = canProcessInputs(false);
	}
	
	@Override
	public void refreshActivityOnProduction() {
		canProcessInputs = canProcessInputs(true);
	}
	
	// Processor Stats
	
	public double getSpeedMultiplier() {
		return reactorCoolingEfficiency;
	}
	
	public double getProcessCooling() {
		return 0.05D*NCConfig.salt_fission_cooling_max_rate*baseProcessCooling;
	}
	
	public boolean setRecipeStats() {
		if (recipeInfo == null) {
			baseProcessCooling = 0D;
			return false;
		}
		baseProcessCooling = recipeInfo.getRecipe().getCoolantHeaterCoolingRate();
		fluidToHold = getFluidToHold();
		return true;
	}
	
	private int getFluidToHold() {
		return Math.min(FluidStackHelper.INGOT_BLOCK_VOLUME/2, getFluidIngredients().get(0).getMaxStackSize(recipeInfo.getFluidIngredientNumbers().get(0))*NCConfig.machine_update_rate / 2);
	}
	
	// Processing
	
	public boolean isProcessing() {
		return readyToProcess() && isReactorOn;
	}
	
	public boolean readyToProcess() {
		return canProcessInputs && isInValidPosition && isMultiblockAssembled();
	}
	
	public boolean canProcessInputs(boolean justProduced) {
		if (!setRecipeStats()) return false;
		else if (!justProduced && time >= baseProcessTime) return true;
		return canProduceProducts();
	}
	
	public boolean canProduceProducts() {
		for (int j = 0; j < fluidOutputSize; j++) {
			IFluidIngredient fluidProduct = getFluidProducts().get(j);
			if (fluidProduct.getMaxStackSize(0) <= 0) continue;
			if (fluidProduct.getStack() == null) return false;
			else if (!tanks.get(j + fluidInputSize).isEmpty()) {
				if (!tanks.get(j + fluidInputSize).getFluid().isFluidEqual(fluidProduct.getStack())) {
					return false;
				} else if (tanks.get(j + fluidInputSize).getFluidAmount() + fluidProduct.getMaxStackSize(0) > tanks.get(j + fluidInputSize).getCapacity()) {
					return false;
				}
			}
		}
		return true;
	}
	
	public void process() {
		time += getSpeedMultiplier();
		if (time >= baseProcessTime) finishProcess();
	}
	
	public void finishProcess() {
		double oldProcessTime = baseProcessTime;
		produceProducts();
		refreshRecipe();
		if (!setRecipeStats()) time = 0;
		else time = MathHelper.clamp(time - oldProcessTime, 0D, baseProcessTime);
		refreshActivityOnProduction();
		if (!canProcessInputs) time = 0;
	}
	
	public void produceProducts() {
		if (recipeInfo == null) return;
		List<Integer> fluidInputOrder = recipeInfo.getFluidInputOrder();
		if (fluidInputOrder == AbstractRecipeHandler.INVALID) return;
		
		for (int i = 0; i < fluidInputSize; i++) {
			int fluidIngredientStackSize = getFluidIngredients().get(fluidInputOrder.get(i)).getMaxStackSize(recipeInfo.getFluidIngredientNumbers().get(i));
			if (fluidIngredientStackSize > 0) tanks.get(i).changeFluidAmount(-fluidIngredientStackSize);
			if (tanks.get(i).getFluidAmount() <= 0) tanks.get(i).setFluidStored(null);
		}
		for (int j = 0; j < fluidOutputSize; j++) {
			IFluidIngredient fluidProduct = getFluidProducts().get(j);
			if (fluidProduct.getMaxStackSize(0) <= 0) continue;
			if (tanks.get(j + fluidInputSize).isEmpty()) {
				tanks.get(j + fluidInputSize).setFluidStored(fluidProduct.getNextStack(0));
			} else if (tanks.get(j + fluidInputSize).getFluid().isFluidEqual(fluidProduct.getStack())) {
				tanks.get(j + fluidInputSize).changeFluidAmount(fluidProduct.getNextStackSize(0));
			}
		}
	}
	
	// IProcessor
	
	@Override
	public ProcessorRecipeHandler getRecipeHandler() {
		return recipeType.getRecipeHandler();
	}
	
	@Override
	public List<Tank> getFluidInputs() {
		return tanks.subList(0, fluidInputSize);
	}
	
	@Override
	public List<IFluidIngredient> getFluidIngredients() {
		return recipeInfo.getRecipe().fluidIngredients();
	}
	
	@Override
	public List<IFluidIngredient> getFluidProducts() {
		return recipeInfo.getRecipe().fluidProducts();
	}
	
	// Fluids
	
	@Override
	@Nonnull
	public List<Tank> getTanks() {
		return tanks;
	}

	@Override
	@Nonnull
	public FluidConnection[] getFluidConnections() {
		return fluidConnections;
	}
	
	@Override
	public void setFluidConnections(@Nonnull FluidConnection[] connections) {
		fluidConnections = connections;
	}

	@Override
	@Nonnull
	public FluidTileWrapper[] getFluidSides() {
		return fluidSides;
	}
	
	@Override
	public @Nonnull GasTileWrapper getGasWrapper() {
		return gasWrapper;
	}
	
	public @Nonnull SaltFissionHeaterSetting[] getHeaterSettings() {
		return heaterSettings;
	}
	
	public void setHeaterSettings(@Nonnull SaltFissionHeaterSetting[] settings) {
		heaterSettings = settings;
	}
	
	public SaltFissionHeaterSetting getHeaterSetting(@Nonnull EnumFacing side) {
		return heaterSettings[side.getIndex()];
	}
	
	public void setHeaterSetting(@Nonnull EnumFacing side, @Nonnull SaltFissionHeaterSetting setting) {
		heaterSettings[side.getIndex()] = setting;
	}
	
	public void toggleHeaterSetting(@Nonnull EnumFacing side) {
		setHeaterSetting(side, getHeaterSetting(side).next());
		switch (getHeaterSetting(side)) {
		case DEFAULT:
			setTankSorption(side, 0, TankSorption.IN);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		case DISABLED:
			setTankSorption(side, 0, TankSorption.NON);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		case HOT_COOLANT_OUT:
			setTankSorption(side, 0, TankSorption.NON);
			setTankSorption(side, 1, TankSorption.OUT);
			break;
		case COOLANT_SPREAD:
			setTankSorption(side, 0, TankSorption.OUT);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		default:
			setTankSorption(side, 0, TankSorption.IN);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		}
		markAndRefresh();
	}
	
	@Override
	public void pushFluidToSide(@Nonnull EnumFacing side) {
		SaltFissionHeaterSetting thisSetting = getHeaterSetting(side);
		if (thisSetting == SaltFissionHeaterSetting.DISABLED) return;
		
		TileEntity tile = getTileWorld().getTileEntity(getTilePos().offset(side));
		
		if (tile instanceof TileSaltFissionHeater) {
			TileSaltFissionHeater heater = (TileSaltFissionHeater)tile;
			SaltFissionHeaterSetting heaterSetting = heater.getHeaterSetting(side.getOpposite());
			
			if (thisSetting == SaltFissionHeaterSetting.COOLANT_SPREAD) {
				if (heaterSetting == SaltFissionHeaterSetting.DEFAULT) {
					pushCoolant(heater);
					pushHotCoolant(heater);
				} else if (heaterSetting == SaltFissionHeaterSetting.HOT_COOLANT_OUT) {
					pushCoolant(heater);
				}
			} else if (thisSetting == SaltFissionHeaterSetting.HOT_COOLANT_OUT && (heaterSetting == SaltFissionHeaterSetting.DEFAULT || heaterSetting == SaltFissionHeaterSetting.COOLANT_SPREAD)) {
				pushHotCoolant(heater);
			}
		}
		
		else if (thisSetting == SaltFissionHeaterSetting.HOT_COOLANT_OUT) {
			if (tile instanceof ITilePassive) if (!((ITilePassive) tile).canPushFluidsTo()) return;
			IFluidHandler adjStorage = tile == null ? null : tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
			
			if (adjStorage == null) return;
			
			for (int i = 0; i < getTanks().size(); i++) {
				if (getTanks().get(i).getFluid() == null || !getTankSorption(side, i).canDrain()) continue;
				
				getTanks().get(i).drainInternal(adjStorage.fill(getTanks().get(i).drainInternal(getTanks().get(i).getCapacity(), false), true), true);
			}
		}
	}
	
	public void pushCoolant(TileSaltFissionHeater other) {
		if (getTanks().get(0).getFluidAmount() > other.getTanks().get(0).getFluidAmount()) {
			getTanks().get(0).drainInternal(other.getTanks().get(0).fillInternal(getTanks().get(0).drainInternal(getTanks().get(0).getFluidAmount() - fluidToHold, false), true), true);
		}
	}
	
	public void pushHotCoolant(TileSaltFissionHeater other) {
		getTanks().get(1).drainInternal(other.getTanks().get(1).fillInternal(getTanks().get(1).drainInternal(getTanks().get(1).getCapacity(), false), true), true);
	}

	@Override
	public boolean getInputTanksSeparated() {
		return false;
	}

	@Override
	public void setInputTanksSeparated(boolean separated) {}

	@Override
	public boolean getVoidUnusableFluidInput(int tankNumber) {
		return false;
	}

	@Override
	public void setVoidUnusableFluidInput(int tankNumber, boolean voidUnusableFluidInput) {}

	@Override
	public boolean getVoidExcessFluidOutput(int tankNumber) {
		return false;
	}

	@Override
	public void setVoidExcessFluidOutput(int tankNumber, boolean voidExcessFluidOutput) {}
	
	@Override
	public boolean hasConfigurableFluidConnections() {
		return true;
	}
	
	// NBT
	
	public NBTTagCompound writeHeaterSettings(NBTTagCompound nbt) {
		NBTTagCompound settingsTag = new NBTTagCompound();
		for (EnumFacing side : EnumFacing.VALUES) {
			settingsTag.setInteger("setting" + side.getIndex(), getHeaterSetting(side).ordinal());
		}
		nbt.setTag("heaterSettings", settingsTag);
		return nbt;
	}
	
	public void readHeaterSettings(NBTTagCompound nbt) {
		if (nbt.hasKey("fluidConnections0")) {
			for (EnumFacing side : EnumFacing.VALUES) {
				TankSorption sorption = TankSorption.values()[nbt.getInteger("fluidConnections" + side.getIndex())];
				switch (sorption) {
				case BOTH:
					setTankSorption(side, 0, TankSorption.IN);
					setTankSorption(side, 1, TankSorption.NON);
					setHeaterSetting(side, SaltFissionHeaterSetting.DEFAULT);
					break;
				case NON:
					setTankSorption(side, 0, TankSorption.NON);
					setTankSorption(side, 1, TankSorption.NON);
					setHeaterSetting(side, SaltFissionHeaterSetting.DISABLED);
					break;
				case IN:
					setTankSorption(side, 0, TankSorption.NON);
					setTankSorption(side, 1, TankSorption.OUT);
					setHeaterSetting(side, SaltFissionHeaterSetting.HOT_COOLANT_OUT);
					break;
				case OUT:
					setTankSorption(side, 0, TankSorption.OUT);
					setTankSorption(side, 1, TankSorption.NON);
					setHeaterSetting(side, SaltFissionHeaterSetting.COOLANT_SPREAD);
					break;
				default:
					setTankSorption(side, 0, TankSorption.IN);
					setTankSorption(side, 1, TankSorption.NON);
					setHeaterSetting(side, SaltFissionHeaterSetting.DEFAULT);
					break;
				}
			}
		}
		else {
			NBTTagCompound settingsTag = nbt.getCompoundTag("heaterSettings");
			for (EnumFacing side : EnumFacing.VALUES) {
				setHeaterSetting(side, SaltFissionHeaterSetting.values()[settingsTag.getInteger("setting" + side.getIndex())]);
			}
		}
	}
	
	@Override
	public NBTTagCompound writeAll(NBTTagCompound nbt) {
		super.writeAll(nbt);
		writeTanks(nbt);
		writeFluidConnections(nbt);
		writeHeaterSettings(nbt);
		
		nbt.setDouble("baseProcessCooling", baseProcessCooling);
		nbt.setDouble("reactorCoolingRate", reactorCoolingEfficiency);
		
		nbt.setDouble("time", time);
		nbt.setBoolean("isProcessing", isProcessing);
		nbt.setBoolean("canProcessInputs", canProcessInputs);
		nbt.setBoolean("isInValidPosition", isInValidPosition);
		nbt.setInteger("fluidToHold", fluidToHold);
		return nbt;
	}
	
	@Override
	public void readAll(NBTTagCompound nbt) {
		super.readAll(nbt);
		readTanks(nbt);
		readFluidConnections(nbt);
		readHeaterSettings(nbt);
		
		baseProcessCooling = nbt.getDouble("baseProcessCooling");
		reactorCoolingEfficiency = nbt.getDouble("reactorCoolingRate");
		
		time = nbt.getDouble("time");
		isProcessing = nbt.getBoolean("isProcessing");
		canProcessInputs = nbt.getBoolean("canProcessInputs");
		isInValidPosition = nbt.getBoolean("isInValidPosition");
		fluidToHold = nbt.getInteger("fluidToHold");
	}
	
	// Capability
	
	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
		if (!getTanks().isEmpty() && hasFluidSideCapability(side)) {
			if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
				return true;
			}
			if (ModCheck.mekanismLoaded() && GasHelper.isGasCapability(capability)) {
				return true;
			}
		}
		return super.hasCapability(capability, side);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
		if (!getTanks().isEmpty() && hasFluidSideCapability(side)) {
			if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
				return (T) getFluidSide(nonNullSide(side));
			}
			if (ModCheck.mekanismLoaded() && GasHelper.isGasCapability(capability)) {
				return (T) getGasWrapper();
			}
		}
		return super.getCapability(capability, side);
	}
}
