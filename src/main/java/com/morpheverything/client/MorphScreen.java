package com.morpheverything.client;

import com.morpheverything.MorphForms;
import com.morpheverything.Net;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.PacketDistributor;

/** The morph menu (key M): search, categories, icons, favorites, reset. Esc closes it. */
public class MorphScreen extends Screen {
    private static final int CELL = 26;

    private record Entry(String form, Component name, String search, ItemStack icon, MorphForms.Category cat) {}

    private static List<Entry> cache;

    private final List<Entry> filtered = new ArrayList<>();
    private final Map<MorphForms.Category, Button> categoryButtons = new EnumMap<>(MorphForms.Category.class);
    private MorphForms.Category category = MorphForms.Category.ALL;
    private String searchText = "";
    private EditBox searchBox;
    private int scroll;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int gridLeft;
    private int gridTop;
    private int cols = 1;
    private int rows = 1;
    private Entry hovered;

    public MorphScreen() {
        super(Component.translatable("gui.morpheverything.title"));
    }

    // ------------------------------------------------------------------ data

    private static List<Entry> buildEntries() {
        List<Entry> list = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return list;
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!MorphForms.isMorphableMob(type, level)) {
                continue;
            }
            String form = MorphForms.mobForm(type);
            Component name = type.getDescription();
            SpawnEggItem egg = SpawnEggItem.byId(type);
            ItemStack icon = egg != null ? new ItemStack(egg) : new ItemStack(Items.NAME_TAG);
            list.add(new Entry(form, name, searchKey(name, form), icon, MorphForms.categoryOfMob(type)));
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!MorphForms.isMorphableBlock(block)) {
                continue;
            }
            String form = MorphForms.blockForm(block);
            Component name = block.getName();
            list.add(new Entry(form, name, searchKey(name, form), new ItemStack(block.asItem()),
                    MorphForms.Category.BLOCKS));
        }
        return list;
    }

    private static String searchKey(Component name, String form) {
        return (name.getString() + " " + form).toLowerCase(Locale.ROOT);
    }

    private boolean matchesCategory(Entry e) {
        return switch (category) {
            case ALL -> true;
            case FAVORITES -> ClientState.FAVORITES.contains(e.form());
            case RECENT -> ClientState.RECENT.contains(e.form());
            default -> e.cat() == category;
        };
    }

    private void refilter() {
        filtered.clear();
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        for (Entry e : cache) {
            if (!matchesCategory(e)) {
                continue;
            }
            if (!q.isEmpty() && !e.search().contains(q)) {
                continue;
            }
            filtered.add(e);
        }
        filtered.sort(Comparator
                .comparing((Entry e) -> ClientState.isUnlocked(e.form()) ? 0 : 1)
                .thenComparing(e -> e.name().getString()));
        for (Map.Entry<MorphForms.Category, Button> en : categoryButtons.entrySet()) {
            en.getValue().active = en.getKey() != category;
        }
        clampScroll();
    }

    private void clampScroll() {
        int totalRows = (filtered.size() + cols - 1) / cols;
        int max = Math.max(0, totalRows - rows);
        scroll = Mth.clamp(scroll, 0, max);
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        if (cache == null) {
            cache = buildEntries();
        }
        int w = Math.min(this.width - 20, 440);
        panelLeft = (this.width - w) / 2;
        panelRight = panelLeft + w;
        panelTop = 8;
        panelBottom = this.height - 8;

        int y = panelTop + 32;
        int resetW = 90;
        searchBox = new EditBox(this.font, panelLeft + 8, y, w - 16 - resetW - 4, 18,
                Component.translatable("gui.morpheverything.search"));
        searchBox.setHint(Component.translatable("gui.morpheverything.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(s -> {
            searchText = s;
            scroll = 0;
            refilter();
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.morpheverything.reset"), b -> reset())
                .bounds(panelRight - 8 - resetW, y - 1, resetW, 20).build());

        categoryButtons.clear();
        MorphForms.Category[] cats = MorphForms.Category.values();
        int perRow = 5;
        int bw = (w - 16) / perRow;
        int catY = y + 24;
        for (int i = 0; i < cats.length; i++) {
            MorphForms.Category c = cats[i];
            int bx = panelLeft + 8 + (i % perRow) * bw;
            int by = catY + (i / perRow) * 22;
            Button btn = Button.builder(
                            Component.translatable("gui.morpheverything.cat." + c.name().toLowerCase(Locale.ROOT)),
                            b -> {
                                category = c;
                                scroll = 0;
                                refilter();
                            })
                    .bounds(bx, by, bw - 2, 20).build();
            categoryButtons.put(c, btn);
            this.addRenderableWidget(btn);
        }

        gridLeft = panelLeft + 8;
        gridTop = catY + 22 * 2 + 4;
        cols = Math.max(1, (w - 16) / CELL);
        rows = Math.max(1, (panelBottom - 28 - gridTop) / CELL);
        refilter();
    }

    private void reset() {
        Net.CHANNEL.send(new Net.RequestMorph(""), PacketDistributor.SERVER.noArg());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // drawn manually in render() so the blur is never applied twice
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF0202020);
        g.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF8B8B8B);
        g.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF8B8B8B);
        g.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF8B8B8B);
        g.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF8B8B8B);

        int centerX = (panelLeft + panelRight) / 2;
        g.drawCenteredString(this.font, this.title, centerX, panelTop + 8, 0xFFFFFFFF);
        String ownForm = ClientState.ownForm();
        g.drawCenteredString(this.font,
                Component.translatable("gui.morpheverything.current", MorphForms.displayName(ownForm)),
                centerX, panelTop + 20, 0xFFFFD27F);

        super.render(g, mouseX, mouseY, partialTick);

        hovered = null;
        int visible = rows * cols;
        for (int i = 0; i < visible; i++) {
            int index = scroll * cols + i;
            if (index >= filtered.size()) {
                break;
            }
            Entry e = filtered.get(index);
            int cx = gridLeft + (i % cols) * CELL;
            int cy = gridTop + (i / cols) * CELL;
            boolean isHover = mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2;
            boolean unlocked = ClientState.isUnlocked(e.form());
            int bg = e.form().equals(ownForm) ? 0xFF3A7A3A : (isHover ? 0xFF606060 : 0xFF2E2E2E);
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, bg);
            g.renderItem(e.icon(), cx + 3, cy + 3);
            if (!unlocked) {
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0xB0000000);
            }
            if (ClientState.FAVORITES.contains(e.form())) {
                g.drawString(this.font, "*", cx + CELL - 9, cy + 1, 0xFFFFD700, true);
            }
            if (isHover) {
                hovered = e;
            }
        }

        g.drawCenteredString(this.font, Component.translatable("gui.morpheverything.hint"),
                centerX, panelBottom - 24, 0xFFAAAAAA);
        g.drawCenteredString(this.font,
                Component.translatable("gui.morpheverything.count", filtered.size()),
                centerX, panelBottom - 13, 0xFF777777);

        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.name());
            tip.add(Component.literal(hovered.form()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            tip.add(ClientState.isUnlocked(hovered.form())
                    ? Component.translatable("gui.morpheverything.unlocked").withStyle(net.minecraft.ChatFormatting.GREEN)
                    : Component.translatable("gui.morpheverything.locked").withStyle(net.minecraft.ChatFormatting.RED));
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ input

    private Entry entryAt(double mx, double my) {
        if (mx < gridLeft || my < gridTop) {
            return null;
        }
        int col = (int) ((mx - gridLeft) / CELL);
        int row = (int) ((my - gridTop) / CELL);
        if (col >= cols || row >= rows) {
            return null;
        }
        int index = (scroll + row) * cols + col;
        if (index < 0 || index >= filtered.size()) {
            return null;
        }
        return filtered.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        Entry e = entryAt(mouseX, mouseY);
        if (e == null) {
            return false;
        }
        if (button == 1 || (button == 0 && Screen.hasShiftDown())) {
            Net.CHANNEL.send(new Net.ToggleFavorite(e.form()), PacketDistributor.SERVER.noArg());
            return true;
        }
        if (button == 0 && ClientState.isUnlocked(e.form())) {
            Net.CHANNEL.send(new Net.RequestMorph(e.form()), PacketDistributor.SERVER.noArg());
            this.onClose();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!searchBox.isFocused() && ClientBootstrap.MENU_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
