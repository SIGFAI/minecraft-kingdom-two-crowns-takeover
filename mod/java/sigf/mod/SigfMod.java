package sigf.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

public final class SigfMod implements ModInitializer {
	public static Item COIN, CROWN;
	public static SoundEvent COIN_DING, GREED_CRY, NIGHT_HORN;
	public static EntityType<GreedEntity> GREED;
	public static EntityType<ArcherEntity> ARCHER;

	@Override
	public void onInitialize() {
		COIN = Sigf.item("coin", p -> new Item(p.stacksTo(64)));
		CROWN = Sigf.item("crown", p -> new Item(p.stacksTo(1)));
		COIN_DING = Sigf.registerSound("coin_ding");
		GREED_CRY = Sigf.registerSound("greed_cry");
		NIGHT_HORN = Sigf.registerSound("night_horn");

		ResourceKey<EntityType<?>> gk = ResourceKey.create(Registries.ENTITY_TYPE, Sigf.id("greed"));
		GREED = Registry.register(BuiltInRegistries.ENTITY_TYPE, gk,
			EntityType.Builder.of(GreedEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(10).build(gk));
		FabricDefaultAttributeRegistry.register(GREED, GreedEntity.greedAttributes());
		ResourceKey<EntityType<?>> ak = ResourceKey.create(Registries.ENTITY_TYPE, Sigf.id("archer"));
		ARCHER = Registry.register(BuiltInRegistries.ENTITY_TYPE, ak,
			EntityType.Builder.of(ArcherEntity::new, MobCategory.CREATURE).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(10).build(ak));
		FabricDefaultAttributeRegistry.register(ARCHER, ArcherEntity.archerAttributes());

		ServerTickEvents.END_SERVER_TICK.register(s -> Kingdom.tick());
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> Kingdom.onDeath(e));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (entity instanceof Villager v) {
				if (!sp.getItemInHand(hand).is(COIN)) { sp.sendSystemMessage(Component.literal("Peasant: Spare a coin, Your Majesty?"), true); return InteractionResult.PASS; }
				if (Kingdom.recruit(sp, v)) sp.getItemInHand(hand).shrink(1);
				return InteractionResult.SUCCESS;
			}
			int i = Kingdom.siteAt(entity);
			if (i >= 0) { Kingdom.pay(sp, i); return InteractionResult.SUCCESS; }
			return InteractionResult.PASS;
		});

		Sigf.after(2, Kingdom::setup);

		// ---- demo ----
		Sigf.demo(0.3, () -> { Kingdom.setPhase(0.28); Sigf.title("KINGDOM TWO CROWNS", "Long live the Monarch!", 3); gather(); });
		Sigf.demo(3, SigfMod::demoRecruit);
		Sigf.demo(5, SigfMod::demoRecruit);
		Sigf.demo(7, SigfMod::demoRecruit);
		Sigf.demo(9, () -> demoPay(2));
		Sigf.demo(14, () -> demoPay(0));
		Sigf.demo(19, () -> demoPay(1));
		Sigf.demo(27, () -> demoPay(2));
		Sigf.demo(33, () -> demoPay(0));
		Sigf.demo(39, () -> demoPay(3));
		for (int t = 22; t < 72; t += 2) if (t != 27 && t != 33 && t != 39) Sigf.demo(t, SigfMod::demoFight);
	}

	/** Brings the peasants in front of the monarch so the first seconds show them. */
	static void gather() {
		ServerPlayer h = Sigf.host();
		Vec3 f = h.getLookAngle(); f = new Vec3(f.x, 0, f.z).normalize();
		Vec3 side = new Vec3(-f.z, 0, f.x);
		int i = 0;
		for (Entity e : Sigf.near(h.position(), 30, x -> x instanceof Villager)) {
			Vec3 p = h.position().add(f.scale(6 + (i % 2) * 1.5)).add(side.scale((i - 2.5) * 2.0));
			p = new Vec3(p.x, Kingdom.gy((int) Math.floor(p.x), (int) Math.floor(p.z)), p.z);
			Sigf.teleport(e, p, Vec3.ZERO);
			i++;
		}
	}

	static void demoPay(int i) {
		ServerPlayer h = Sigf.host();
		Kingdom.give(h, 6);
		Sigf.teleport(h, new Vec3(Kingdom.c.getX() + 0.5, Kingdom.c.getY(), Kingdom.c.getZ() + 0.5), Vec3.ZERO);
		Sigf.lookAt(h, Kingdom.sites.get(i).sign.add(0, 2, 0));
		Sigf.after(0.8, () -> Kingdom.pay(h, i));
	}

	static void demoRecruit() {
		Kingdom.give(Sigf.host(), 1);
		var l = Sigf.near(Sigf.host().position(), 14, e -> e instanceof Villager);
		if (!l.isEmpty()) Kingdom.recruit(Sigf.host(), (Villager) l.get(0));
	}

	static void demoFight() {
		ServerPlayer h = Sigf.host();
		Kingdom.give(h, 2);
		var l = Sigf.near(h.position(), 30, e -> e instanceof GreedEntity);
		if (l.isEmpty()) { Sigf.lookAt(h, new Vec3(Kingdom.c.getX() + 8, Kingdom.c.getY() + 2, Kingdom.c.getZ())); return; }
		Entity t = l.get(0);
		for (Entity e : l) if (e.distanceTo(h) < t.distanceTo(h)) t = e;
		Sigf.lookAt(h, t.position().add(0, 1, 0));
		if (t.distanceTo(h) < 4) { h.attack(t); }
	}
}
