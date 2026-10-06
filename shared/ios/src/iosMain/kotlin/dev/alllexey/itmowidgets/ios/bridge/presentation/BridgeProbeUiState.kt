package dev.alllexey.itmowidgets.ios.bridge.presentation

data class BridgeProbeState(val count: Int)

/** Sealed, so Swift switches over `onEnum(of:)`. */
sealed interface BridgeProbeEvent {
    data class Reached(val count: Int) : BridgeProbeEvent
    data object Reset : BridgeProbeEvent
}
