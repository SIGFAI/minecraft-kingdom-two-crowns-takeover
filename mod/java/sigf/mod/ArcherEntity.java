package sigf.mod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;

/** A recruited peasant with a bow: shoots the Greed, ignores the monarch. */
public class ArcherEntity extends Skeleton {
	public ArcherEntity(EntityType<? extends Skeleton> type, Level level) { super(type, level); }

	public static AttributeSupplier.Builder archerAttributes() {
		return Skeleton.createAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.FOLLOW_RANGE, 30);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.removeAllGoals(g -> true);
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, GreedEntity.class, true));
	}

	@Override public void aiStep() { super.aiStep(); if (isOnFire() && !isInLava()) clearFire(); }
	@Override public boolean isShaking() { return false; }
}
