package com.diniy.duit.settings

import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.BuildConfig
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.domain.model.CommissionFeatureDefaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs for the personal (debug) build: Commission Tracking can never be switched off there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommissionFeaturePersonalTest {
    @Test
    fun personalEdition_alwaysKeepsCommissionOn_evenIfAValueWasStored() = runTest {
        if (BuildConfig.OPTIONAL_COMMISSION) return@runTest // the public variant is covered in testBeta
        val store = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())
        store.setCommissionFeatureEnabled(false)
        assertTrue(store.appSettings.first().commissionFeatureEnabled)
    }

    @Test
    fun publicDefault_isOffForNewUsers_andOnWhenRecordsExist() {
        assertFalse(CommissionFeatureDefaults.initialValue(hasCommissionRecords = false))
        assertTrue(CommissionFeatureDefaults.initialValue(hasCommissionRecords = true))
    }
}

class RecordCountLabelTest {
    @Test
    fun singularAndPlural() {
        org.junit.Assert.assertEquals("0 records", com.diniy.duit.feature.settings.recordCountLabel(0))
        org.junit.Assert.assertEquals("1 record", com.diniy.duit.feature.settings.recordCountLabel(1))
        org.junit.Assert.assertEquals("2 records", com.diniy.duit.feature.settings.recordCountLabel(2))
    }
}
