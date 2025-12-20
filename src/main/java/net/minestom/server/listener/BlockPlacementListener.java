package net.minestom.server.listener;

import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
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
    private static InteractResult interactBlock(Player player, PlayerHand hand, Block block, BlockVec blockPosition, Point cursorPosition, BlockFace face) {
        PlayerBlockInteractEvent event = new PlayerBlockInteractEvent(player, hand, block, blockPosition, cursorPosition, face);
        EventDispatcher.call(event);

        BlockHandler handler = block.handler();

        boolean preventItemUse = event.preventItemUse();

        if (!event.isCancelled() && handler != null) {
            preventItemUse |= !handler.onInteract(new BlockHandler.Interaction(block, player.getInstance(), face, blockPosition, cursorPosition, player, hand));
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
    private static InteractResult useItemOnBlock(Player player, PlayerHand hand, Block block, ItemStack itemStack, BlockVec blockPosition, Point cursorPosition, BlockFace face) {
        // Player didn't try to place a block but interacted with one
        PlayerUseItemOnBlockEvent event = new PlayerUseItemOnBlockEvent(player, hand,  block, itemStack, blockPosition, cursorPosition, face);
        EventDispatcher.call(event);

        // If itemstack is non block event.preventBlockplacement is true.
        if (event.preventBlockPlacement()) return InteractResult.CONSUME;

        return  InteractResult.PASS;
    }


    public static void listener(ClientPlayerBlockPlacementPacket packet, Player player) {
        final PlayerHand hand = packet.hand();
        final BlockFace blockFace = packet.blockFace();
        Point blockPosition = packet.blockPosition();

        final Instance instance = player.getInstance();
        if (instance == null)
            return;

        // Prevent outdated/modified client data
        final Chunk interactedChunk = instance.getChunkAt(blockPosition);
        if (!ChunkUtils.isLoaded(interactedChunk)) {
            // Client tried to place a block in an unloaded chunk, ignore the request
            return;
        }

        final ItemStack usedItem = player.getItemInHand(hand);
        final Block interactedBlock = instance.getBlock(blockPosition);
        final Point cursorPosition = new Vec(packet.cursorPositionX(), packet.cursorPositionY(), packet.cursorPositionZ());

        InteractResult interactResult = interactBlock(player, hand, interactedBlock, blockPosition.asBlockVec(), cursorPosition, blockFace);

        if (interactResult == InteractResult.CONSUME) {
            // If the usage was blocked then the world is already up-to-date (from the prior handlers),
            // So ack the change with the current world state.
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }

        InteractResult usageResult = useItemOnBlock(player, hand, interactedBlock, usedItem, blockPosition.asBlockVec(), cursorPosition, blockFace);

        if (usageResult == InteractResult.CONSUME) {
            // Ack the block change. This is required to reset the client prediction to the server state.
            player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
            return;
        }


        final Material useMaterial = usedItem.material();

        // Get the newly placed block position
        //todo it feels like it should be possible to have better replacement rules than this, feels pretty scuffed.
        Point placementPosition = blockPosition;
        var interactedPlacementRule = BLOCK_MANAGER.getBlockPlacementRule(interactedBlock);
        if (!interactedBlock.isAir() && (interactedPlacementRule == null || !interactedPlacementRule.isSelfReplaceable(new BlockPlacementRule.Replacement(interactedBlock, blockFace, cursorPosition, false, useMaterial)))) {
            // If the block is not replaceable, try to place next to it.
            placementPosition = blockPosition.relative(blockFace);
        }

        final Chunk chunk = instance.getChunkAt(placementPosition);
        final ItemBlockState blockState = usedItem.get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY);
        final Block placedBlock = blockState.apply(useMaterial.block());

        if (!canPlaceBlock(player, usedItem, interactedBlock, blockPosition, blockFace, instance, cursorPosition, useMaterial)) {
            // Send a block change with the real block in the instance to keep the client in sync,
            // using refreshChunk results in the client not being in sync
            // after rapid invalid block placements
            final Block block = instance.getBlock(placementPosition);
            player.sendPacket(new BlockChangePacket(placementPosition, block));
            return;
        }

        placeBlock(player, placedBlock, blockFace, placementPosition, cursorPosition, hand, blockState, useMaterial, chunk, instance, packet, usedItem);
    }

    private static Point getPlacementPosition(Point blockPosition, Block interactedBlock, BlockFace blockFace, Point cursorPosition, Material useMaterial) {

        BlockPlacementRule interactedPlacementRule = BLOCK_MANAGER.getBlockPlacementRule(interactedBlock);

        if (!interactedBlock.isAir() && (interactedPlacementRule == null || !interactedPlacementRule.isSelfReplaceable(new BlockPlacementRule.Replacement(interactedBlock, blockFace, cursorPosition, false, useMaterial)))) {
            // If the block is not replaceable, try to place next to it.
            return blockPosition.relative(blockFace);
        }
        return blockPosition;
    }

    private static boolean canPlaceBlock(Player player, ItemStack usedItem, Block interactedBlock, Point blockPosition, BlockFace blockFace, Instance instance,  Point cursorPosition, Material useMaterial) {
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        if (player.getGameMode() == GameMode.ADVENTURE) {
            //Check if the block can be placed on the block
            BlockPredicates placePredicate = usedItem.get(DataComponents.CAN_PLACE_ON, BlockPredicates.NEVER);
            if (!placePredicate.test(interactedBlock)) return false;
        }

        Point placementPosition = getPlacementPosition(blockPosition, interactedBlock, blockFace, cursorPosition, useMaterial);

        var placementBlock = instance.getBlock(placementPosition);
        var placementRule = BLOCK_MANAGER.getBlockPlacementRule(placementBlock);
        if (!placementBlock.registry().isReplaceable() && !(placementRule != null && placementRule.isSelfReplaceable(
                new BlockPlacementRule.Replacement(placementBlock, blockFace, cursorPosition, true, useMaterial)))) {
            // If the block is still not replaceable, cancel the placement
            return false;
        }

        final DimensionType instanceDim = instance.getCachedDimensionType();
        if (placementPosition.y() >= instanceDim.maxY() || placementPosition.y() < instanceDim.minY()) {
            return false;
        }

        // Ensure that the final placement position is inside the world border.
        if (!instance.getWorldBorder().inBounds(placementPosition)) {
            return false;
        }

        final Chunk chunk = instance.getChunkAt(placementPosition);
        Check.stateCondition(!ChunkUtils.isLoaded(chunk),
                "A player tried to place a block in the border of a loaded chunk {0}", placementPosition);
        if (chunk.isReadOnly()) {
            refresh(player, chunk);
            return false;
        }

        final ItemBlockState blockState = usedItem.get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY);
        final Block placedBlock = blockState.apply(useMaterial.block());

        Entity collisionEntity = CollisionUtils.canPlaceBlockAt(instance, placementPosition, placedBlock);
        if (collisionEntity != null) {
            // If a player is trying to place a block on themselves, the client will send a block change but will not set the block on the client
            // For this reason, the block doesn't need to be updated for the client

            // Client also doesn't predict placement of blocks on entities, but we need to refresh for cases where bounding boxes on the server don't match the client
            if (collisionEntity != player)
                refresh(player, chunk);

            return false;
        }

        return true;
    }

    private static void placeBlock(Player player, Block placedBlock, BlockFace blockFace, Point placementPosition, Point cursorPosition, PlayerHand hand, ItemBlockState blockState, Material useMaterial, Chunk chunk, Instance instance, ClientPlayerBlockPlacementPacket packet, ItemStack usedItem) {
        // BlockPlaceEvent check
        PlayerBlockPlaceEvent playerBlockPlaceEvent = new PlayerBlockPlaceEvent(player, placedBlock, blockFace, placementPosition.asBlockVec(), cursorPosition, hand);
        if (player.getGameMode() == GameMode.CREATIVE) playerBlockPlaceEvent.setBlockConsumeAmount(0);

        playerBlockPlaceEvent.setDoBlockUpdates(blockState.equals(useMaterial.prototype().get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY)));
        EventDispatcher.call(playerBlockPlaceEvent);

        if (playerBlockPlaceEvent.isCancelled()) {
            refresh(player, chunk);
            return;
        }
        // Get the result block as event can change it.
        final Block resultBlock = playerBlockPlaceEvent.getBlock();
        final Block previousBlock = instance.getBlock(placementPosition);

        instance.placeBlock(new BlockHandler.PlayerPlacement(resultBlock, previousBlock, instance, placementPosition, player, hand, blockFace, cursorPosition));
        player.sendPacket(new AcknowledgeBlockChangePacket(packet.sequence()));
        // Block consuming
        if (!playerBlockPlaceEvent.doesConsumeBlock()) {
            // Prevent invisible item on client
            player.getInventory().update();
            return;
        }
        // Consume the block in the player's hand
        final ItemStack newUsedItem = usedItem.consume(playerBlockPlaceEvent.getBlockConsumeAmount());
        player.setItemInHand(hand, newUsedItem);
    }

    private static void refresh(Player player, Chunk chunk) {
        player.getInventory().update();
        chunk.sendChunk(player);
    }
}
