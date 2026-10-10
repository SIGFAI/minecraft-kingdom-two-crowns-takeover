package sigf.mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;
import sigf.kit.SigfKit;

/** The kingdom: camp, build sites, peasants, archers, the day and night cycle and the waves of Greed. */
public final class Kingdom {
	static final class Site {
		final String kind; final BlockPos base; Vec3 sign; int tier;
		Site(String kind, BlockPos base, Vec3 sign) { this.kind = kind; this.base = base; this.sign = sign; }
		int cost() { return tier == 0 ? 3 : 5; }
		String tag(int i) { return "sigf_site" + i; }
	}
	record Op(BlockPos pos, BlockState state) {}

	static BlockPos c;
	static final List<Site> sites = new ArrayList<>();
	static final ArrayDeque<Op> queue = new ArrayDeque<>();
	static final Map<UUID, Entity> crowns = new HashMap<>();
	static double cycle = 90;
	static double clock = 0, lastPhase = 0;
	static long age = 0;
	static int night = 0, slain = 0;
	static boolean ready = false;

	static ServerLevel lv() { return Sigf.level(); }
	static int gy(int x, int z) { return lv().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z); }
	static void put(int x, int y, int z, Block b) { lv().setBlockAndUpdate(new BlockPos(x, y, z), b.defaultBlockState()); }
	static double phase() { return ((clock % cycle) + cycle) % cycle / cycle; }
	public static void setPhase(double p) { clock = p * cycle; lastPhase = p; }

	// ---- setup ----

	static void setup() {
		Vec3 cv = SigfKit.center();
		c = new BlockPos((int) Math.floor(cv.x), (int) Math.floor(cv.y), (int) Math.floor(cv.z));
		int cx = c.getX(), cz = c.getZ();
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
			if (dx * dx + dz * dz <= 18) { int y = gy(cx + dx, cz + dz) - 1; lv().setBlockAndUpdate(new BlockPos(cx + dx, y, cz + dz), Blocks.DIRT_PATH.defaultBlockState()); }
		}
		put(cx + 3, gy(cx + 3, cz + 1), cz + 1, Blocks.CAMPFIRE);
		int by = gy(cx - 3, cz + 2);
		put(cx - 3, by, cz + 2, Blocks.OAK_FENCE); put(cx - 3, by + 1, cz + 2, Blocks.OAK_FENCE); put(cx - 3, by + 2, cz + 2, Blocks.OAK_FENCE);
		put(cx - 3, by + 3, cz + 2, Blocks.BANNER.red());
		put(cx - 2, gy(cx - 2, cz - 3), cz - 3, Blocks.HAY_BLOCK);
		put(cx - 3, gy(cx - 3, cz - 3), cz - 3, Blocks.HAY_BLOCK);
		put(cx + 2, gy(cx + 2, cz - 3), cz - 3, Blocks.BARREL);
		put(cx + 3, gy(cx + 3, cz - 2), cz - 2, Blocks.BARREL);
		sites.clear();
		sites.add(new Site("Wall", new BlockPos(cx + 10, 0, cz), new Vec3(cx + 7.5, 0, cz + 0.5)));
		sites.add(new Site("Wall", new BlockPos(cx - 10, 0, cz), new Vec3(cx - 6.5, 0, cz + 0.5)));
		sites.add(new Site("Tower", new BlockPos(cx, 0, cz + 10), new Vec3(cx + 0.5, 0, cz + 6.5)));
		sites.add(new Site("Tower", new BlockPos(cx, 0, cz - 10), new Vec3(cx + 0.5, 0, cz - 6.5)));
		for (int i = 0; i < sites.size(); i++) {
			Site s = sites.get(i);
			int y = gy((int) Math.floor(s.sign.x), (int) Math.floor(s.sign.z));
			s.sign = new Vec3(s.sign.x, y, s.sign.z);
			put((int) Math.floor(s.sign.x), y, (int) Math.floor(s.sign.z), Blocks.OAK_FENCE);
			put((int) Math.floor(s.sign.x), y + 1, (int) Math.floor(s.sign.z), Blocks.GOLD_BLOCK);
			Sigf.command("summon text_display " + s.sign.x + " " + (y + 2.6) + " " + s.sign.z + " {Tags:[\"" + s.tag(i) + "\"],billboard:\"center\",background:1073741824,text:{text:\"x\"}}");
			Sigf.command("summon interaction " + s.sign.x + " " + (y + 0.5) + " " + s.sign.z + " {Tags:[\"" + s.tag(i) + "\"],width:1.6f,height:2.5f,response:true}");
			refreshSign(i);
		}
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3, r = 3.5 + (i % 2);
			Vec3 p = new Vec3(cx + 0.5 + Math.cos(a) * r, gy(cx + (int) (Math.cos(a) * r), cz + (int) (Math.sin(a) * r)), cz + 0.5 + Math.sin(a) * r);
			Villager v = Sigf.spawn(EntityTypes.VILLAGER, p);
			if (v != null) { v.setCustomName(Component.literal("Peasant")); v.setPersistenceRequired(); }
		}
		for (ServerPlayer pl : Sigf.players()) {
			pl.getInventory().add(new ItemStack(SigfMod.COIN, 8));
			pl.getInventory().add(new ItemStack(Items.STONE_SWORD));
			pl.sendSystemMessage(Component.literal("Right-click a Peasant with a coin to hire an archer. Pay the gold signs to build."), false);
		}
		ready = true;
		Sigf.log("kingdom ready at " + c);
	}

	static void refreshSign(int i) {
		Site s = sites.get(i);
		String t = s.tier >= 2 ? "Stone " + s.kind + " (done)" : (s.tier == 0 ? "Build " + s.kind : "Upgrade to stone " + s.kind);
		String cost = s.tier >= 2 ? "" : "  " + s.cost() + " coins";
		String color = s.tier >= 2 ? "green" : "gold";
		Sigf.command("data merge entity @e[tag=" + s.tag(i) + ",type=minecraft:text_display,limit=1] {text:{text:\"" + t + cost + "\",color:\"" + color + "\",bold:true}}");
	}

	// ---- per tick ----

	public static void tick() {
		if (!ready) return;
		age++;
		clock += 0.05;
		if (age % 5 == 0) {
			long t = skyTime(phase());
			Sigf.command("time set " + t);
			double p = phase();
			if (crossed(p, 0.44)) { Sigf.title("NIGHT FALLS", "The Greed comes for your crown", 3); Sigf.sound(SigfMod.NIGHT_HORN, SigfKit.center(), 2f, 1f); }
			if (crossed(p, 0.50)) wave(0);
			if (crossed(p, 0.68)) wave(1);
			if (crossed(p, 0.93)) dawn();
			if (p < lastPhase) night++;
			lastPhase = p;
		}
		for (int i = 0; i < 1 && !queue.isEmpty(); i++) {
			Op o = queue.poll();
			lv().setBlockAndUpdate(o.pos(), o.state());
			lv().sendParticles(new DustParticleOptions(0xD8C9A0, 1.0f), o.pos().getX() + 0.5, o.pos().getY() + 0.5, o.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
			if (queue.size() % 2 == 0) Sigf.sound(SoundEvents.WOOD_PLACE, Vec3.atCenterOf(o.pos()), 0.8f, 0.8f + (queue.size() % 7) * 0.05f);
		}
		crowns();
		if (age % 20 == 0) greedHoming();
		if (age % 280 == 0) {
			for (ServerPlayer p : Sigf.players()) { give(p, 1); p.sendSystemMessage(Component.literal("Taxes collected: +1 coin"), true); }
		}
		if (age % 100 == 50) hud();
	}

	/** Long golden dusk instead of pitch black, so the fights stay readable. */
	static long skyTime(double p) {
		double t;
		if (p < 0.4) t = 1000 + p / 0.4 * 10500;
		else if (p < 0.92) t = 11500 + (p - 0.4) / 0.52 * 1700;
		else t = 22800 + (p - 0.92) / 0.08 * 2200;
		return (long) t % 24000;
	}

	static boolean crossed(double p, double mark) { return lastPhase < mark && p >= mark; }

	static void hud() {
		long archers = Sigf.near(SigfKit.center(), 60, e -> e instanceof ArcherEntity).size();
		for (ServerPlayer p : Sigf.players()) {
			int coins = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) { ItemStack s = p.getInventory().getItem(i); if (s.is(SigfMod.COIN)) coins += s.getCount(); }
			p.sendSystemMessage(Component.literal("Coins: " + coins + "    Archers: " + archers + "    Greed slain: " + slain), true);
		}
	}

	static void give(ServerPlayer p, int n) {
		if (!p.getInventory().add(new ItemStack(SigfMod.COIN, n))) lv().addFreshEntity(new ItemEntity(lv(), p.getX(), p.getY() + 1, p.getZ(), new ItemStack(SigfMod.COIN, n)));
	}

	static void crowns() {
		for (ServerPlayer p : Sigf.players()) {
			if (p.isSpectator()) continue;
			Entity e = crowns.get(p.getUUID());
			if (e == null || e.isRemoved()) {
				String tag = "sigf_crown_" + p.getUUID().toString().substring(0, 8);
				Sigf.command("summon item_display " + p.getX() + " " + (p.getY() + 2.3) + " " + p.getZ()
					+ " {Tags:[\"" + tag + "\"],item:{id:\"sigf:crown\",count:1},billboard:\"vertical\",teleport_duration:2,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]}}");
				var found = Sigf.near(p.position().add(0, 2.3, 0), 2, x -> x.entityTags().contains(tag));
				if (found.isEmpty()) continue;
				e = found.get(0);
				crowns.put(p.getUUID(), e);
			}
			double bob = Math.sin(age * 0.15) * 0.05;
			Sigf.teleport(e, new Vec3(p.getX(), p.getY() + 2.05 + bob, p.getZ()), Vec3.ZERO);
			if (age % 6 == 0) lv().sendParticles(new DustParticleOptions(0xFFD030, 0.7f), p.getX(), p.getY() + 2.2, p.getZ(), 1, 0.25, 0.1, 0.25, 0);
		}
	}

	// ---- coins and recruiting ----

	static void coinTrail(Vec3 from, Vec3 to) {
		for (int i = 0; i <= 14; i++) {
			double t = i / 14.0;
			Vec3 p = from.lerp(to, t).add(0, Math.sin(t * Math.PI) * 1.2, 0);
			lv().sendParticles(new DustParticleOptions(0xFFC830, 1.4f), p.x, p.y, p.z, 2, 0.04, 0.04, 0.04, 0);
		}
		Sigf.sound(SigfMod.COIN_DING, to, 1f, 1f + (float) Math.random() * 0.2f);
	}

	/** Hires a peasant as an archer. */
	public static boolean recruit(ServerPlayer p, Villager v) {
		coinTrail(p.position().add(0, 1.4, 0), v.position().add(0, 1.5, 0));
		Vec3 pos = v.position();
		v.discard();
		ArcherEntity a = Sigf.spawn(SigfMod.ARCHER, pos);
		if (a == null) return false;
		arm(a, "Archer");
		Sigf.particles(ParticleTypes.HAPPY_VILLAGER, pos.add(0, 1, 0), 18, 0.5);
		Sigf.particles(ParticleTypes.FIREWORK, pos.add(0, 1.2, 0), 14, 0.3);
		Sigf.sound(SoundEvents.PLAYER_LEVELUP, pos, 0.7f, 1.4f);
		return true;
	}

	static void arm(ArcherEntity a, String name) {
		a.setCustomName(Component.literal(name));
		a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		a.setDropChance(EquipmentSlot.MAINHAND, 0f);
		a.reassessWeaponGoal();
		a.setPersistenceRequired();
	}

	static boolean takeCoins(ServerPlayer p, int n) {
		int have = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) { ItemStack s = p.getInventory().getItem(i); if (s.is(SigfMod.COIN)) have += s.getCount(); }
		if (have < n) return false;
		for (int i = 0; i < p.getInventory().getContainerSize() && n > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(SigfMod.COIN)) { int k = Math.min(n, s.getCount()); s.shrink(k); n -= k; }
		}
		return true;
	}

	static int siteAt(Entity e) {
		for (int i = 0; i < sites.size(); i++) if (e.entityTags().contains(sites.get(i).tag(i))) return i;
		return -1;
	}

	/** Pays a build site; false when the monarch cannot afford it. */
	public static boolean pay(ServerPlayer p, int i) {
		Site s = sites.get(i);
		if (s.tier >= 2 || !queue.isEmpty()) return false;
		if (!takeCoins(p, s.cost())) { p.sendSystemMessage(Component.literal("Not enough coins: " + s.cost() + " needed."), true); return false; }
		coinTrail(p.position().add(0, 1.4, 0), s.sign.add(0, 1.8, 0));
		s.tier++;
		build(s);
		refreshSign(i);
		return true;
	}

	// ---- building ----

	static void op(int x, int y, int z, Block b) { queue.add(new Op(new BlockPos(x, y, z), b.defaultBlockState())); }

	static void build(Site s) {
		int bx = s.base.getX(), bz = s.base.getZ();
		if (s.kind.equals("Wall")) {
			Block body = s.tier == 1 ? Blocks.OAK_PLANKS : Blocks.STONE_BRICKS;
			int h = s.tier == 1 ? 3 : 4;
			for (int dz = -6; dz <= 6; dz++) {
				int gnd = gy(bx, bz + dz);
				if (s.tier == 2 && dz != 0) gnd = lowestWall(bx, bz + dz);
				for (int k = 0; k < h; k++) {
					if (dz == 0 && k < 3) continue;
					op(bx, gnd + k, bz + dz, (s.tier == 1 && k == h - 1) ? Blocks.OAK_FENCE : body);
				}
				if (s.tier == 2 && (dz & 1) == 0) op(bx, gnd + h, bz + dz, Blocks.STONE_BRICK_SLAB);
			}
			if (s.tier == 2) { op(bx, lowestWall(bx, bz - 2) + 5, bz - 2, Blocks.LANTERN); op(bx, lowestWall(bx, bz + 2) + 5, bz + 2, Blocks.LANTERN); }
		} else {
			Block post = s.tier == 1 ? Blocks.OAK_LOG : Blocks.STONE_BRICKS;
			Block deck = s.tier == 1 ? Blocks.OAK_PLANKS : Blocks.STONE_BRICKS;
			Block rail = s.tier == 1 ? Blocks.OAK_FENCE : Blocks.STONE_BRICK_WALL;
			int gnd = gy(bx, bz);
			for (int k = 0; k < 5; k++) for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) op(bx + dx, gnd + k, bz + dz, post);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) op(bx + dx, gnd + 5, bz + dz, deck);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) op(bx + dx, gnd + 6, bz + dz, rail);
			if (s.tier == 2) for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) op(bx + dx, gnd + 7, bz + dz, Blocks.LANTERN);
			final int ty = gnd + 6;
			Sigf.after(3.5, () -> {
				ArcherEntity a = Sigf.spawn(SigfMod.ARCHER, new Vec3(bx + 0.5, ty, bz + 0.5));
				if (a == null) return;
				arm(a, "Tower Archer");
				Sigf.particles(ParticleTypes.FIREWORK, new Vec3(bx + 0.5, ty + 1, bz + 0.5), 14, 0.3);
			});
		}
	}

	/** Ground level under an existing wall column: wooden wall blocks count as ground for the heightmap, so skip them. */
	static int lowestWall(int x, int z) {
		int y = gy(x, z);
		while (y > -60 && (lv().getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.OAK_PLANKS) || lv().getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.OAK_FENCE))) y--;
		return y;
	}

	// ---- the Greed ----

	static Vec3 ring(double angle, double r) {
		int x = (int) Math.floor(c.getX() + 0.5 + Math.cos(angle) * r), z = (int) Math.floor(c.getZ() + 0.5 + Math.sin(angle) * r);
		return new Vec3(x + 0.5, gy(x, z), z + 0.5);
	}

	static GreedEntity greed(Vec3 pos, int kind) {
		GreedEntity g = Sigf.spawn(SigfMod.GREED, pos);
		if (g == null) return null;
		g.setPersistenceRequired();
		if (kind == 1) {
			g.getAttribute(Attributes.SCALE).setBaseValue(1.9);
			g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(70);
			g.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
			g.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
			g.setHealth(70);
			g.setCustomName(Component.literal("The Breeder"));
			g.setCustomNameVisible(true);
			g.setGlowingTag(true);
		} else if (kind == 2) {
			g.getAttribute(Attributes.SCALE).setBaseValue(0.65);
			g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(7);
			g.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33);
			g.setHealth(7);
		}
		return g;
	}

	static void wave(int pulse) {
		int count = 4 + night * 2 + pulse * 3;
		Sigf.title(pulse == 0 ? "THE GREED ARRIVES" : "THE BREEDER COMES", pulse == 0 ? "Night " + (night + 1) + ": defend the camp" : "Shoot it down!", 3);
		double base = Math.random() * Math.PI * 2;
		for (int portal = 0; portal < 2; portal++) {
			Vec3 pos = ring(base + portal * 2.2, 22);
			Sigf.particles(ParticleTypes.PORTAL, pos.add(0, 1, 0), 80, 1.2);
			Sigf.particles(ParticleTypes.WITCH, pos.add(0, 1, 0), 30, 1.0);
			Sigf.sound(SigfMod.GREED_CRY, pos, 1.5f, 0.8f);
			for (int i = 0; i < count / 2 + 1; i++) greed(pos.add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1), (i % 3 == 2) ? 2 : 0);
		}
		if (pulse == 1) greed(ring(base + 1, 22), 1);
		Sigf.sound(SoundEvents.WITHER_SPAWN, SigfKit.center(), 0.4f, 1.6f);
	}

	static void greedHoming() {
		for (Entity e : Sigf.near(SigfKit.center(), 60, x -> x instanceof GreedEntity)) {
			GreedEntity g = (GreedEntity) e;
			if (g.getTarget() == null) g.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.1);
		}
	}

	static void dawn() {
		Sigf.title("DAWN", "The Greed flees the sun", 3);
		for (Entity e : Sigf.near(SigfKit.center(), 80, x -> x instanceof GreedEntity)) {
			Sigf.particles(ParticleTypes.PORTAL, e.position().add(0, 1, 0), 40, 0.5);
			e.discard();
		}
	}

	/** Called when any living thing died. */
	public static void onDeath(Entity e) {
		if (e instanceof GreedEntity g) {
			slain++;
			boolean big = g.getBbHeight() > 2.5;
			int n = big ? 6 : (Math.random() < 0.6 ? 2 : 1);
			for (int i = 0; i < n; i++) {
				ItemEntity it = new ItemEntity(lv(), g.getX(), g.getY() + 0.8, g.getZ(), new ItemStack(SigfMod.COIN));
				it.setDeltaMovement((Math.random() - 0.5) * 0.4, 0.3 + Math.random() * 0.2, (Math.random() - 0.5) * 0.4);
				lv().addFreshEntity(it);
			}
			Sigf.particles(ParticleTypes.PORTAL, g.position().add(0, 1, 0), 40, 0.5);
			lv().sendParticles(new DustParticleOptions(0xFFC830, 1.4f), g.getX(), g.getY() + 1, g.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
			Sigf.sound(SigfMod.COIN_DING, g.position(), 1f, 1f);
		}
	}
}
