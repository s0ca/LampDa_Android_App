package com.lampda.app

internal data class UsbIdentity(val vendor: Int, val product: Int, val interfaceClasses: Set<Int> = emptySet()) {
    val isSupported get() = vendor == 0x1209 && product in setOf(0x7A05, 0x8A05)
    val isBootloader get() = isSupported && 8 in interfaceClasses
    val isLamp get() = isSupported && 2 in interfaceClasses && !isBootloader
}

internal enum class UsbDetection { Empty, Runtime, Bootloader, Other, MultipleLamps }

internal fun detectUsb(identities: List<UsbIdentity>): UsbDetection = when {
    identities.isEmpty() -> UsbDetection.Empty
    identities.count { it.isSupported } > 1 -> UsbDetection.MultipleLamps
    identities.any { it.isLamp } -> UsbDetection.Runtime
    identities.any { it.isBootloader } -> UsbDetection.Bootloader
    else -> UsbDetection.Other
}

internal fun usbInstallBlocker(
    busy: Boolean,
    firmwareType: LampType?,
    confirmed: Boolean,
    hasVolume: Boolean,
    verifiedType: LampType?,
    attempted: Boolean,
): String? = when {
    busy -> "USB operation in progress."
    attempted -> "Verify the lamp before attempting another update."
    firmwareType == null -> "Choose a local UF2 file."
    !confirmed -> "Confirm the firmware source and variant."
    !hasVolume -> "Select and validate the bootloader USB volume."
    verifiedType == null -> "Confirm the lamp type for this bootloader session."
    verifiedType != firmwareType -> "The firmware filename does not match the lamp type."
    else -> null
}
