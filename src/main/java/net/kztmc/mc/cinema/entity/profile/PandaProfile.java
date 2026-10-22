package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * パンダ。
 *  - ポーズ: 立つ・座る・仰向け・転がる・くしゃみ・食べる。フラグと、その移行量(sitAmount など)を書く。
 *  - 転がる: rollCounter(0〜32)で決まる。時間スライダーの0〜32tickで1回転する。
 *  - くしゃみ: sneezeCounter(0〜20)。食べる: eatCounter(0より大きければ食べている)。どちらもデータ経由なので、メソッドで書く。
 *  - 種類: 主な遺伝子と隠れた遺伝子の両方を同じにする。茶色は、両方が茶色のときだけ、体の色が茶色になる。
 *    ほかの種類(のんびり・心配性など)は、顔つきが変わる。
 * 食べているときの手の中のもの(竹など)は、装備の「メイン」で持たせる。
 */
public final class PandaProfile extends EntityProfile {
    private static final String[] GENES = {"NORMAL", "LAZY", "WORRIED", "PLAYFUL", "BROWN", "WEAK", "AGGRESSIVE"};

    public PandaProfile() {
        super("minecraft:panda");
        choice("pose", "ポーズ", 0, "立つ", "座る", "仰向け", "転がる", "くしゃみ", "食べる");
        choice("gene", "種類", 0, "通常", "のんびり", "心配性", "遊び好き", "茶色", "弱虫", "攻撃的");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int pose = options.getOrDefault("pose", 0);
        int n = (int) Math.floor(time);

        flag(e, List.of("sit", "setSitting"), "sitAmount", pose == 1 || pose == 5);   // 食べるときも座る
        flag(e, List.of("setOnBack"), "onBackAmount", pose == 2);
        flag(e, List.of("roll", "setRolling"), "rollAmount", pose == 3);
        EntityReflect.setInt(e, "rollCounter", pose == 3 ? Math.min(n, 32) : 0);

        flag(e, List.of("sneeze", "setSneezing"), null, pose == 4);
        EntityReflect.invokeFirst(e, List.of("setSneezeCounter"), pose == 4 ? Math.min(n, 20) : 0);

        flag(e, List.of("eat", "setEating"), null, pose == 5);
        EntityReflect.invokeFirst(e, List.of("setEatCounter"), pose == 5 ? Math.max(1, n) : 0);

        String gene = GENES[Math.max(0, Math.min(GENES.length - 1, options.getOrDefault("gene", 0)))];
        EntityReflect.invokeWithEnumArg(e, "setMainGene", gene);
        EntityReflect.invokeWithEnumArg(e, "setHiddenGene", gene);
    }

    private static void flag(Entity e, List<String> methods, String amountField, boolean on) {
        EntityReflect.invokeFirst(e, methods, on);
        if (amountField != null) {
            float v = on ? 1f : 0f;
            EntityReflect.setFloat(e, amountField, v);
            EntityReflect.setFloat(e, amountField + "O", v);
        }
    }
}