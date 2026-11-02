package com.lampda.app

enum class LampType(val label: String) {
    Simple("Simple"), Rgb("RGB");
}

internal fun reportedLampType(line: String): LampType? = when (firmwareLine(line).lowercase()) {
    "simple" -> LampType.Simple
    "indexable" -> LampType.Rgb
    else -> null
}

data class RgbColor(val red: Int = 255, val green: Int = 255, val blue: Int = 255) {
    init { require(listOf(red, green, blue).all { it in 0..255 }) }
    val hex: String get() = "#%02X%02X%02X".format(red, green, blue)
    companion object {
        fun fromHex(text: String): RgbColor? {
            val digits = text.removePrefix("#")
            if (!digits.matches(Regex("[0-9a-fA-F]{6}"))) return null
            val value = digits.toInt(16)
            return RgbColor(value shr 16, value shr 8 and 255, value and 255)
        }
    }
}

enum class LampMode(
    val patternId: Int,
    val label: String,
    val supportsSpeed: Boolean,
    val lampType: LampType = LampType.Simple,
) {
    Static(0, "Fixed", false),
    Candle(1, "Candle", false),
    Lightning(2, "Lightning", false),
    Strobe(3, "Strobe", true),
    PulseOne(4, "Pulse 1", false),
    PulseTwo(5, "Pulse 2", false),
    RgbCustom(-1, "Custom color", false, LampType.Rgb),
    RgbRed(0, "Red", false, LampType.Rgb),
    RgbBlue(1, "Blue", false, LampType.Rgb),
    RgbGreen(2, "Green", false, LampType.Rgb),
    RgbCyan(3, "Cyan", false, LampType.Rgb),
    RgbYellow(4, "Yellow", false, LampType.Rgb),
    RgbPurple(5, "Purple", false, LampType.Rgb),
    RgbWhite(6, "White", false, LampType.Rgb),
    RgbJumpThree(7, "Jump 3 colors", true, LampType.Rgb),
    RgbJumpSeven(8, "Jump 7 colors", true, LampType.Rgb),
    RgbFadeThree(9, "Fade 3 colors", true, LampType.Rgb),
    RgbFadeSeven(10, "Fade 7 colors", true, LampType.Rgb),
    RgbFadeRed(11, "Fade red", true, LampType.Rgb),
    RgbFadeGreen(12, "Fade green", true, LampType.Rgb),
    RgbFadeBlue(13, "Fade blue", true, LampType.Rgb),
    RgbFadeYellow(14, "Fade yellow", true, LampType.Rgb),
    RgbFadeCyan(15, "Fade cyan", true, LampType.Rgb),
    RgbFadePurple(16, "Fade purple", true, LampType.Rgb),
    RgbFadeWhite(17, "Fade white", true, LampType.Rgb),
    RgbFadeRedGreen(18, "Fade red / green", true, LampType.Rgb),
    RgbFadeRedBlue(19, "Fade red / blue", true, LampType.Rgb),
    RgbFadeGreenBlue(20, "Fade green / blue", true, LampType.Rgb),
    RgbStrobeSeven(21, "Strobe 7 colors", true, LampType.Rgb),
    RgbStrobeRed(22, "Strobe red", true, LampType.Rgb),
    RgbStrobeGreen(23, "Strobe green", true, LampType.Rgb),
    RgbStrobeBlue(24, "Strobe blue", true, LampType.Rgb),
    RgbStrobeYellow(25, "Strobe yellow", true, LampType.Rgb),
    RgbStrobeCyan(26, "Strobe cyan", true, LampType.Rgb),
    RgbStrobePurple(27, "Strobe purple", true, LampType.Rgb),
    RgbStrobeWhite(28, "Strobe white", true, LampType.Rgb),
    PaletteWarm(100, "Warm white", true, LampType.Rgb),
    PaletteRainbow(101, "Rainbow palette", true, LampType.Rgb),
    PalettePapi(102, "Color steps", true, LampType.Rgb),
    RainbowSwirl(110, "Rainbow swirl", false, LampType.Rgb),
    RainbowFade(111, "Rainbow fade", true, LampType.Rgb),
    PerlinNoise(112, "Perlin noise", true, LampType.Rgb),
    Aurora(113, "Aurora", false, LampType.Rgb),
    Fireplace(114, "Fireplace", true, LampType.Rgb),
    Sine(115, "Sine", true, LampType.Rgb),
    Spiral(116, "Spiral", false, LampType.Rgb),
    Distortion(117, "Distortion waves", false, LampType.Rgb),
    Gravity(118, "Gravity", true, LampType.Rgb),
    Rain(119, "Rain", true, LampType.Rgb),
    Bubbles(120, "Bubbles", true, LampType.Rgb),
    Sierpinski(121, "Sierpinski", true, LampType.Rgb),
    ColorWipe(130, "Color wipe", false, LampType.Rgb),
    DoubleFill(131, "Double fill", false, LampType.Rgb),
    PingPong(132, "Ping pong", false, LampType.Rgb),
    VuMeter(140, "VU meter", false, LampType.Rgb),
    Spectrum(141, "Audio spectrum", false, LampType.Rgb),
    NudzHeineken(150, "Heineken", true, LampType.Rgb),
    NudzHuitSix(151, "8.6", true, LampType.Rgb),
    NudzViolons(152, "Violonsaouls", true, LampType.Rgb),
    NudzBeer(153, "Beer glass", true, LampType.Rgb),
    ;

    val groupId: Int get() = when {
        lampType == LampType.Simple -> when (patternId) { 0 -> 0; 1, 2 -> 1; else -> 2 }
        patternId < 100 -> 5
        patternId < 110 -> 0
        patternId < 130 -> 1
        patternId < 140 -> 2
        patternId < 150 -> 3
        else -> 4
    }
    val modeId: Int get() = when {
        lampType == LampType.Simple -> when (patternId) { 0 -> 0; 1, 2 -> patternId - 1; else -> patternId - 3 }
        patternId < 100 -> patternId + 1
        patternId < 110 -> patternId - 100
        patternId < 130 -> patternId - 110
        patternId < 140 -> patternId - 130
        patternId < 150 -> patternId - 140
        else -> patternId - 150
    }
    val category: String get() = if (lampType == LampType.Simple) when (groupId) {
        0 -> "White"; 1 -> "Calm"; else -> "Flashes"
    } else when (groupId) {
        0 -> "Palettes"; 1 -> "Calm"; 2 -> "Party"; 3 -> "Sound"; 4 -> "Images"; else -> "Color effects"
    }
    val rampLabel: String get() = when (this) {
        PaletteWarm -> "Color temperature"
        PaletteRainbow, PalettePapi, Fireplace -> "Palette"
        NudzHeineken, NudzHuitSix, NudzViolons -> "Scroll speed"
        NudzBeer -> "Fill level"
        PerlinNoise, Sine, Gravity, Rain, Bubbles, Sierpinski -> "Effect parameter"
        else -> "Speed"
    }
}

internal fun LampType.availableModes(): List<LampMode> = LampMode.entries.filter { it.lampType == this }
    .let { modes -> if (this == LampType.Rgb) modes.sortedWith(compareBy({ it.groupId }, { it.modeId })) else modes }
