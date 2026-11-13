/*
 * MIT License
 *
 * Phaze port of the "ZakoHealthIndicator" mod by 3ako.
 * Original source: https://github.com/3ako/ZakoHealthIndicator
 * Copyright (c) 2023 3ako
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */
package vorga.phazeclient.implement.features.modules.other;
import net.minecraft.entity.player.PlayerEntity;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
public final class HealthIndicator extends RectHudModule {
    private static final HealthIndicator INSTANCE = new HealthIndicator();
    private static final int COLOR_RED         = 0xFFFF5555;
    private static final int COLOR_GOLD        = 0xFFFFAA00;
    private static final int COLOR_YELLOW      = 0xFFFFFF55;
    private static final int COLOR_GREEN       = 0xFF55FF55;
    private static final int COLOR_DARK_GREEN  = 0xFF00AA00;
    public final ValueSetting targetDelay = new ValueSetting(
            "Target Delay",
            "Seconds to keep showing the target's HP after the last hit"
    ).range(1, 30).setValue(10);
    public final BooleanSetting colorByHp = new BooleanSetting(
            "Color By HP",
            "Tint the HP value by remaining hearts (red <= 5, gold <= 10, yellow <= 15, green <= 20, dark-green > 20)"
    ).setValue(true);
    public final BooleanSetting hpPrefix = new BooleanSetting(
            "HP Prefix",
            "Prefix the value with \"HP\" (e.g. \"HP 10\" instead of just \"10\")"
    ).setValue(false);
    private volatile PlayerEntity target = null;
    private volatile long lastAttackMs = 0L;
    public static HealthIndicator getInstance() {
        return INSTANCE;
    }
    private HealthIndicator() {
        super("health_indicator", "Health Indicator", ModuleCategory.OTHER, 320.0f, 200.0f, 1.0f);
        background.setValue(false);
        targetDelay.setFullWidth(true);
        colorByHp.setFullWidth(true);
        hpPrefix.setFullWidth(true);
        setup(targetDelay, colorByHp, hpPrefix, cornerRounding);
    }
    public void recordAttack(PlayerEntity victim) {
        if (victim == null) {
            return;
        }
        this.target = victim;
        this.lastAttackMs = System.currentTimeMillis();
    }
    public boolean hasActiveTarget() {
        PlayerEntity t = this.target;
        if (t == null) {
            return false;
        }
        if (!t.isAlive()) {
            return false;
        }
        long windowMs = (long) (targetDelay.getValue() * 1000.0f);
        return System.currentTimeMillis() - lastAttackMs < windowMs;
    }
    public String getDisplayText() {
        if (!hasActiveTarget()) {
            return "";
        }
        PlayerEntity t = this.target;
        if (t == null) {
            return "";
        }
        int hp = (int) t.getHealth();
        return hpPrefix.isValue() ? "HP " + hp : String.valueOf(hp);
    }
    public String getPlaceholderText() {
        return hpPrefix.isValue() ? "HP 20" : "20";
    }
    public int getCurrentHpColor() {
        if (!colorByHp.isValue()) {
            return 0xFFFFFFFF;
        }
        PlayerEntity t = this.target;
        if (t == null) {
            return 0xFFFFFFFF;
        }
        float hp = t.getHealth();
        if (hp <= 5.0f) {
            return COLOR_RED;
        }
        if (hp <= 10.0f) {
            return COLOR_GOLD;
        }
        if (hp <= 15.0f) {
            return COLOR_YELLOW;
        }
        if (hp <= 20.0f) {
            return COLOR_GREEN;
        }
        return COLOR_DARK_GREEN;
    }
    @Override
    public String getDescription() {
        return "Shows the HP of the player you most recently hit (port of ZakoHealthIndicator)";
    }
    @Override
    public String getIcon() {
        return "health_indicator.png";
    }
    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
