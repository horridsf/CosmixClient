package net.lax1dude.eaglercraft.v1_8.cosmix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public final class CosmixFeaturesScreen extends Screen {

	private static final int PANEL_TOP = 56;
	private static final int PANEL_BOTTOM_MARGIN = 34;
	private static final int CARD_TOP = 84;
	private static final int CARD_STEP = 34;

	private final Screen parent;
	private final List<Button> categoryButtons = new ArrayList<>();
	private final List<ModuleCard> moduleCards = new ArrayList<>();
	private Category selectedCategory = Category.ALL;
	private String searchQuery = "";
	private StringWidget moduleSummary;
	private int contentLeft;
	private int contentTop;
	private int contentBottom;
	private int scrollRow;

	public CosmixFeaturesScreen(Screen parent) {
		super(Component.translatable("cosmix.mod_menu.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.categoryButtons.clear();
		this.moduleCards.clear();
		this.contentLeft = 154;
		this.contentTop = CARD_TOP;
		this.contentBottom = Math.max(this.contentTop + CARD_STEP, this.height - 44);
		this.scrollRow = 0;

		this.addRenderableWidget(new StringWidget(
			16, 14, this.font.width("COSMIX CLIENT"), 12,
			Component.literal("COSMIX CLIENT"), this.font
		));
		this.addRenderableWidget(new StringWidget(
			16, 29, this.font.width(Component.translatable("cosmix.mod_menu.heading")), 10,
			Component.translatable("cosmix.mod_menu.heading"), this.font
		));

		EditBox searchBox = new EditBox(
			this.font, Math.max(108, this.width - 210), 16, Math.max(100, this.width - Math.max(108, this.width - 210) - 16), 20,
			Component.translatable("cosmix.mod_menu.search")
		);
		searchBox.setMaxLength(48);
		searchBox.setHint(Component.translatable("cosmix.mod_menu.search_hint").withStyle(EditBox.SEARCH_HINT_STYLE));
		searchBox.setValue(this.searchQuery);
		searchBox.setResponder(value -> {
			this.searchQuery = value;
			this.scrollRow = 0;
			this.updateModuleLayout();
		});
		this.addRenderableWidget(searchBox);

		for (Category category : Category.values()) {
			Button button = Button.builder(category.message(), widget -> {
				this.selectedCategory = category;
				this.scrollRow = 0;
				this.updateCategoryButtons();
				this.updateModuleLayout();
			}).bounds(20, 78 + this.categoryButtons.size() * 24, 112, 20).build();
			this.categoryButtons.add(button);
			this.addRenderableWidget(button);
		}

		this.moduleSummary = new StringWidget(
			this.contentLeft, 62, Math.max(40, this.width - this.contentLeft - 24), 10,
			Component.empty(), this.font
		);
		this.addRenderableWidget(this.moduleSummary);

		for (CosmixFeatures.Feature feature : CosmixFeatures.Feature.values()) {
			Button button = Button.builder(CosmixFeatures.toggleMessage(feature), widget -> {
				CosmixFeatures.toggle(feature);
				widget.setMessage(CosmixFeatures.toggleMessage(feature));
				this.updateModuleSummary();
			})
				.bounds(this.contentLeft, this.contentTop, Math.max(80, this.width - this.contentLeft - 24), 26)
				.tooltip(Tooltip.create(Component.translatable(feature.descriptionKey())))
				.build();
			button.visible = false;
			this.moduleCards.add(new ModuleCard(feature, button));
			this.addRenderableWidget(button);
		}

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.done"), widget -> this.minecraft.gui.setScreen(this.parent))
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20)
				.build()
		);

		this.updateCategoryButtons();
		this.updateModuleLayout();
	}

	private void updateCategoryButtons() {
		for (int i = 0; i < this.categoryButtons.size(); i++) {
			Category category = Category.values()[i];
			this.categoryButtons.get(i).setMessage(Component.literal(
				(category == this.selectedCategory ? "> " : "  ") + I18n.get(category.labelKey)
			));
		}
	}

	private void updateModuleLayout() {
		int mainWidth = this.width - this.contentLeft - 22;
		int columns = mainWidth >= 360 ? 2 : 1;
		int cardWidth = Math.max(80, (mainWidth - 8 * (columns - 1)) / columns);
		int visibleRows = Math.max(1, (this.contentBottom - this.contentTop) / CARD_STEP);
		List<ModuleCard> matchingCards = new ArrayList<>();

		for (ModuleCard card : this.moduleCards) {
			boolean matchesCategory = this.selectedCategory.includes(card.feature);
			boolean matchesSearch = this.matchesSearch(card.feature);
			card.button.visible = matchesCategory && matchesSearch;
			if (card.button.visible) {
				matchingCards.add(card);
			}
		}

		int totalRows = (matchingCards.size() + columns - 1) / columns;
		int maxScrollRow = Math.max(0, totalRows - visibleRows);
		this.scrollRow = Math.max(0, Math.min(this.scrollRow, maxScrollRow));

		for (int i = 0; i < matchingCards.size(); i++) {
			ModuleCard card = matchingCards.get(i);
			int row = i / columns;
			if (row < this.scrollRow || row >= this.scrollRow + visibleRows) {
				card.button.visible = false;
				continue;
			}

			int column = i % columns;
			card.button.setPosition(
				this.contentLeft + column * (cardWidth + 8),
				this.contentTop + (row - this.scrollRow) * CARD_STEP
			);
			card.button.setWidth(cardWidth);
		}

		this.updateModuleSummary(matchingCards.size());
	}

	private boolean matchesSearch(CosmixFeatures.Feature feature) {
		if (this.searchQuery.isBlank()) {
			return true;
		}
		String query = this.searchQuery.toLowerCase(Locale.ROOT);
		return I18n.get(feature.labelKey()).toLowerCase(Locale.ROOT).contains(query)
			|| I18n.get(feature.descriptionKey()).toLowerCase(Locale.ROOT).contains(query);
	}

	private void updateModuleSummary() {
		int matching = 0;
		for (ModuleCard card : this.moduleCards) {
			if (this.selectedCategory.includes(card.feature) && this.matchesSearch(card.feature)) {
				matching++;
			}
		}
		this.updateModuleSummary(matching);
	}

	private void updateModuleSummary(int matching) {
		if (this.moduleSummary == null) {
			return;
		}
		int enabled = 0;
		for (ModuleCard card : this.moduleCards) {
			if (this.selectedCategory.includes(card.feature) && this.matchesSearch(card.feature)
				&& CosmixFeatures.isEnabled(card.feature)) {
				enabled++;
			}
		}
		this.moduleSummary.setMessage(Component.translatable("cosmix.mod_menu.summary", matching, enabled));
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (scrollY != 0.0D && x >= this.contentLeft && y >= this.contentTop && y < this.contentBottom) {
			int mainWidth = this.width - this.contentLeft - 22;
			int columns = mainWidth >= 360 ? 2 : 1;
			int matching = 0;
			for (ModuleCard card : this.moduleCards) {
				if (this.selectedCategory.includes(card.feature) && this.matchesSearch(card.feature)) {
					matching++;
				}
			}
			int visibleRows = Math.max(1, (this.contentBottom - this.contentTop) / CARD_STEP);
			int totalRows = (matching + columns - 1) / columns;
			int maxScrollRow = Math.max(0, totalRows - visibleRows);
			if (maxScrollRow > 0) {
				this.scrollRow = Math.max(0, Math.min(maxScrollRow, this.scrollRow + (scrollY < 0.0D ? 1 : -1)));
				this.updateModuleLayout();
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		graphics.fillGradient(0, 0, this.width, this.height, 0xFF0C0D14, 0xFF171321);
		graphics.fill(12, PANEL_TOP, 136, this.height - PANEL_BOTTOM_MARGIN, 0xD91A1C28);
		graphics.fill(144, PANEL_TOP, this.width - 12, this.height - PANEL_BOTTOM_MARGIN, 0xD9161824);
		graphics.fill(12, PANEL_TOP - 2, this.width - 12, PANEL_TOP, 0xFF9B6BFF);
		graphics.fill(136, PANEL_TOP, 144, this.height - PANEL_BOTTOM_MARGIN, 0xFF282438);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private enum Category {
		ALL("cosmix.mod_menu.category.all"),
		HUD("cosmix.mod_menu.category.hud"),
		QUALITY_OF_LIFE("cosmix.mod_menu.category.qol"),
		PERFORMANCE("cosmix.mod_menu.category.performance"),
		SKYBLOCK("cosmix.mod_menu.category.skyblock");

		private final String labelKey;

		Category(String labelKey) {
			this.labelKey = labelKey;
		}

		private Component message() {
			return Component.translatable(this.labelKey);
		}

		private boolean includes(CosmixFeatures.Feature feature) {
			return switch (this) {
				case ALL -> true;
				case HUD -> switch (feature) {
					case COORDINATES, DIRECTION, BIOME, ARMOR, EFFECTS, KEYSTROKES, HUNGER, TARGET_INFO,
						WORLD_TIME, MOVEMENT_SPEED, LIGHT_LEVEL -> true;
					default -> false;
				};
				case QUALITY_OF_LIFE -> switch (feature) {
					case HUD_OVERLAY, CROSSHAIR, CONFIRM_DISCONNECT -> true;
					default -> false;
				};
				case PERFORMANCE -> switch (feature) {
					case FPS, PING -> true;
					default -> false;
				};
				case SKYBLOCK -> feature == CosmixFeatures.Feature.SKYBLOCK_INFO;
			};
		}
	}

	private static final class ModuleCard {
		private final CosmixFeatures.Feature feature;
		private final Button button;

		private ModuleCard(CosmixFeatures.Feature feature, Button button) {
			this.feature = feature;
			this.button = button;
		}
	}
}
