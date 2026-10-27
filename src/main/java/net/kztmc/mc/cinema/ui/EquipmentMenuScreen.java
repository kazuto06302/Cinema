package net.kztmc.mc.cinema.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.kztmc.mc.cinema.entity.RegistryHolders;

/**
 * 装備の装飾の設定画面(スロットの右クリックで開く)。
 * エンチャント光沢のオン/オフと、防具のトリム(模様と素材)。変更は、すぐNPCに反映される。
 */
public class EquipmentMenuScreen extends Screen {
    private static final List<String> PATTERNS = List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex",
            "tide", "snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt");
    private static final List<String> PATTERN_NAMES = List.of("歩哨", "砂丘", "海岸", "ワイルド", "守護者", "目", "ヴェックス",
            "潮", "鼻", "あばら骨", "尖塔", "道しるべ", "造形者", "沈黙", "召集者", "宿主", "流れ", "稲妻");

    /** トリム素材のID、アイコンにするアイテムのID、表示名 */
    private static final List<String> MATERIALS = List.of("quartz", "iron", "netherite", "redstone", "copper", "gold",
            "emerald", "diamond", "lapis", "amethyst", "resin");
    private static final List<String> MATERIAL_ITEMS = List.of("quartz", "iron_ingot", "netherite_ingot", "redstone",
            "copper_ingot", "gold_ingot", "emerald", "diamond", "lapis_lazuli", "amethyst_shard", "resin_brick");
    private static final List<String> MATERIAL_NAMES = List.of("石英", "鉄", "ネザライト", "レッドストーン", "銅", "金",
            "エメラルド", "ダイヤモンド", "ラピスラズリ", "アメジスト", "樹脂");

    private static final int COLS = 6;

    private final Screen parent;
    private final String slotLabel;
    private final Supplier<ItemStack> getter;
    private final Consumer<ItemStack> setter;

    private String pattern;    // 選択中の模様(なければ null)
    private String material;   // 選択中の素材(なければ null)

    public EquipmentMenuScreen(Screen parent, String slotLabel, Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        super(Component.literal("装備の装飾"));
        this.parent = parent;
        this.slotLabel = slotLabel;
        this.getter = getter;
        this.setter = setter;

        // いまのトリムを、選択状態にする
        var trim = getter.get().get(DataComponents.TRIM);
        if (trim != null) {
            pattern = trim.pattern().unwrapKey().map(k -> k.identifier().getPath()).orElse(null);
            material = trim.material().unwrapKey().map(k -> k.identifier().getPath()).orElse(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private static ItemStack itemOf(String path) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void init() {
        ItemStack stack = getter.get();
        int w = Theme.u(240);
        int x = (width - w) / 2;
        int y = Math.max(Theme.u(10), height / 2 - Theme.u(150));
        int btnH = Math.max(Theme.u(22), 22);
        int gap = Math.max(1, Theme.u(3));
        int cell = Math.max(Theme.u(24), 24);

        y += cell + gap;   // 見出し(アイテムの名前)の分

        addRenderableWidget(new ModernCheckbox(x, y, w, btnH, "エンチャント(光る)",
                stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null, on -> {
            ItemStack s = getter.get().copy();
            if (on) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            else s.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            setter.accept(s);
        }));
        y += btnH + gap * 2;

        if (stack.is(ItemTags.TRIMMABLE_ARMOR)) {
            y = addGrid(x, y, w, cell, gap, "トリムの模様(鍛冶型)", PATTERNS.size(),
                    i -> itemOf(PATTERNS.get(i) + "_armor_trim_smithing_template"),
                    i -> PATTERN_NAMES.get(i), i -> PATTERNS.get(i).equals(pattern), i -> {
                        pattern = PATTERNS.get(i).equals(pattern) ? null : PATTERNS.get(i);
                        applyTrim();
                    });
            y = addGrid(x, y, w, cell, gap, "トリムの素材(鉱石など)", MATERIALS.size(),
                    i -> itemOf(MATERIAL_ITEMS.get(i)),
                    i -> MATERIAL_NAMES.get(i), i -> MATERIALS.get(i).equals(material), i -> {
                        material = MATERIALS.get(i).equals(material) ? null : MATERIALS.get(i);
                        applyTrim();
                    });
        }

        addRenderableWidget(new ModernButton(x, y, w, btnH, "閉じる", ModernButton.Style.DEFAULT, this::onClose));
    }

    /** アイコンの格子を、見出しつきで追加して、次の y を返す */
    private int addGrid(int x, int y, int w, int cell, int gap, String title, int count,
                        java.util.function.IntFunction<ItemStack> icon, java.util.function.IntFunction<String> name,
                        java.util.function.IntPredicate selected, java.util.function.IntConsumer onPick) {
        titles.add(new int[] {x, y});
        titleTexts.add(title);
        y += Theme.textH() + Theme.u(4);
        int cw = (w - gap * (COLS - 1)) / COLS;
        for (int i = 0; i < count; i++) {
            int cx = x + (i % COLS) * (cw + gap);
            int cy = y + (i / COLS) * (cell + gap);
            final int index = i;
            addRenderableWidget(new ItemButton(cx, cy, cw, cell, icon.apply(i), ItemStack.EMPTY, b -> {
                onPick.accept(index);
                rebuildWidgets();
            }).setSelected(selected.test(i)).tip(name.apply(i)));
        }
        int rows = (count + COLS - 1) / COLS;
        return y + rows * (cell + gap) + gap;
    }

    private final java.util.List<int[]> titles = new java.util.ArrayList<>();
    private final java.util.List<String> titleTexts = new java.util.ArrayList<>();

    @Override
    protected void rebuildWidgets() {
        titles.clear();
        titleTexts.clear();
        super.rebuildWidgets();
    }

    /** 模様と素材が両方あればトリムを付け、どちらかが欠ければ外す */
    private void applyTrim() {
        ItemStack s = getter.get().copy();
        var access = Minecraft.getInstance().level.registryAccess();
        var patternHolder = pattern == null ? null : RegistryHolders.get(access, Registries.TRIM_PATTERN, pattern);
        var materialHolder = material == null ? null : RegistryHolders.get(access, Registries.TRIM_MATERIAL, material);
        if (patternHolder != null && materialHolder != null) {
            s.set(DataComponents.TRIM, new ArmorTrim(materialHolder, patternHolder));
        } else {
            s.remove(DataComponents.TRIM);
        }
        setter.accept(s);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int w = Theme.u(240);
        int x = (width - w) / 2;
        int y = Math.max(Theme.u(10), height / 2 - Theme.u(150));

        // 見出し: アイテムのアイコンと名前
        ItemStack stack = getter.get();
        g.item(stack, x, y + 4);
        Theme.text(g, font, slotLabel + ": " + stack.getHoverName().getString(), x + 22, y + 8, Theme.TEXT);

        for (int i = 0; i < titles.size(); i++) {
            Theme.text(g, font, titleTexts.get(i), titles.get(i)[0], titles.get(i)[1], Theme.TEXT_DIM);
        }
    }
}