package com.lampda.app

internal data class LampTargetState(
    val address: String,
    val type: LampType,
    val connected: Boolean,
    val verified: Boolean,
)

internal fun controlTargetAddresses(
    selected: Set<String>,
    lamps: List<LampTargetState>,
): List<String> {
    val chosen = selected.mapNotNull { address -> lamps.firstOrNull { it.address == address } }
    if (chosen.size != selected.size) return emptyList()
    if (chosen.size == 1) return chosen.filter { it.connected }.map { it.address }
    if (chosen.map { it.type }.distinct().size > 1) return emptyList()
    return chosen.filter { it.connected && it.verified }.map { it.address }
}

internal fun selectionAfterToggle(
    selected: Set<String>, address: String, checked: Boolean, lamps: List<LampTargetState>,
): Set<String> {
    if (!checked) return selected - address
    if (address in selected) return selected
    val candidate = lamps.firstOrNull { it.address == address } ?: return selected
    if (!candidate.connected) return selected
    if (selected.isEmpty()) return setOf(address)
    val chosen = selected.mapNotNull { old -> lamps.firstOrNull { it.address == old } }
    // A checkbox records intent; verification gates dispatch, not selection.
    if (chosen.size != selected.size || (candidate.verified &&
        chosen.any { it.verified && it.type != candidate.type })) return selected
    return selected + address
}

internal fun primarySelection(active: String?, selected: Set<String>): String? =
    active?.takeIf { it in selected } ?: selected.firstOrNull()

internal fun readyPrimary(active: String?, selected: Set<String>, ready: Set<String>): String? =
    active?.takeIf { it in selected && it in ready }
        ?: selected.firstOrNull { it in ready }
        ?: primarySelection(active, selected)
