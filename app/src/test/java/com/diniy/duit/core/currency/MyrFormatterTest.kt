package com.diniy.duit.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyrFormatterTest {

    @Test
    fun `format formats integer sen cleanly without float rounding`() {
        assertEquals("RM 0.00", MyrFormatter.format(0L))
        assertEquals("RM 1,250.05", MyrFormatter.format(125_005L))
        assertEquals("-RM 45.90", MyrFormatter.format(-4_590L))
        assertEquals("+MYR 300.00", MyrFormatter.format(30_000L, showCurrencyCode = true, includePlusSign = true))
    }

    @Test
    fun `parseToSen parses valid MYR inputs and rejects invalid precision`() {
        assertEquals(125_000L, MyrFormatter.parseToSen("1250"))
        assertEquals(125_050L, MyrFormatter.parseToSen("1,250.5"))
        assertEquals(4_509L, MyrFormatter.parseToSen("RM 45.09"))
        assertNull(MyrFormatter.parseToSen("-10.00"))
        assertNull(MyrFormatter.parseToSen("12.345"))
        assertNull(MyrFormatter.parseToSen(""))
        assertNull(MyrFormatter.parseToSen("abc"))
    }

    @Test
    fun `basis points percent conversion preserves exact hundredths`() {
        assertEquals(350, MyrFormatter.parsePercentToBps("3.5"))
        assertEquals(1275, MyrFormatter.parsePercentToBps("12.75%"))
        assertEquals("3.50%", MyrFormatter.formatBpsAsPercent(350))
        assertNull(MyrFormatter.parsePercentToBps("101"))
        assertNull(MyrFormatter.parsePercentToBps("1.234"))
    }
}
