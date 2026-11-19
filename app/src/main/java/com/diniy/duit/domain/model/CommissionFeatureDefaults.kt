package com.diniy.duit.domain.model

/** Public edition default for Settings > Optional Features > Commission Tracking. */
object CommissionFeatureDefaults {
    /** New users start with it off; anyone who already has commission data keeps it on. */
    fun initialValue(hasCommissionRecords: Boolean): Boolean = hasCommissionRecords
}
