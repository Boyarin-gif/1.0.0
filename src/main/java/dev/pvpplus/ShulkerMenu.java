package dev.pvpplus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

/** Серверное меню: содержимое шалкера открывается как сундук 9x3, шалкер остаётся в инвентаре. */
public final class ShulkerMenu extends GenericContainerScreenHandler {
    private final ItemStack shulker;
    private final ShulkerInv inv;
    private final PlayerInventory playerInv;

    private ShulkerMenu(int syncId, PlayerInventory pinv, ShulkerInv inv, ItemStack shulker) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, pinv, inv, 3);
        this.shulker = shulker;
        this.inv = inv;
        this.playerInv = pinv;
    }

    public static boolean isShulker(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    public static void tryOpen(ServerPlayerEntity player, int slotId) {
        ScreenHandler h = player.currentScreenHandler;
        if (h instanceof ShulkerMenu) return;
        if (slotId < 0 || slotId >= h.slots.size()) return;
        Slot slot = h.getSlot(slotId);
        if (slot.inventory != player.getInventory()) return;
        ItemStack stack = slot.getStack();
        if (!isShulker(stack)) return;
        ShulkerInv inv = new ShulkerInv(stack);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, pinv, p) -> new ShulkerMenu(syncId, pinv, inv, stack), stack.getName()));
    }

    /** Блокируем любые действия с самим шалкером, пока он открыт (защита от дюпа). */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex).getStack() == shulker) return;
        if (type == SlotActionType.SWAP) {
            if ((button >= 0 && button < 9 && playerInv.getStack(button) == shulker)
                    || (button == 40 && playerInv.getStack(40) == shulker)) return;
        }
        super.onSlotClick(slotIndex, button, type, player);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        PlayerInventory pi = player.getInventory();
        for (int i = 0; i < pi.size(); i++) {
            if (pi.getStack(i) == shulker) return super.canUse(player);
        }
        return false;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inv.save();
    }

    private static final class ShulkerInv extends SimpleInventory {
        private final ItemStack stack;
        private boolean ready = false;

        ShulkerInv(ItemStack stack) {
            super(27);
            this.stack = stack;
            ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
            if (c != null) {
                DefaultedList<ItemStack> list = DefaultedList.ofSize(27, ItemStack.EMPTY);
                c.copyTo(list);
                for (int i = 0; i < 27; i++) setStack(i, list.get(i));
            }
            ready = true;
        }

        @Override
        public boolean isValid(int slot, ItemStack s) {
            return !isShulker(s);
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (ready) save();
        }

        void save() {
            List<ItemStack> list = new ArrayList<>(27);
            for (int i = 0; i < 27; i++) list.add(getStack(i).copy());
            stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(list));
        }
    }
}
