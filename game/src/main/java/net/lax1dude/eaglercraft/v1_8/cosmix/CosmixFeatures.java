package net.lax1dude.eaglercraft.v1_8.cosmix;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class CosmixFeatures {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String STORAGE_KEY = "cosmix.features.v1";
	private static final Map<Feature, Boolean> enabled = new EnumMap<>(Feature.class);
	private static boolean loaded;

	private CosmixFeatures() {
	}

	public enum Feature {
		HUD_OVERLAY("hud_overlay", "cosmix.feature.hud_overlay", true),
		COORDINATES("coordinates", "cosmix.feature.coordinates", true),
		FPS("fps", "cosmix.feature.fps", true),
		DIRECTION("direction", "cosmix.feature.direction", false),
		BIOME("biome", "cosmix.feature.biome", false),
		ARMOR("armor", "cosmix.feature.armor", false),
		EFFECTS("effects", "cosmix.feature.effects", false),
		PING("ping", "cosmix.feature.ping", false),
		KEYSTROKES("keystrokes", "cosmix.feature.keystrokes", false),
		HUNGER("hunger", "cosmix.feature.hunger", false),
		TARGET_INFO("target_info", "cosmix.feature.target_info", false),
		CROSSHAIR("crosshair", "cosmix.feature.crosshair", true),
		SKYBLOCK_INFO("skyblock_info", "cosmix.feature.skyblock_info", false),
		CONFIRM_DISCONNECT("confirm_disconnect", "cosmix.feature.confirm_disconnect", true),
		WORLD_TIME("world_time", "cosmix.feature.world_time", false),
		MOVEMENT_SPEED("movement_speed", "cosmix.feature.movement_speed", false),
		LIGHT_LEVEL("light_level", "cosmix.feature.light_level", false);

		private final String storageKey;
		private final String labelKey;
		private final boolean defaultEnabled;

		Feature(String storageKey, String labelKey, boolean defaultEnabled) {
			this.storageKey = storageKey;
			this.labelKey = labelKey;
			this.defaultEnabled = defaultEnabled;
		}

		public String labelKey() {
			return this.labelKey;
		}

		public String descriptionKey() {
			return "cosmix.feature.description." + this.storageKey;
		}
	}

	public static boolean isEnabled(Feature feature) {
		ensureLoaded();
		return enabled.getOrDefault(feature, feature.defaultEnabled);
	}

	public static void toggle(Feature feature) {
		ensureLoaded();
		enabled.put(feature, !isEnabled(feature));
		save();
	}

	public static Component toggleMessage(Feature feature) {
		return Component.translatable(
			"cosmix.feature.toggle",
			Component.translatable(feature.labelKey),
			Component.translatable(isEnabled(feature) ? "cosmix.feature.enabled" : "cosmix.feature.disabled")
		);
	}

	public static void extractHud(GuiGraphicsExtractor graphics) {
		ensureLoaded();
		if (!isEnabled(Feature.HUD_OVERLAY)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		List<Component> lines = new ArrayList<>();
		int maxWidth = 0;
		BlockPos position = player.blockPosition();
		if (isEnabled(Feature.COORDINATES)) {
			lines.add(Component.translatable(
				"cosmix.hud.coordinates",
				position.getX(),
				position.getY(),
				position.getZ()
			));
		}
		if (isEnabled(Feature.FPS)) {
			lines.add(Component.translatable("cosmix.hud.fps", minecraft.getFps()));
		}
		if (isEnabled(Feature.DIRECTION)) {
			Direction direction = player.getDirection();
			lines.add(Component.translatable("cosmix.hud.direction", Component.translatable("direction." + direction.getName())));
		}
		if (isEnabled(Feature.BIOME)) {
			String biomeName = player.level().getBiome(position).getRegisteredName();
			int namespaceSeparator = biomeName.indexOf(':');
			if (namespaceSeparator >= 0) {
				biomeName = biomeName.substring(namespaceSeparator + 1);
			}
			lines.add(Component.translatable("cosmix.hud.biome", biomeName.replace('_', ' ')));
		}
		if (isEnabled(Feature.ARMOR)) {
			String armorStatus = armorStatus(player);
			if (!armorStatus.isEmpty()) {
				lines.add(Component.translatable("cosmix.hud.armor", armorStatus));
			}
		}
		if (isEnabled(Feature.EFFECTS)) {
			for (MobEffectInstance effect : player.getActiveEffects()) {
				int seconds = Math.max(0, effect.getDuration() / 20);
				int remainingSeconds = seconds % 60;
				String duration = seconds / 60 + ":" + (remainingSeconds < 10 ? "0" : "") + remainingSeconds;
				lines.add(Component.translatable(
					"cosmix.hud.effect",
					effect.getEffect().value().getDisplayName(),
					effect.getAmplifier() + 1,
					duration
				));
			}
		}
		if (isEnabled(Feature.PING)) {
			ClientPacketListener connection = minecraft.getConnection();
			PlayerInfo playerInfo = connection == null ? null : connection.getPlayerInfo(player.getUUID());
			if (playerInfo != null) {
				lines.add(Component.translatable("cosmix.hud.ping", playerInfo.getLatency()));
			}
		}
		if (isEnabled(Feature.HUNGER)) {
			float saturation = player.getFoodData().getSaturationLevel();
			lines.add(Component.translatable(
				"cosmix.hud.hunger",
				player.getFoodData().getFoodLevel(),
				Math.round(saturation * 10.0F) / 10.0F
			));
		}
		if (isEnabled(Feature.TARGET_INFO)) {
			addTargetInfo(lines, minecraft, player);
		}
		if (isEnabled(Feature.SKYBLOCK_INFO)) {
			addSkyblockInfo(lines, player);
		}
		if (isEnabled(Feature.WORLD_TIME)) {
			long dayTime = player.level().getDefaultClockTime();
			lines.add(Component.translatable(
				"cosmix.hud.world_time",
				formatWorldTime(dayTime),
				Math.floorDiv(dayTime, 24000L) + 1L
			));
		}
		if (isEnabled(Feature.MOVEMENT_SPEED)) {
			var movement = player.getDeltaMovement();
			double speed = Math.sqrt(movement.x * movement.x + movement.z * movement.z) * 20.0D;
			lines.add(Component.translatable("cosmix.hud.movement_speed", Math.round(speed * 10.0D) / 10.0D));
		}
		if (isEnabled(Feature.LIGHT_LEVEL) && minecraft.hitResult instanceof BlockHitResult blockHitResult) {
			int lightLevel = player.level().getMaxLocalRawBrightness(blockHitResult.getBlockPos());
			lines.add(Component.translatable("cosmix.hud.light_level", lightLevel));
		}

		for (Component line : lines) {
			maxWidth = Math.max(maxWidth, minecraft.font.width(line));
		}
		if (!lines.isEmpty()) {
			int left = 4;
			int top = 4;
			graphics.nextStratum();
			graphics.fill(left, top, left + maxWidth + 8, top + lines.size() * 10 + 4, 0x80000000);
			for (int i = 0; i < lines.size(); i++) {
				graphics.text(minecraft.font, lines.get(i), left + 4, top + 3 + i * 10, -1, false);
			}
		}
		if (isEnabled(Feature.KEYSTROKES)) {
			extractKeystrokes(graphics, minecraft);
		}
	}

	private static void addTargetInfo(List<Component> lines, Minecraft minecraft, LocalPlayer player) {
		HitResult hitResult = minecraft.hitResult;
		if (hitResult instanceof EntityHitResult entityHitResult) {
			if (entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
				lines.add(Component.translatable(
					"cosmix.hud.target_health",
					livingEntity.getName(),
					Math.round(livingEntity.getHealth())
				));
			} else {
				lines.add(Component.translatable("cosmix.hud.target", entityHitResult.getEntity().getName()));
			}
		} else if (hitResult instanceof BlockHitResult blockHitResult) {
			var blockState = player.level().getBlockState(blockHitResult.getBlockPos());
			if (!blockState.isAir()) {
				lines.add(Component.translatable("cosmix.hud.target", blockState.getBlock().getName()));
			}
		}
	}

	private static String formatWorldTime(long dayTime) {
		long ticks = Math.floorMod(dayTime, 24000L);
		int hours = (int)((ticks / 1000L + 6L) % 24L);
		int minutes = (int)(ticks % 1000L * 60L / 1000L);
		return (hours < 10 ? "0" : "") + hours + ":" + (minutes < 10 ? "0" : "") + minutes;
	}

	private static void addSkyblockInfo(List<Component> lines, LocalPlayer player) {
		Scoreboard scoreboard = player.level().getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return;
		}
		String title = sidebar.getDisplayName().getString();
		String normalizedTitle = title.toLowerCase(java.util.Locale.ROOT);
		if (!normalizedTitle.contains("skyblock") && !normalizedTitle.contains("hypixel")) {
			return;
		}

		lines.add(Component.translatable("cosmix.hud.skyblock.title", sidebar.getDisplayName()));
		scoreboard.listPlayerScores(sidebar).stream()
			.sorted((first, second) -> Integer.compare(second.value(), first.value()))
			.limit(3)
			.forEach(entry -> {
				Component name = entry.display() == null ? Component.literal(entry.owner()) : entry.display();
				lines.add(Component.translatable("cosmix.hud.skyblock.entry", name, entry.value()));
			});
	}

	private static String armorStatus(LocalPlayer player) {
		EquipmentSlot[] armorSlots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		StringBuilder status = new StringBuilder();
		for (EquipmentSlot slot : armorSlots) {
			ItemStack item = player.getItemBySlot(slot);
			if (item.isEmpty()) {
				continue;
			}
			if (status.length() > 0) {
				status.append(" / ");
			}
			if (item.isDamageableItem()) {
				int remaining = item.getMaxDamage() - item.getDamageValue();
				status.append(remaining * 100 / item.getMaxDamage()).append('%');
			} else {
				status.append("OK");
			}
		}
		return status.toString();
	}

	private static void extractKeystrokes(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		int keySize = 18;
		int gap = 2;
		int left = graphics.guiWidth() - keySize * 3 - gap * 2 - 4;
		int top = 4;
		drawKey(graphics, minecraft, "W", minecraft.options.keyUp, left + keySize + gap, top, keySize, keySize);
		drawKey(graphics, minecraft, "A", minecraft.options.keyLeft, left, top + keySize + gap, keySize, keySize);
		drawKey(graphics, minecraft, "S", minecraft.options.keyDown, left + keySize + gap, top + keySize + gap, keySize, keySize);
		drawKey(graphics, minecraft, "D", minecraft.options.keyRight, left + (keySize + gap) * 2, top + keySize + gap, keySize, keySize);
		drawKey(graphics, minecraft, "JUMP", minecraft.options.keyJump, left, top + (keySize + gap) * 2, keySize * 2 + gap, keySize);
		drawKey(graphics, minecraft, "SNEAK", minecraft.options.keyShift, left + (keySize + gap) * 2, top + (keySize + gap) * 2, keySize * 2 + gap, keySize);
	}

	private static void drawKey(
		GuiGraphicsExtractor graphics,
		Minecraft minecraft,
		String label,
		KeyMapping key,
		int x,
		int y,
		int width,
		int height
	) {
		boolean pressed = key.isDown();
		graphics.fill(x, y, x + width, y + height, pressed ? 0xFF7967F2 : 0xB01B2230);
		int textColor = pressed ? 0xFFFFFFFF : 0xFFD8DCE8;
		graphics.text(minecraft.font, label, x + (width - minecraft.font.width(label)) / 2, y + (height - 8) / 2, textColor, false);
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		byte[] stored = EagRuntime.getStorage(STORAGE_KEY);
		if (stored == null) {
			return;
		}

		try {
			JSONObject settings = new JSONObject(new String(stored, StandardCharsets.UTF_8));
			for (Feature feature : Feature.values()) {
				if (settings.has(feature.storageKey)) {
					Object value = settings.get(feature.storageKey);
					if (value instanceof Boolean enabledValue) {
						enabled.put(feature, enabledValue);
					} else {
						LOGGER.warn("Ignoring non-boolean Cosmix setting '{}'", feature.storageKey);
					}
				}
			}
		} catch (JSONException exception) {
			LOGGER.warn("Ignoring malformed saved Cosmix feature settings", exception);
		}
	}

	private static void save() {
		JSONObject settings = new JSONObject();
		for (Feature feature : Feature.values()) {
			settings.put(feature.storageKey, isEnabled(feature));
		}
		EagRuntime.setStorage(STORAGE_KEY, settings.toString().getBytes(StandardCharsets.UTF_8));
	}
}
