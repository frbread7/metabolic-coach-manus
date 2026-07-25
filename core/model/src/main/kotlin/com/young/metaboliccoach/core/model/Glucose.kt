package com.young.metaboliccoach.core.model

enum class GlucoseUnit(
    val concentrationLabel: String,
    val rateLabel: String,
) {
    MG_DL("mg/dL", "mg/dL/min"),
    MMOL_L("mmol/L", "mmol/L/min"),
    ;

    fun fromMgDl(value: Double): Double = when (this) {
        MG_DL -> value
        MMOL_L -> value / GlucoseReading.MG_DL_PER_MMOL_L
    }

    fun toMgDl(value: Double): Double = when (this) {
        MG_DL -> value
        MMOL_L -> value * GlucoseReading.MG_DL_PER_MMOL_L
    }
}

enum class GlucoseTrend(
    val symbol: String,
    val approximateRateMgDlPerMinute: Double,
) {
    RAPIDLY_FALLING("⇊", -3.0),
    FALLING("↓", -2.0),
    SLIGHTLY_FALLING("↘", -1.0),
    STABLE("→", 0.0),
    SLIGHTLY_RISING("↗", 1.0),
    RISING("↑", 2.0),
    RAPIDLY_RISING("⇈", 3.0),
    UNKNOWN("?", 0.0),
}

data class GlucoseReading(
    val id: String,
    val valueMgDl: Int,
    val trend: GlucoseTrend,
    val deltaMgDl: Int?,
    val rateMgDlPerMinute: Double?,
    val measuredAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val sourceId: String,
) {
    fun displayValue(unit: GlucoseUnit): String =
        if (unit == GlucoseUnit.MG_DL) {
            valueMgDl.toString()
        } else {
            "%.1f".format(unit.fromMgDl(valueMgDl.toDouble()))
        }

    fun displayDelta(unit: GlucoseUnit): String? = deltaMgDl?.let { delta ->
        when (unit) {
            GlucoseUnit.MG_DL -> "%+d".format(delta)
            GlucoseUnit.MMOL_L -> "%+.1f".format(unit.fromMgDl(delta.toDouble()))
        }
    }

    fun displayRateWithUnit(unit: GlucoseUnit): String? =
        rateMgDlPerMinute?.let { rate ->
            when (unit) {
                GlucoseUnit.MG_DL -> "%+.1f mg/dL/min".format(rate)
                GlucoseUnit.MMOL_L ->
                    "%+.2f mmol/L/min".format(unit.fromMgDl(rate))
            }
        }

    companion object {
        const val MG_DL_PER_MMOL_L = 18.0182
    }
}

enum class ProviderAvailability {
    AVAILABLE,
    CONFIGURATION_REQUIRED,
    PERMISSION_REQUIRED,
    APP_NOT_INSTALLED,
    PARTNER_APPROVAL_REQUIRED,
    UNSUPPORTED,
    ERROR,
}

data class ProviderStatus(
    val providerId: String,
    val displayName: String,
    val availability: ProviderAvailability,
    val detail: String,
)

enum class GlucoseProviderFailureKind {
    CONFIGURATION,
    CONNECTIVITY,
    TIMEOUT,
    AUTHENTICATION,
    SERVER,
    RESPONSE,
    UNKNOWN,
}

data class GlucoseProviderFailure(
    val kind: GlucoseProviderFailureKind,
    val detail: String,
    val retryable: Boolean,
)

sealed interface GlucoseProviderState {
    data object Idle : GlucoseProviderState

    data object ConfigurationRequired : GlucoseProviderState

    data class Loading(
        val cached: GlucoseReading?,
    ) : GlucoseProviderState

    data class Available(
        val reading: GlucoseReading,
        val refreshedAtEpochMillis: Long,
    ) : GlucoseProviderState

    data class Degraded(
        val cached: GlucoseReading?,
        val failure: GlucoseProviderFailure,
    ) : GlucoseProviderState
}

data class GlucoseDataOrigin(
    val packageName: String,
    val latestReadingAtEpochMillis: Long,
)
