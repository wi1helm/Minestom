package nub.wi1helm;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityProjectile;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.ai.goal.RangedAttackGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BoatCreature extends EntityCreature {

    public BoatCreature(@NotNull Instance instance) {
        super(EntityType.ENDERMAN);

        // Create a RangedAttackGoal with custom parameters
        RangedAttackGoal rangedAttackGoal = new RangedAttackGoal(this, 0, 10, 5, false, 1.5, 0.1, TimeUnit.CLIENT_TICK);

        // Set the custom projectile generator to shoot snowballs
        rangedAttackGoal.setProjectileGenerator((shooter, targetPos, power, spread) -> {
            // Create a snowball projectile
            EntityProjectile snowball = new EntityProjectile(shooter, EntityType.BREEZE_WIND_CHARGE);

            // Set the initial position of the projectile
            snowball.setInstance(shooter.getInstance(), shooter.getPosition().add(0, shooter.getEyeHeight(), 0));

            // Calculate the direction to the target
            Vec direction = targetPos.asVec().sub(snowball.getPosition().asVec()).normalize();

            // Adjust the direction's Y component to account for a slight upward angle
            double pitchAdjustment = 0.2; // Adjust this value to control the angle
            direction = new Vec(direction.x(), direction.y() + pitchAdjustment, direction.z());

            // Multiply by power to get the velocity
            Vec velocity = direction.mul(power).mul(10);

            // Apply the velocity to the projectile
            snowball.setVelocity(velocity);
        });

        // Add the goal to the creature
        this.addAIGroup(List.of(rangedAttackGoal), List.of(new ClosestEntityTarget(this, 32, entity -> entity instanceof Player)));
    }
}
