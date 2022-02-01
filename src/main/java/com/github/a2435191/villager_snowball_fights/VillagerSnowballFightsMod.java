package com.github.a2435191.villager_snowball_fights;


import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

@Mod("villager_snowball_fights")
public class VillagerSnowballFightsMod {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MOD_MARKER = MarkerManager.getMarker("VillagerSnowballFightsMod");

    public VillagerSnowballFightsMod() {
        LOGGER.info(MOD_MARKER, "HELLO WORLD");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void addSnowballAiToVillagerOnSpawn(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof Villager) {
            Villager villager = (Villager) event.getEntity();
            villager.targetSelector.addGoal(5, new VillagerSnowballFightGoal(villager));
        }
    }


}
