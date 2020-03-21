/*
 * @file BlockDecorLabeledCrate.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2019 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Storage crate with a content hint.
 */
package wile.engineersdecor.blocks;

import wile.engineersdecor.ModContent;
import wile.engineersdecor.ModEngineersDecor;
import wile.engineersdecor.libmc.blocks.StandardBlocks;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.block.material.PushReaction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.item.*;
import net.minecraft.inventory.*;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import com.mojang.blaze3d.platform.GlStateManager;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;


public class BlockDecorLabeledCrate
{
  public static void on_config(int stack_limit)
  {
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class DecorLabeledCrateBlock extends StandardBlocks.Horizontal implements IDecorBlock
  {
    public DecorLabeledCrateBlock(long config, Block.Properties builder, final AxisAlignedBB unrotatedAABB)
    { super(config, builder, unrotatedAABB); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasComparatorInputOverride(BlockState state)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(BlockState blockState, World world, BlockPos pos)
    { return Container.calcRedstone(world.getTileEntity(pos)); }

    @Override
    public boolean hasTileEntity(BlockState state)
    { return true; }

    @Override
    @Nullable
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    { return new LabeledCrateTileEntity(); }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      if(world.isRemote) return;
      if((!stack.hasTag()) || (!stack.getTag().contains("tedata"))) return;
      CompoundNBT te_nbt = stack.getTag().getCompound("tedata");
      if(te_nbt.isEmpty()) return;
      final TileEntity te = world.getTileEntity(pos);
      if(!(te instanceof LabeledCrateTileEntity)) return;
      ((LabeledCrateTileEntity)te).readnbt(te_nbt);
      ((LabeledCrateTileEntity)te).markDirty();
    }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, World world, BlockPos pos, boolean explosion)
    {
      final List<ItemStack> stacks = new ArrayList<ItemStack>();
      if(world.isRemote) return stacks;
      final TileEntity te = world.getTileEntity(pos);
      if(!(te instanceof LabeledCrateTileEntity)) return stacks;
      if(!explosion) {
        ItemStack stack = new ItemStack(this, 1);
        CompoundNBT te_nbt = ((LabeledCrateTileEntity) te).reset_getnbt();
        if(!te_nbt.isEmpty()) {
          CompoundNBT nbt = new CompoundNBT();
          nbt.put("tedata", te_nbt);
          stack.setTag(nbt);
        }
        stacks.add(stack);
      } else {
        for(ItemStack stack: ((LabeledCrateTileEntity)te).stacks_) stacks.add(stack);
        ((LabeledCrateTileEntity)te).reset_getnbt();
      }
      return stacks;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rayTraceResult)
    {
      if(world.isRemote) return true;
      final TileEntity te = world.getTileEntity(pos);
      if(!(te instanceof LabeledCrateTileEntity)) return true;
      if((!(player instanceof ServerPlayerEntity) && (!(player instanceof FakePlayer)))) return true;
      NetworkHooks.openGui((ServerPlayerEntity)player,(INamedContainerProvider)te);
      return true;
    }

    @Override
    public PushReaction getPushReaction(BlockState state)
    { return PushReaction.BLOCK; }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class LabeledCrateTileEntity extends TileEntity implements INameable, IInventory, INamedContainerProvider, ISidedInventory
  {
    public static final int NUM_OF_FIELDS = 1;
    public static final int NUM_OF_SLOTS = 55;
    public static final int ITEMFRAME_SLOTNO = 54;

    // BTileEntity -----------------------------------------------------------------------------

    protected NonNullList<ItemStack> stacks_ = NonNullList.<ItemStack>withSize(NUM_OF_SLOTS, ItemStack.EMPTY);

    public LabeledCrateTileEntity()
    { this(ModContent.TET_LABELED_CRATE); }

    public LabeledCrateTileEntity(TileEntityType<?> te_type)
    { super(te_type); reset(); }

    public CompoundNBT reset_getnbt()
    {
      CompoundNBT nbt = new CompoundNBT();
      writenbt(nbt);
      reset();
      return nbt;
    }

    protected void reset()
    {
      stacks_ = NonNullList.<ItemStack>withSize(NUM_OF_SLOTS, ItemStack.EMPTY);
    }

    public void readnbt(CompoundNBT compound)
    {
      NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(NUM_OF_SLOTS, ItemStack.EMPTY);
      ItemStackHelper.loadAllItems(compound, stacks);
      while(stacks.size() < NUM_OF_SLOTS) stacks.add(ItemStack.EMPTY);
      stacks_ = stacks;
    }

    protected void writenbt(CompoundNBT compound)
    {
      ItemStackHelper.saveAllItems(compound, stacks_);
    }

    public ItemStack getItemFrameStack()
    { return (stacks_.size() > ITEMFRAME_SLOTNO) ? (stacks_.get(ITEMFRAME_SLOTNO)) : (ItemStack.EMPTY); }

    // TileEntity ------------------------------------------------------------------------------

    @Override
    public void read(CompoundNBT compound)
    { super.read(compound); readnbt(compound); }

    @Override
    public CompoundNBT write(CompoundNBT compound)
    { super.write(compound); writenbt(compound); return compound; }

    @Override
    public CompoundNBT getUpdateTag()
    { CompoundNBT nbt = super.getUpdateTag(); writenbt(nbt); return nbt; }

    @Override
    @Nullable
    public SUpdateTileEntityPacket getUpdatePacket()
    { return new SUpdateTileEntityPacket(pos, 1, getUpdateTag()); }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) // on client
    { super.read(pkt.getNbtCompound()); readnbt(pkt.getNbtCompound()); super.onDataPacket(net, pkt); }

    @Override
    public void handleUpdateTag(CompoundNBT tag) // on client
    { read(tag); }

    // INameable  ---------------------------------------------------------------------------

    @Override
    public ITextComponent getName()
    { final Block block=getBlockState().getBlock(); return new StringTextComponent((block!=null) ? block.getTranslationKey() : "Small Waste Incinerator"); }

    @Override
    public boolean hasCustomName()
    { return false; }

    @Override
    public ITextComponent getCustomName()
    { return getName(); }

    // IContainerProvider ----------------------------------------------------------------------

    @Override
    public ITextComponent getDisplayName()
    { return INameable.super.getDisplayName(); }

    @Override
    public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player )
    { return new BContainer(id, inventory, this, IWorldPosCallable.of(world, pos), fields); }

