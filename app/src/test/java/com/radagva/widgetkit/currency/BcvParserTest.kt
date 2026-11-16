package com.radagva.widgetkit.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcvParserTest {

    // Trimmed from the bcv.org.ve home page (2026-11-16).
    private val page = """
        <div id="euro" class="col-sm-12 col-xs-12 ">
        <div class="field-content" ><div class="row recuadrotsmc">
          <div class="col-sm-6 col-xs-6"><span style="display: inline; margin-left: 15px;"> EUR </span></div>
          <div class="col-sm-6 col-xs-6 centrado textp"><strong class="strong-tb"> 1.010,56078251</strong> </div>
        </div></div></div>
        <div id="dolar" class="col-sm-12 col-xs-12 ">
        <div class="field-content" ><div class="row recuadrotsmc">
          <div class="col-sm-6 col-xs-6"><span style="display: inline; margin-left: 15px;"> USD</span></div>
          <div class="col-sm-6 col-xs-6 centrado textp" style="padding-top:2px; font-weight: bold;" > <strong class="strong-tb">914,02180000</strong>  </div>
        </div></div></div>
        Fecha Valor: <span  property="dc:date" datatype="xsd:dateTime" content="2026-11-16T00:00:00-04:00" class="date-display-single">Lunes, 16 Noviembre  2026</span>
    """.trimIndent()

    @Test
    fun parsesDollar() {
        assertEquals(BcvRate(914.0218, "2026-11-16T00:00:00-04:00"), BcvParser.parse(page, Currency.USD))
    }

    @Test
    fun parsesEuroWithThousandsSeparator() {
        assertEquals(1010.56078251, BcvParser.parse(page, Currency.EUR).value, 1e-9)
    }

    @Test
    fun missingDateIsNull() {
        val noDate = page.substringBefore("Fecha Valor")
        assertNull(BcvParser.parse(noDate, Currency.USD).valueDate)
    }

    @Test(expected = CurrencyParseException::class)
    fun missingBlockThrows() {
        BcvParser.parse("<html></html>", Currency.USD)
    }

    @Test(expected = CurrencyParseException::class)
    fun garbageValueThrows() {
        BcvParser.parseNumber("N/A")
    }
}
