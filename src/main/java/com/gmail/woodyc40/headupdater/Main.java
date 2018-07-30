package com.gmail.woodyc40.headupdater;

import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class Main extends JavaPlugin {
    private static final String CMD_NAME = "headupdate";
    private static final float ROT_DIFF = 0.2F;
    private static final long ROT_DURATION = 10; // seconds

    @Override
    public void onEnable() {
        this.getCommand(CMD_NAME).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (CMD_NAME.equalsIgnoreCase(command.getName())) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("You are not a player");
                return true;
            }

            Player player = (Player) sender;
            Villager entity = CustomVillager.spawn(player.getLocation());

            // 0.2 fits evenly into a whole number so
            // an int-cast is ok here
            int rotationRounds = (int) (360.0F / ROT_DIFF);
            int roundsPerTick = (int) (rotationRounds / ROT_DURATION / 20);

            new BukkitRunnable() {
                private int roundsCompleted = 0;

                @Override
                public void run() {
                    if (this.roundsCompleted == 0) {
                        sender.sendMessage("Starting the rotation!");
                    }

                    if (!entity.isValid()) {
                        sender.sendMessage("Entity seems to have died before completion");
                        this.cancel();
                        return;
                    }

                    for (int i = 0; i < roundsPerTick; i++) {
                        Location existingLocation = entity.getLocation();
                        existingLocation.setYaw(existingLocation.getYaw() + ROT_DIFF);

                        teleportEntity(entity, existingLocation);
                    }

                    this.roundsCompleted += roundsPerTick;
                    if (this.roundsCompleted >= rotationRounds) {
                        sender.sendMessage("Rotation should be complete!");
                        this.cancel();
                    }
                }
            }.runTaskTimer(this, 20L, 1L);
            return true;
        }

        return false;
    }

    // Avoid using this method unless you really need it
    // Forcing movement updates pollutes the client
    // connection and you should stick with the default
    // teleportation method whenever possible
    private void teleportEntity(Entity entity, Location location) {
        World world = location.getWorld();
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        EntityTracker tracker = worldServer.tracker;

        int entityId = entity.getEntityId();

        // I should probably null check here, but if it
        // fails, then the entity failed to spawn anyways
        // so it doesn't really matter
        EntityTrackerEntry trackerEntry = tracker.trackedEntities.get(entityId);
        net.minecraft.server.v1_8_R3.Entity handle = ((CraftEntity) entity).getHandle();

        // Force update here
        trackerEntry.m = trackerEntry.c;
        trackerEntry.xRot = -4; // Yaw delta initial
        trackerEntry.i = -4;    // Head rot initial

        // Set entity position

        // LivingEntities will not rotate in place if you
        // do not call these 2 methods
        handle.f(location.getYaw());
        handle.g(location.getYaw());

        handle.setPositionRotation(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    // Helper utilities

    private static Field getField(Class<?> cls, String fieldName) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T getValue(Field field, Object instance) {
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class CustomVillager extends EntityVillager {
        // Pathfinder fields
        private static final Field PGS_B = getField(PathfinderGoalSelector.class, "b");
        private static final Field PGS_C = getField(PathfinderGoalSelector.class, "c");

        // Registry fields
        private static final Map ET_C = getValue(getField(EntityTypes.class, "c"), null);
        private static final Map ET_D = getValue(getField(EntityTypes.class, "d"), null);
        private static final Map ET_E = getValue(getField(EntityTypes.class, "e"), null);
        private static final Map ET_F = getValue(getField(EntityTypes.class, "f"), null);
        private static final Map ET_G = getValue(getField(EntityTypes.class, "g"), null);

        // Registry constants for the custom villager
        private static final Class<?> VILLAGER_CLS = CustomVillager.class;
        private static final int VILLAGER_ID = 120;
        private static final String VILLAGER_ENT_NAME = "Villager";

        static {
            register(VILLAGER_CLS, VILLAGER_ENT_NAME, VILLAGER_ID);
        }

        private final boolean isCustom;

        public CustomVillager(net.minecraft.server.v1_8_R3.World world, boolean ignore) {
            super(world);
            this.isCustom = true;

            clearGoalSelectors(this.goalSelector);
            clearGoalSelectors(this.targetSelector);
        }

        // Play-nice constructor so regular villagers don't freak out
        public CustomVillager(net.minecraft.server.v1_8_R3.World world) {
            super(world);
            this.isCustom = false;
        }

        @Override
        public void move(double d0, double d1, double d2) {
            // Prevent villager from moving/being moved
            if (!this.isCustom) {
                super.move(d0, d1, d2);
            }
        }

        private static void clearGoalSelectors(PathfinderGoalSelector goalSelector) {
            Main.<List>getValue(PGS_B, goalSelector).clear();
            Main.<List>getValue(PGS_C, goalSelector).clear();
        }

        private static void register(Class<?> entityClass, String entityName, int typeId) {
            ET_C.put(entityName, entityClass);
            ET_D.put(entityClass, entityName);
            ET_E.put(typeId, entityClass);
            ET_F.put(entityClass, typeId);
            ET_G.put(entityName, typeId);
        }

        public static Villager spawn(Location location) {
            World world = location.getWorld();
            CraftWorld craftWorld = (CraftWorld) world;
            WorldServer worldServer = craftWorld.getHandle();
            EntityVillager newEntity = new CustomVillager(worldServer, true);

            newEntity.setLocation(location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());

            return (Villager) craftWorld.addEntity(newEntity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
    }
}