package net.minestom.server.event.player;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.trait.BlockEvent;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/**
 * Called when a player tries placing a block.
 */
public class PlayerBlockPlaceEvent
        implements PlayerInstanceEvent, BlockEvent, CancellableEvent {

    private final Player player;
    private Block block;
    private final BlockFace blockFace;
    private final BlockVec blockPosition;
    private final Point cursorPosition;
    private final PlayerHand hand;

    private int blockConsumeAmount = 1;
    private boolean doBlockUpdates = true;

    private boolean cancelled;

    public PlayerBlockPlaceEvent(Player player, Block block,
                                 BlockFace blockFace, BlockVec blockPosition,
                                 Point cursorPosition, PlayerHand hand) {
        this.player = player;
        this.block = block;
        this.blockFace = blockFace;
        this.blockPosition = blockPosition;
        this.cursorPosition = cursorPosition;
        this.hand = hand;
    }

    // ---- Block ----

    @Override
    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public BlockFace getBlockFace() {
        return blockFace;
    }

    @Override
    public BlockVec getBlockPosition() {
        return blockPosition;
    }

    public Point getCursorPosition() {
        return cursorPosition;
    }

    public PlayerHand getHand() {
        return hand;
    }

    /**
     * Sets how many blocks should be consumed from the player's inventory.
     * 0 = do not consume any blocks.
     * @param amount the amount to consume
     */
    public void setBlockConsumeAmount(int amount) {
        if (amount < 0) {
            amount = 0;
        }
        this.blockConsumeAmount = amount;
    }

    /**
     * Gets how many blocks will be consumed.
     *
     * @return the consume amount (0 = no consumption)
     */
    public int getBlockConsumeAmount() {
        return blockConsumeAmount;
    }

    /**
     * @return true if at least one block will be consumed
     */
    public boolean doesConsumeBlock() {
        return blockConsumeAmount > 0;
    }

    public void setDoBlockUpdates(boolean doBlockUpdates) {
        this.doBlockUpdates = doBlockUpdates;
    }

    public boolean shouldDoBlockUpdates() {
        return doBlockUpdates;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
