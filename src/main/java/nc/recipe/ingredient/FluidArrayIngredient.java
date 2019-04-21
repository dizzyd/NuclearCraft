package nc.recipe.ingredient;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;

import nc.recipe.IngredientMatchResult;
import nc.recipe.IngredientSorption;
import net.minecraftforge.fluids.FluidStack;

public class FluidArrayIngredient implements IFluidIngredient {
	
	public List<IFluidIngredient> ingredientList;
	public List<FluidStack> cachedStackList = new ArrayList<FluidStack>();
	
	public FluidArrayIngredient(IFluidIngredient... ingredients) {
		this(Lists.newArrayList(ingredients));
	}
	
	public FluidArrayIngredient(List<IFluidIngredient> ingredientList) {
		this.ingredientList = ingredientList;
		ingredientList.forEach(input -> cachedStackList.add(input.getStack()));
	}
	
	@Override
	public FluidStack getStack() {
		if (cachedStackList == null || cachedStackList.isEmpty() || cachedStackList.get(0) == null) return null;
		return cachedStackList.get(0).copy();
	}
	
	@Override
	public String getIngredientName() {
		//return ingredientList.get(0).getIngredientName();
		return getIngredientNamesConcat();
	}
	
	@Override
	public String getIngredientNamesConcat() {
		String names = "";
		for (IFluidIngredient ingredient : ingredientList) names += (", " + ingredient.getIngredientName());
		return "{ " + names.substring(2) + " }";
	}
	
	public String getIngredientRecipeString() {
		String names = "";
		for (IFluidIngredient ingredient : ingredientList) names += (", " + ingredient.getMaxStackSize(0) + " x " + ingredient.getIngredientName());
		return "{ " + names.substring(2) + " }";
	}
	
	@Override
	public int getMaxStackSize(int ingredientNumber) {
		return ingredientList.get(ingredientNumber).getMaxStackSize(0);
	}
	
	@Override
	public void setMaxStackSize(int stackSize) {
		for (IFluidIngredient ingredient : ingredientList) ingredient.setMaxStackSize(stackSize);
		for (FluidStack stack : cachedStackList) stack.amount = stackSize;
	}
	
	@Override
	public List<FluidStack> getInputStackList() {
		List<FluidStack> stacks = new ArrayList<FluidStack>();
		ingredientList.forEach(ingredient -> ingredient.getInputStackList().forEach(obj -> stacks.add(obj)));
		return stacks;
	}
	
	@Override
	public List<FluidStack> getOutputStackList() {
		if (cachedStackList == null || cachedStackList.isEmpty()) return new ArrayList<FluidStack>();
		return Lists.newArrayList(getStack());
	}
	
	@Override
	public IngredientMatchResult match(Object object, IngredientSorption sorption) {
		for (int i = 0; i < ingredientList.size(); i++) {
			if (ingredientList.get(i).match(object, sorption).matches()) return new IngredientMatchResult(true, i);
		}
		return IngredientMatchResult.FAIL;
	}
	
	@Override
	public boolean isValid() {
		return cachedStackList != null && !cachedStackList.isEmpty();
	}
}
