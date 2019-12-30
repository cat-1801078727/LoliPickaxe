package com.anotherstar.common.entity;

import com.anotherstar.client.model.ModelLoli;
import com.anotherstar.client.render.RenderLoli;
import com.anotherstar.client.render.RenderRemiliaLoli;
import com.anotherstar.common.LoliPickaxe;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EntityLoader {

	@SubscribeEvent
	public void onEntityRegistation(RegistryEvent.Register<EntityEntry> event) {
		event.getRegistry().register(EntityEntryBuilder.create().entity(EntityLoli.class)
				.id(new ResourceLocation(LoliPickaxe.MODID, "loli"), 219).name("Loli").tracker(80, 3, false).build());
		EntityRegistry.registerEgg(new ResourceLocation(LoliPickaxe.MODID, "loli"), 0xFFFFFF, 0x000000);
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void registerModel(ModelRegistryEvent event) {
		if (Loader.isModLoaded(TouhouLittleMaid.MOD_ID)) {
			registerRemiliaModel();
		} else {
			RenderingRegistry.registerEntityRenderingHandler(EntityLoli.class,
					manager -> new RenderLoli(manager, new ModelLoli(), 0.2f));
		}
	}

	@Optional.Method(modid = TouhouLittleMaid.MOD_ID)
	private void registerRemiliaModel() {
		RenderingRegistry.registerEntityRenderingHandler(EntityLoli.class, RenderRemiliaLoli.FACTORY);
	}

}