    // IInventory ------------------------------------------------------------------------------

    @Override
    public int getSizeInventory()
    { return stacks_.size(); }

    @Override
    public boolean isEmpty()
    { for(ItemStack stack: stacks_) { if(!stack.isEmpty()) return false; } return true; }

    @Override
    public ItemStack getStackInSlot(int index)
    { return ((index >= 0) && (index < getSizeInventory())) ? stacks_.get(index) : ItemStack.EMPTY; }

    @Override
    public ItemStack decrStackSize(int index, int count)
    { return ItemStackHelper.getAndSplit(stacks_, index, count); }

    @Override
    public ItemStack removeStackFromSlot(int index)
    { return ItemStackHelper.getAndRemove(stacks_, index); }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack)
    {
      if(stack.getCount() > getInventoryStackLimit()) stack.setCount(getInventoryStackLimit());
      stacks_.set(index, stack);
      markDirty();
      if(getWorld() instanceof ServerWorld) {
        // This should result in sending TE data (getUpdateTag etc) to the client for the TER.
        BlockState state = world.getBlockState(getPos());
        getWorld().notifyBlockUpdate(getPos(), state, state, 2|16|32);
      }
    }

    @Override
    public int getInventoryStackLimit()
    { return 64; }

    @Override
    public void markDirty()
    { super.markDirty(); }

    @Override
    public boolean isUsableByPlayer(PlayerEntity player)
    { return getPos().distanceSq(player.getPosition()) < 36; }

    @Override
    public void openInventory(PlayerEntity player)
    {}

    @Override
    public void closeInventory(PlayerEntity player)
    { markDirty(); }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack)
    { return (index != ITEMFRAME_SLOTNO); }

    @Override
    public void clear()
    { stacks_.clear(); }

    // Fields -----------------------------------------------------------------------------------------------

    protected final IIntArray fields = new IntArray(LabeledCrateTileEntity.NUM_OF_FIELDS)
    {
      @Override
      public int get(int id)
      {
        switch(id) {
          default: return 0;
        }
      }
      @Override
      public void set(int id, int value)
      {
        switch(id) {
          default: break;
        }
      }
    };

    // ISidedInventory ----------------------------------------------------------------------------

    private static final int[] SIDED_INV_SLOTS;
    static {
      // that useless unoptimised language ... no proper inline conv to int[]?
      // private static final int[] SIDED_INV_SLOTS = IntStream.rangeClosed(0, BTileEntity.NUM_OF_SLOTS-2).boxed().collect(Collectors.toList()).toArray();
      SIDED_INV_SLOTS = new int[LabeledCrateTileEntity.NUM_OF_SLOTS-1];
      for(int i=0; i<SIDED_INV_SLOTS.length; ++i) SIDED_INV_SLOTS[i] = i;
    }

    @Override
    public int[] getSlotsForFace(Direction side)
    { return SIDED_INV_SLOTS; }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, Direction direction)
    { return true; }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, Direction direction)
    { return true; }

    // IItemHandler  --------------------------------------------------------------------------------

    protected static class BItemHandler implements IItemHandler
    {
      private LabeledCrateTileEntity te;

      BItemHandler(LabeledCrateTileEntity te)
      { this.te = te; }

      @Override
      public int getSlots()
      { return ITEMFRAME_SLOTNO; } // iframe slot is the last

      @Override
      public int getSlotLimit(int index)
      { return te.getInventoryStackLimit(); }

      @Override
      public boolean isItemValid(int slot, @Nonnull ItemStack stack)
      { return true; }

      @Override
      @Nonnull
      public ItemStack insertItem(int slotno, @Nonnull ItemStack stack, boolean simulate)
      {
        if(stack.isEmpty()) return ItemStack.EMPTY;
        if((slotno < 0) || ((slotno >= NUM_OF_SLOTS)) || ((slotno == ITEMFRAME_SLOTNO)) ) return ItemStack.EMPTY;
        ItemStack slotstack = getStackInSlot(slotno);
        if(!slotstack.isEmpty()) {
          if(slotstack.getCount() >= Math.min(slotstack.getMaxStackSize(), getSlotLimit(slotno))) return stack;
          if(!ItemHandlerHelper.canItemStacksStack(stack, slotstack)) return stack;
          if(!te.canInsertItem(slotno, stack, Direction.UP) || (!te.isItemValidForSlot(slotno, stack))) return stack;
          int n = Math.min(stack.getMaxStackSize(), getSlotLimit(slotno)) - slotstack.getCount();
          if(stack.getCount() <= n) {
            if(!simulate) {
              ItemStack copy = stack.copy();
              copy.grow(slotstack.getCount());
              te.setInventorySlotContents(slotno, copy);
            }
            return ItemStack.EMPTY;
          } else {
            stack = stack.copy();
            if(!simulate) {
              ItemStack copy = stack.split(n);
              copy.grow(slotstack.getCount());
              te.setInventorySlotContents(slotno, copy);
              return stack;
            } else {
              stack.shrink(n);
              return stack;
            }
          }
        } else {
          if(!te.canInsertItem(slotno, stack, Direction.UP) || (!te.isItemValidForSlot(slotno, stack))) return stack;
          int n = Math.min(stack.getMaxStackSize(), getSlotLimit(slotno));
          if(n < stack.getCount()) {
            stack = stack.copy();
            if(!simulate) {
              te.setInventorySlotContents(slotno, stack.split(n));
              return stack;
            } else {
              stack.shrink(n);
              return stack;
            }
          } else {
            if(!simulate) te.setInventorySlotContents(slotno, stack);
            return ItemStack.EMPTY;
          }
        }
      }

      @Override
      @Nonnull
      public ItemStack extractItem(int index, int amount, boolean simulate)
      {
        if((index < 0) || ((index >= NUM_OF_SLOTS)) || ((index == ITEMFRAME_SLOTNO)) ) return ItemStack.EMPTY;
        if(!simulate) return ItemStackHelper.getAndSplit(te.stacks_, index, amount);
        ItemStack stack = te.stacks_.get(index).copy();
        if(stack.getCount() > amount) stack.setCount(amount);
        return stack;
      }

      @Override
      @Nonnull
      public ItemStack getStackInSlot(int index)
      { return te.getStackInSlot(index); }
    }

    // Capability export ----------------------------------------------------------------------------

    protected LazyOptional<IItemHandler> item_handler_ = LazyOptional.of(() -> new LabeledCrateTileEntity.BItemHandler(this));

    @Override
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable Direction facing)
    {
      if(!this.removed) {
        if(capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return item_handler_.cast();
      }
      return super.getCapability(capability, facing);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // GUI
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class BGui extends ContainerScreen<BContainer>
  {
    protected final PlayerEntity player_;

    public BGui(BContainer container, PlayerInventory player_inventory, ITextComponent title)
    {
      super(container, player_inventory, title);
      player_ = player_inventory.player;
      xSize = 213;
      ySize = 206;
    }

    @Override
    public void init()
    { super.init(); }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks)
    {
      renderBackground();
      super.render(mouseX, mouseY, partialTicks);
      renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY)
    {
      GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
      this.minecraft.getTextureManager().bindTexture(new ResourceLocation(ModEngineersDecor.MODID, "textures/gui/labeled_crate_gui.png"));
      final int x0=guiLeft, y0=this.guiTop, w=xSize, h=ySize;
      blit(x0, y0, 0, 0, w, h);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Container
  //--------------------------------------------------------------------------------------------------------------------

  public static class BContainer extends Container
  {
    //------------------------------------------------------------------------------------------------------------------
    protected static class StorageSlot extends Slot
    {
      StorageSlot(IInventory inventory, int index, int x, int y)
      { super(inventory, index, x, y); }

      @Override
      public int getSlotStackLimit()
      { return 64; }
    }

    //------------------------------------------------------------------------------------------------------------------
    private static final int PLAYER_INV_START_SLOTNO = LabeledCrateTileEntity.NUM_OF_SLOTS;
    protected final PlayerEntity player_;
    protected final IInventory inventory_;
    protected final IWorldPosCallable wpc_;
    private final IIntArray fields_;
    private int proc_time_needed_;
    //------------------------------------------------------------------------------------------------------------------
    public int field(int index) { return fields_.get(index); }
    public PlayerEntity player() { return player_ ; }
    public IInventory inventory() { return inventory_ ; }
    public World world() { return player_.world; }
    //------------------------------------------------------------------------------------------------------------------

    public BContainer(int cid, PlayerInventory player_inventory)
    { this(cid, player_inventory, new Inventory(LabeledCrateTileEntity.NUM_OF_SLOTS), IWorldPosCallable.DUMMY, new IntArray(LabeledCrateTileEntity.NUM_OF_FIELDS)); }

    private BContainer(int cid, PlayerInventory player_inventory, IInventory block_inventory, IWorldPosCallable wpc, IIntArray fields)
    {
      super(ModContent.CT_LABELED_CRATE, cid);
      player_ = player_inventory.player;
      inventory_ = block_inventory;
      wpc_ = wpc;
      fields_ = fields;
      int i=-1;
      // storage slots (stacks 0 to 53)
      for(int y=0; y<6; ++y) {
        for(int x=0; x<9; ++x) {
          int xpos = 28+x*18, ypos = 10+y*18;
          addSlot(new StorageSlot(inventory_, ++i, xpos, ypos));
        }
      }
      // picture frame slot (54)
      addSlot(new Slot(inventory_, ++i, 191, 100) {
        @Override public int getSlotStackLimit(){return 1;}
      });
      // player slots
      for(int x=0; x<9; ++x) {
        addSlot(new Slot(player_inventory, x, 28+x*18, 183)); // player slots: 0..8
      }
      for(int y=0; y<3; ++y) {
        for(int x=0; x<9; ++x) {
          addSlot(new Slot(player_inventory, x+y*9+9, 28+x*18, 125+y*18)); // player slots: 9..35
        }
      }
    }

    @Override
    public boolean canInteractWith(PlayerEntity player)
    { return inventory_.isUsableByPlayer(player); }

    @Override
    public boolean canMergeSlot(ItemStack stack, Slot slot)
    { return (slot.getSlotStackLimit() > 1); }

    @Override
    public void onContainerClosed(PlayerEntity player)
    { super.onContainerClosed(player); }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity player, int index)
    {
      Slot slot = getSlot(index);
      if((slot==null) || (!slot.getHasStack())) return ItemStack.EMPTY;
      ItemStack slot_stack = slot.getStack();
      ItemStack transferred = slot_stack.copy();
      if((index>=0) && (index<PLAYER_INV_START_SLOTNO)) {
        // Crate slots
        if(!mergeItemStack(slot_stack, PLAYER_INV_START_SLOTNO, PLAYER_INV_START_SLOTNO+36, false)) return ItemStack.EMPTY;
      } else if((index >= PLAYER_INV_START_SLOTNO) && (index <= PLAYER_INV_START_SLOTNO+36)) {
        // Player slot
        if(!mergeItemStack(slot_stack, 0, PLAYER_INV_START_SLOTNO-1, false)) return ItemStack.EMPTY;
      } else {
        // invalid slot
        return ItemStack.EMPTY;
      }
      if(slot_stack.isEmpty()) {
        slot.putStack(ItemStack.EMPTY);
      } else {
        slot.onSlotChanged();
      }
      if(slot_stack.getCount() == transferred.getCount()) return ItemStack.EMPTY;
      slot.onTake(player, slot_stack);
      return transferred;
    }
  }
}
