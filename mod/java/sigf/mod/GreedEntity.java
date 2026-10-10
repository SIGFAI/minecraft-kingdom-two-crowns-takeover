package sigf.mod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import sigf.kit.Sigf;

/** The Greed: a masked thief that runs at the camp at night and steals coins. */
public class GreedEntity extends Zombie {
	public GreedEntity(EntityType<? extends Zombie> type, Level level) { super(type, level); }

	public static AttributeSupplier.Builder greedAttributes() {
		return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.ARMOR, 0);
	}

	@Override protected boolean isSunSensitive() { return false; }
	@Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return SigfMod.GREED_CRY; }

	@Override
	protected void addBehaviourGoals() {
		super.addBehaviourGoals();
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ArcherEntity.class, true));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hit = super.hurtServer(level, source, amount);
		if (hit) {
			level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() * 0.6, getZ(), 14, 0.3, 0.4, 0.3, 0.3);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.3, 0.3, 0.3, 0.2);
		}
		return hit;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean ok = super.doHurtTarget(level, target);
		if (ok && target instanceof ServerPlayer p) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (s.is(SigfMod.COIN)) {
					s.shrink(1);
					ItemEntity drop = new ItemEntity(level, p.getX(), p.getY() + 1, p.getZ(), new ItemStack(SigfMod.COIN));
					drop.setDeltaMovement((random.nextDouble() - 0.5) * 0.4, 0.35, (random.nextDouble() - 0.5) * 0.4);
					level.addFreshEntity(drop);
					p.sendSystemMessage(net.minecraft.network.chat.Component.literal("The Greed snatched a coin!"), true);
					Sigf.sound(SigfMod.GREED_CRY, position(), 0.8f, 1.2f);
					break;
				}
			}
		}
		return ok;
	}
}
