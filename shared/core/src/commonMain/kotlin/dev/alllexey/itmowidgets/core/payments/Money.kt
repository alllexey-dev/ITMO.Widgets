package dev.alllexey.itmowidgets.core.payments

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

/** An amount of rubles held in whole kopecks, so sums and comparisons are exact. Personal data: never log it. */
@JvmInline
value class Money(val kopecks: Long) : Comparable<Money> {

    val isNegative: Boolean get() = kopecks < 0

    val isPositive: Boolean get() = kopecks > 0

    operator fun plus(other: Money) = Money(kopecks + other.kopecks)

    operator fun minus(other: Money) = Money(kopecks - other.kopecks)

    operator fun unaryMinus() = Money(-kopecks)

    override fun compareTo(other: Money): Int = kopecks.compareTo(other.kopecks)

    companion object {
        val ZERO = Money(0)

        /** my.itmo's rubles, kopecks as a fraction (`1500.5`). */
        fun ofRubles(rubles: Double) = Money((rubles * KOPECKS_PER_RUBLE).roundToLong())

        const val KOPECKS_PER_RUBLE = 100
    }
}
