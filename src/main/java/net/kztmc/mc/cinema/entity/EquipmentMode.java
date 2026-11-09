package net.kztmc.mc.cinema.entity;

/** 右パネルとインベントリ画面の、装備欄の形 */
public enum EquipmentMode {
    FULL,     // 通常: メイン・オフ・頭・胴・脚・足
    ANIMAL,   // 動物の装備: 胴=防具・カーペット・ハーネス、脚のスロット=鞍
    MAIN,     // メインハンドだけ(持ち物がそのまま内容物: 落下ブロック・額縁)
    NONE      // 装備欄なし
}