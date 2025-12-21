package net.minestom.server.listener;

import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.BlockPredicates;
import net.minestom.server.item.component.ItemBlockState;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.validate.Check;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockPlacementListener {
    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();



    private enum InteractResult {
        PASS,
        CONSUME,
        FAIL
    }
    /*
    * This handles when a player interacts with a block.
    * Vanilla cases involves (opening containers, opening doors, flickering levers, etc.)
    * Handles PlayerBlockInteractEvent and blockhandler.onInteract()
    */
    private static InteractResult interactBlock(BlockPlacementContext context) {
        PlayerBlockInteractEvent event = new PlayerBlockInteractEvent(context.player, context.hand, context.targetBlock, context.targetPosition.asBlockVec(), context.cursorPosition, context.targetFace);
        EventDispatcher.call(event);

        BlockHandler handler = context.targetBlock.handler();

        boolean preventItemUse = event.preventItemUse();

        if (!event.isCancelled() && handler != null) {
            preventItemUse |= !handler.onInteract(new BlockHandler.Interaction(context.targetBlock, context.instance, context.targetFace, context.targetPosition, context.cursorPosition, context.player, context.hand));
        }

        return preventItemUse
                ? InteractResult.CONSUME
                : InteractResult.PASS;

    }
    /*
    * This handles when a player uses and item on a block
    * Vanilla cases involves (bone mealing grass, flint and steel, fire chargers, etc.)
    *
    *
    */
    private static InteractResult useItemOnBlock(BlockPlacementContext context) {
        // Player didn't try to place a block but interacted with one
        PlayerUseItemOnBlockEvent event = new PlayerUseItemOnBlockEvent(context.player, context.hand,  context.targetBlock, context.heldItem, context.targetPosition, context.cursorPosition, context.targetFace);
        EventDispatcher.call(event);

        // If itemstack is non block event.preventBlockplacement is true.
        if (event.preventBlockPlacement()) return InteractResult.CONSUME;

        return  InteractResult.PASS;
    }


    public static void listener(ClientPlayerBlockPlacementPacket packet, Player player) {
        if (player.getInstance() == null) return;
        if (!ChunkUtils.isLoaded(player.getInstance(), packet.blockPosition())) return;

        final BlockPlacementContext context = new BlockPlacementContext(packet, player);

        InteractResult interactResult = interactBlock(context);
        if (interactResult == InteractResult.CONSUME) {
            // If the usage was blocked then the world is already up-to-date (from the prior handlers),
            // So ack the change with the current world state.
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }

        InteractResult usageResult = useItemOnBlock(context);

        if (usageResult == InteractResult.CONSUME) {
            // Ack the block change. This is required to reset the client prediction to the server state.
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }

        if (!canPlaceBlock(context)) {
            // Send a block change with the real block in the instance to keep the client in sync,
            // using refreshChunk results in the client not being in sync
            // after rapid invalid block placements
            final Block block = context.instance.getBlock(context.placementPosition);
            player.sendPacket(new BlockChangePacket(context.placementPosition, block));
            return;
        }

        placeBlock(context);
    }

    private static Point getPlacementPosition(Point blockPosition, Block interactedBlock, BlockFace blockFace, Point cursorPosition, Material useMaterial) {

        BlockPlacementRule interactedPlacementRule = BLOCK_MANAGER.getBlockPlacementRule(interactedBlock);

        if (!interactedBlock.isAir() && (interactedPlacementRule == null || !interactedPlacementRule.isSelfReplaceable(new BlockPlacementRule.Replacement(interactedBlock, blockFace, cursorPosition, false, useMaterial)))) {
            // If the block is not replaceable, try to place next to it.
            return blockPosition.relative(blockFace);
        }
        return blockPosition;
    }

    private static Block getPlacedBlock(ItemStack heldItem, Material heldMaterial) {
        final ItemBlockState blockState = getItemBlockState(heldItem);
        return blockState.apply(heldMaterial.block());
    }

    private static ItemBlockState getItemBlockState(ItemStack heldItem) {
        return heldItem.get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY);
    }

    private static boolean canPlaceBlock(BlockPlacementContext context) {
        if (context.player.getGameMode() == GameMode.SPECTATOR) return false;
        if (context.player.getGameMode() == GameMode.ADVENTURE) {
            //Check if the block can be placed on the block
            BlockPredicates placePredicate = context.heldItem.get(DataComponents.CAN_PLACE_ON, BlockPredicates.NEVER);
            if (!placePredicate.test(context.targetBlock)) return false;
        }

        //todo it feels like it should be possible to have better replacement rules than this, feels pretty scuffed.
        BlockPlacementRule placementRule = BLOCK_MANAGER.getBlockPlacementRule(context.placementBlock);
        if (!context.placementBlock.registry().isReplaceable() && !(placementRule != null && placementRule.isSelfReplaceable(
                new BlockPlacementRule.Replacement(context.placementBlock, context.targetFace, context.cursorPosition, true, context.heldMaterial)))) {
            // If the block is still not replaceable, cancel the placement
            return false;
        }

        final DimensionType instanceDim = context.instance.getCachedDimensionType();
        if (context.placementPosition.y() >= instanceDim.maxY() || context.placementPosition.y() < instanceDim.minY()) return false;

        // Ensure that the final placement position is inside the world border.
        if (!context.instance.getWorldBorder().inBounds(context.placementPosition)) return false;

        Check.stateCondition(!ChunkUtils.isLoaded(context.placementChunk),
                "A player tried to place a block in the border of a loaded chunk {0}", context.placementPosition);
        if (context.placementChunk.isReadOnly()) {
            refresh(context);
            return false;
        }


        Entity collisionEntity = CollisionUtils.canPlaceBlockAt(context.instance, context.placementPosition, context.placementBlock);
        if (collisionEntity != null) {
            // If a player is trying to place a block on themselves, the client will send a block change but will not set the block on the client
            // For this reason, the block doesn't need to be updated for the client

            // Client also doesn't predict placement of blocks on entities, but we need to refresh for cases where bounding boxes on the server don't match the client
            if (collisionEntity != context.player)
                refresh(context);

            return false;
        }



        return true;
    }

    private static void placeBlock(BlockPlacementContext context) {
        // BlockPlaceEvent check
        PlayerBlockPlaceEvent playerBlockPlaceEvent = new PlayerBlockPlaceEvent(context.player, context.placementBlock, context.targetFace, context.placementPosition.asBlockVec(), context.cursorPosition, context.hand);
        if (context.player.getGameMode() == GameMode.CREATIVE) playerBlockPlaceEvent.setBlockConsumeAmount(0);

        ItemBlockState blockState = getItemBlockState(context.heldItem);

        playerBlockPlaceEvent.setDoBlockUpdates(blockState.equals(context.heldMaterial.prototype().get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY)));
        EventDispatcher.call(playerBlockPlaceEvent);

        if (playerBlockPlaceEvent.isCancelled()) {
            refresh(context);
            return;
        }
        // Get the result block as event can change it.
        final Block resultBlock = playerBlockPlaceEvent.getBlock();
        final Block previousBlock = context.instance.getBlock(context.placementPosition);

        context.instance.placeBlock(new BlockHandler.PlayerPlacement(resultBlock, previousBlock, context.instance, context.placementPosition, context.player, context.hand, context.targetFace, context.cursorPosition));
        context.player.sendPacket(new AcknowledgeBlockChangePacket(context.packet.sequence()));
        // Block consuming
        if (!playerBlockPlaceEvent.doesConsumeBlock()) {
            // Prevent invisible item on client
            context.player.getInventory().update();
            return;
        }
        // Consume the block in the player's hand
        final ItemStack newUsedItem = context.heldItem.consume(playerBlockPlaceEvent.getBlockConsumeAmount());
        context.player.setItemInHand(context.hand, newUsedItem);
    }

    private static void refresh(BlockPlacementContext context) {
        context.player.getInventory().update();
        if (context.placementChunk == null) return;
        context.placementChunk.sendChunk(context.player);
    }

    private static final class BlockPlacementContext {

        final ClientPlayerBlockPlacementPacket packet;
        final Player player;
        final PlayerHand hand;
        final Instance instance;

        final Block targetBlock;
        final Point targetPosition;
        final BlockFace targetFace;
        final Point cursorPosition;

        final ItemStack heldItem;
        final Material heldMaterial;

        Point placementPosition;
        Block placementBlock;
        @Nullable Chunk placementChunk;


        BlockPlacementContext(ClientPlayerBlockPlacementPacket packet, Player player) {
            this.packet = packet;
            this.player = player;
            this.hand = packet.hand();
            this.instance = player.getInstance();

            this.targetPosition = packet.blockPosition();
            this.targetFace = packet.blockFace();
            this.cursorPosition = new Pos(packet.cursorPositionX(), packet.cursorPositionY(), packet.cursorPositionZ());
            this.targetBlock = this.instance.getBlock(this.targetPosition);

            this.heldItem = player.getItemInHand(this.hand);
            this.heldMaterial = this.heldItem.material();

            this.placementPosition = getPlacementPosition(
                    this.targetPosition,
                    this.targetBlock,
                    this.targetFace,
                    this.cursorPosition,
                    this.heldMaterial
            );
            this.placementBlock = getPlacedBlock(this.heldItem, this.heldMaterial);
            this.placementChunk = this.instance.getChunkAt(this.placementPosition);
        }
    }

}
