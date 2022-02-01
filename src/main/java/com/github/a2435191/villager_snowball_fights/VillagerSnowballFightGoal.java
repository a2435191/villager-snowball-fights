package com.github.a2435191.villager_snowball_fights;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class VillagerSnowballFightGoal extends Goal {

    private static final int PLAY_DIST_MAX = 20; // taxicab distance
    private static final int PLAY_DIST_MIN = 2;  // euclidean distance
    private static final int MAX_THROW_TICK_INTERVAL = 50;

    private static final int SNOW_CHECK_INTERVAL = 50;
    private static final double GOLEM_TARGET_CHANCE = 0.10D;
    private static final double VIEW_CONE_CENTRAL_ANGLE_DEG = 10;
    private static final int MIN_ZOMBIE_RANGE_TO_PLAY = 32;
    private static final Logger LOGGER = LogManager.getLogger();

    private final Villager villager;

    private @Nullable
    LivingEntity currentTarget = null;
    private int ticksSinceLastThrow = MAX_THROW_TICK_INTERVAL;
    private int ticksSinceLastNearbySnowCheck = SNOW_CHECK_INTERVAL;

    private boolean nearSnow = false;


    public VillagerSnowballFightGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }

    private static boolean canTarget(Villager villager) {
//        LOGGER.info("check: {}", villager.isBaby());
//        LOGGER.info("check: {}", !villager.isTrading());
//        LOGGER.info("check: {}", !villager.isSleeping());
//        LOGGER.info("check: {}", !villager.isOnFire());
//        LOGGER.info("check: {}", !villager.isSteppingCarefully());
//        LOGGER.info("check: {}", !villager.isUnderWater());
//        LOGGER.info("check: {}", villager.getHealth() == villager.getMaxHealth());
        return villager.isBaby()
                   && !villager.isTrading()
                   && !villager.isSleeping()
                   && !villager.isOnFire()
                   && !villager.isSteppingCarefully()
                   && !villager.isUnderWater()
                   && villager.getHealth() == villager.getMaxHealth()
                   && !nearbyZombie(villager);
    }

    private static boolean nearbyZombie(Villager villager) {
        boolean val = villager.level.getNearbyEntities(
                Zombie.class,
                TargetingConditions.forNonCombat(),
                villager,
                getSearchBox(villager, MIN_ZOMBIE_RANGE_TO_PLAY)
            )
            .stream()
            .anyMatch(villager::hasLineOfSight);
        //LOGGER.info("nearbyZombie: {}", val);
        return val;
    }

    private static boolean canSeeInViewCone(LivingEntity viewer, Entity target, double maxAngle) {
        if (!viewer.hasLineOfSight(target)) {
            return false;
        }
        Vec3 delta = target.getEyePosition().subtract(viewer.getEyePosition()).normalize();
        double viewingAngleCosine = viewer.getLookAngle().normalize().dot(delta);

        double viewingAngleDegrees = Math.abs(
            Mth.wrapDegrees(
                Math.toDegrees(
                    Math.acos(viewingAngleCosine))));

        return viewingAngleDegrees <= Math.abs(Mth.wrapDegrees(maxAngle));


    }

    private static AABB getSearchBox(Villager villager, int halfSideLength) {
        AABB villagerBox = villager.getBoundingBox();

        Vec3 delta = new Vec3(halfSideLength, halfSideLength, halfSideLength);
        Vec3 corner1 = villagerBox.getCenter().subtract(delta);
        Vec3 corner2 = villagerBox.getCenter().add(delta);
        return new AABB(corner1, corner2);

    }


    @Override
    public boolean canUse() {

        if (ticksSinceLastNearbySnowCheck > SNOW_CHECK_INTERVAL) {
            ticksSinceLastNearbySnowCheck = 0;
            this.setNearSnow();
        }
        ticksSinceLastNearbySnowCheck++;


        VillagerType type = this.villager.getVillagerData().getType();
//        LOGGER.info("canTarget: {}", canTarget(this.villager));
//        LOGGER.info("type: {}", (type == VillagerType.SNOW || type == VillagerType.TAIGA));
//        LOGGER.info("nearSnow: {}", this.nearSnow);

        boolean result = canTarget(this.villager)
                             && (type == VillagerType.SNOW || type == VillagerType.TAIGA)
                             && this.nearSnow;
//        LOGGER.info("canUse returned {}", result);
        return result;
    }

    private void throwSnowballAtOther(Entity other) {
        Snowball snowball = new Snowball(this.villager.level, this.villager);
        double dy = other.getEyeY() - 1.1D - snowball.getY();
        double dx = other.getX() - snowball.getX();
        double dz = other.getZ() - snowball.getZ();
        double yGravityCorrection = Math.sqrt(dx * dx + dz * dz) * 0.2D;
        snowball.shoot(dx, dy + yGravityCorrection, dz,
            2.0F, 30.0F
        );
        this.villager.level.addFreshEntity(snowball);
        this.villager.playSound(
            SoundEvents.SNOWBALL_THROW,
            this.villager.getRandom().nextFloat(0.9F, 1.1F), // pitch
            0.4F / (this.villager.getRandom().nextFloat(0.8F, 1.2F)) // volume
        );
        this.villager.lookAt(other, 180, 180);
    }

    private List<LivingEntity> otherBabyVillagersWithinRange() {
        List<LivingEntity> babyVillagers = this.villager.level
            .getNearbyEntities(
                Villager.class,
                TargetingConditions.forNonCombat(),
                this.villager,
                getSearchBox(this.villager, PLAY_DIST_MAX)
            )
            .stream()
            .filter(
                villager ->
                    canTarget(villager)
                        && villager.position().distanceTo(this.villager.position()) >= PLAY_DIST_MIN
                        && canSeeInViewCone(this.villager, villager, VIEW_CONE_CENTRAL_ANGLE_DEG)
            )
            .map(villager -> (LivingEntity) villager)
            .toList();


        return babyVillagers;
    }

    private List<LivingEntity> golemsWithinRange() {

        List<LivingEntity> golems = this.villager.level
            .getNearbyEntities(
                IronGolem.class,
                TargetingConditions.forNonCombat(),
                this.villager,
                getSearchBox(this.villager, PLAY_DIST_MAX)
            )
            .stream()
            .filter(
                golem ->
                    golem.position().distanceTo(this.villager.position()) >= PLAY_DIST_MIN
                        && canSeeInViewCone(this.villager, golem, 10.0F)
            )
            .map(golem -> (LivingEntity) golem)
            .toList();

        return golems;
    }

    private void setNearSnow() {
        BlockPos standingOn = this.villager.getOnPos();
        this.nearSnow = this.villager.level
            .getBiome(standingOn)
            .coldEnoughToSnow(standingOn);
//LOGGER.info("nearSnow: {}", nearSnow);
    }

    @Override
    public void tick() {
        if (ticksSinceLastThrow >= MAX_THROW_TICK_INTERVAL) {
            ticksSinceLastThrow = 0;

            List<LivingEntity> targets;
            if (this.villager.getRandom().nextDouble() < GOLEM_TARGET_CHANCE) {
                targets = this.golemsWithinRange();
            } else {
                targets = this.otherBabyVillagersWithinRange();
            }

            if (targets.size() > 0) {
                int index = this.villager.getRandom().nextInt(0, targets.size());
                this.currentTarget = targets.get(index);
                this.throwSnowballAtOther(this.currentTarget);
            }
        }
        if (this.currentTarget != null) {
            this.villager.lookAt(this.currentTarget, 360, 360);
        }

        ticksSinceLastThrow += this.villager.getRandom().nextInt(1, 4);


    }
}
