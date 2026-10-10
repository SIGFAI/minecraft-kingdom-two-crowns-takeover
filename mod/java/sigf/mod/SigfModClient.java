package sigf.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import sigf.kit.Sigf;

public final class SigfModClient implements ClientModInitializer {
	static final Identifier GREED_TEX = Identifier.fromNamespaceAndPath("sigf", "textures/entity/greed.png");
	static final Identifier ARCHER_TEX = Identifier.fromNamespaceAndPath("sigf", "textures/entity/archer.png");
	private static boolean cam;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(SigfMod.GREED, ctx -> new ZombieRenderer(ctx) {
			@Override public Identifier getTextureLocation(ZombieRenderState s) { return GREED_TEX; }
		});
		EntityRendererRegistry.register(SigfMod.ARCHER, ctx -> new SkeletonRenderer(ctx) {
			@Override public Identifier getTextureLocation(SkeletonRenderState s) { return ARCHER_TEX; }
		});
		// Third person so the crown on the monarch is on screen (demo and player mode).
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) { cam = false; return; }
			if (!cam && (Sigf.isDemo() || Sigf.isPlayer())) { cam = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
		});
	}
}
