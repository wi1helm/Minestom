package nub.wi1helm;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.extras.query.Query;
import net.minestom.server.extras.query.response.BasicQueryResponse;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.ping.ResponseData;
import net.minestom.server.ping.ServerListPingType;

import java.nio.file.Path;
import java.util.Set;

public class Main {
    public static void main(String[] args) {

        String workingDir = System.getProperty("user.dir");

        // Print it out
        System.out.println("Current working directory: " + workingDir);

        // Server init
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Create the instance(World)
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setChunkLoader(new AnvilLoader("src/main/worlds/hub"));

        // Add an event callback to specify the spawning instance (and the spawn position)
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        globalEventHandler.addListener(ServerListPingEvent.class, serverListPingEvent -> {
            ResponseData responseData = serverListPingEvent.getResponseData();
            responseData.setDescription(Component.text("NUb obama"));
            responseData.setOnline(634324);
            responseData.setMaxPlayer(-10000);
        });



        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 100, 0));
        });
        globalEventHandler.addListener(PlayerSpawnEvent.class, playerSpawnEvent -> {
           playerSpawnEvent.getPlayer().switchEntityType(EntityType.OCELOT);
            EntityCreature boat = new BoatCreature(instanceContainer);
            Pos spawnPosition = new Pos(0D, 100D, 0D);
            boat.setInstance(instanceContainer, spawnPosition);
        });

        globalEventHandler.addListener(ProjectileCollideWithBlockEvent.class, event -> {
            event.getEntity().setInstance(instanceContainer);
            event.getEntity().remove();
            Pos pos = event.getEntity().getPosition();
            Particle particle = Particle.ANGRY_VILLAGER;
            Set<Player> players = instanceContainer.getPlayers();

            for (Player player : players){
                player.sendPacket(new ParticlePacket(particle, pos.x(), pos.y(), pos.z(), 0, 0, 0, 1, 2));
                player.sendPacket(new DamageEventPacket(player.getEntityId(), 1, event.getEntity().getEntityId(), 0, pos));
                player.takeKnockback(0.4F, player.getPosition().x(), player.getPosition().z());
            }
        });


        minecraftServer.start("0.0.0.0", 25565);
    }
}