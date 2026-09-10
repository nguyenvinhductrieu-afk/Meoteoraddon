# ============================================================
# ProGuard config — Farm Meteor Addon (Fabric 1.21.x)
# ============================================================
# This config renames all internal identifiers aggressively
# while keeping everything that Meteor/Fabric/Orbit need.
# ============================================================

# ── Keep Fabric entry point ──────────────────────────────────
-keep public class com.example.addon.AddonTemplate {
    public *;
    protected *;
}

# ── Keep Module class names + constructors ───────────────────
# Orbit scans the package returned by getPackage() using reflection,
# so the class names MUST remain inside com.example.addon.modules
-keep public class com.example.addon.modules.* extends meteordevelopment.meteorclient.systems.modules.Module {
    public <init>(...);
}

# ── Keep HUD element names + constructors ────────────────────
-keep public class com.example.addon.hud.* extends meteordevelopment.meteorclient.systems.hud.HudElement {
    public <init>(...);
}

# ── Keep Command names + constructors ────────────────────────
-keep public class com.example.addon.commands.* extends meteordevelopment.meteorclient.commands.Command {
    public <init>(...);
}

# ── Keep @EventHandler methods (Orbit uses getDeclaredMethods) ─
-keepclassmembers class com.example.addon.** {
    @meteordevelopment.orbit.EventHandler <methods>;
}

# ── Keep Module lifecycle overrides ─────────────────────────
-keepclassmembers class com.example.addon.** extends meteordevelopment.meteorclient.systems.modules.Module {
    public void onActivate();
    public void onDeactivate();
    protected void onActivate();
    protected void onDeactivate();
}

# ── Keep NBT persistence methods ────────────────────────────
# Used by VillagerRoller and AutoTrade to save positions
-keepclassmembers class com.example.addon.** {
    public net.minecraft.nbt.NbtCompound toTag();
    public meteordevelopment.meteorclient.systems.modules.Module fromTag(net.minecraft.nbt.NbtCompound);
}

# ── Keep public enum constants (Meteor EnumSetting serializes by name) ──
-keepclassmembers enum com.example.addon.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final static ** *;
}

# ── Keep Mixin classes fully ─────────────────────────────────
-keep class com.example.addon.mixin.** { *; }

# ── Keep features (public API) ───────────────────────────────
-keep class com.example.addon.features.** { *; }

# ── Aggressive renaming settings ─────────────────────────────
# Rename all private/package-private members that aren't kept above.
# Do NOT use -repackageclasses — it breaks Orbit's getPackage() scan.
-allowaccessmodification

# Use very short names: a, b, aa, ab, ...
-classobfuscationdictionary  proguard-dict.txt
-obfuscationdictionary        proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# ── Strip all debug info ─────────────────────────────────────
-renamesourcefileattribute   X
# Remove line numbers → decompilers can't reconstruct original lines
# But keep enough for Fabric's crash reporting to not crash itself
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,EnclosingMethod,*Annotation*

# ── Optimization & Shrinking ─────────────────────────────────
# Tắt tính năng dọn rác (Shrink) để GIỮ LẠI toàn bộ junk code
-dontshrink

-optimizationpasses 5
# Avoid cast simplification and field fusion which can break Minecraft
-optimizations !code/simplification/cast,!field/*,!class/merging/*

# ── Suppress warnings for library classes ────────────────────
# Meteor, Minecraft, Fabric, LWJGL etc. are library jars — we don't obfuscate them
-dontwarn meteordevelopment.**
-dontwarn net.minecraft.**
-dontwarn net.fabricmc.**
-dontwarn org.lwjgl.**
-dontwarn com.mojang.**
-dontwarn org.slf4j.**
-dontwarn io.netty.**
-dontwarn com.google.**
-dontwarn org.apache.**
-dontwarn java.awt.**
-dontwarn javax.**
-dontnote **

# Bỏ qua tất cả cảnh báo thiếu class từ thư viện ngoài để ProGuard không bị crash
-ignorewarnings
