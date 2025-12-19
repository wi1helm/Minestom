package net.minestom.server.event.player;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.trait.BlockEvent;
import net.minestom.server.event.trait.ItemEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;

/**
 * Used when a player is clicking on a block with an item (but is not a block in item form).
 */
public class PlayerUseItemOnBlockEvent implements PlayerInstanceEvent, ItemEvent, BlockEvent {

    private final Player player;
    private final PlayerHand hand;
    private final ItemStack itemStack;
    private final Point blockPosition;
    private final Point cursorPosition;
    private final BlockFace blockFace;
    private final Block block;

    /**
     * Does this item usage prevent/stop block placement?
     * True if block items should not be placed.
     */
    private boolean preventBlockPlacement;

    public PlayerUseItemOnBlockEvent(Player player, PlayerHand hand,
                                     Block block,
                                     ItemStack itemStack,
                                     Point blockPosition, Point cursorPosition,
                                     BlockFace blockFace) {
        this.player = player;
        this.hand = hand;
        this.block = block;
        this.itemStack = itemStack;
        this.blockPosition = blockPosition;
        this.cursorPosition = cursorPosition;
        this.blockFace = blockFace;

    }
    /**
     * Gets if the event should prevent block placement.
     *
     * @return true if the placement is prevented, false otherwise
     */
    public boolean isPreventBlockPlacement() {
        return preventBlockPlacement;
    }

    /**
     * Sets the prevent block placement state of this event
     * Note: If this is true, then no block placement will be occur.
     * This exists so that block items have a way of not trying to be placed.
     * @param prevent - true to block item interactions, false to not block
     */
    public void setPreventBlockPlacement(boolean prevent) {
        this.preventBlockPlacement = prevent;
    }


    /**
     * Gets the cursor position of the interacted block
     *
     * @return the cursor position of the interaction
     */
    public Point getCursorPosition() { return cursorPosition; }

    /**
     * Gets which face the player has interacted with.
     *
     * @return the block face
     */
    public BlockFace getBlockFace() {
        return blockFace;
    }

    /**
     * Gets which hand the player used to interact with the block.
     *
     * @return the hand
     */
    public PlayerHand getHand() {
        return hand;
    }

    /**
     * Gets with which item the player has interacted with the block.
     *
     * @return the item
     */
    @Override
    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Block getBlock() {
        return block;
    }
    /**
     * Gets the position of the interacted block.
     *
     * @return the block position
     */
    @Override
    public BlockVec getBlockPosition() {
        return blockPosition.asBlockVec();
    }
}
