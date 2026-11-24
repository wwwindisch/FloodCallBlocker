package app.floodcallblocker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNormalizerTest {
    private val de = PhoneNormalizer("DE")

    @Test
    fun `all German spellings from F-10 map to the same number`() {
        val variants =
            listOf(
                "+49 37521 437183",
                "0049 37521 437183",
                "037521 437183",
                "+49 (0) 37521-437183",
            )
        variants.forEach { assertEquals(it, "+4937521437183", de.normalize(it)) }
    }

    @Test
    fun `foreign numbers keep their country`() {
        assertEquals("+41446681800", de.normalize("+41 44 668 18 00"))
        assertEquals("+442079460958", de.normalize("020 7946 0958".let { "+44 $it".replace("+44 0", "+44 ") }))
    }

    @Test
    fun `default region applies to national numbers`() {
        assertEquals("+41446681800", PhoneNormalizer("CH").normalize("044 668 18 00"))
    }

    @Test
    fun `invalid blank and suppressed input yields null`() {
        listOf("", "   ", null, "abc", "123", "+49", "Unknown", "Private", "-1", "+0000").forEach {
            assertNull(it, de.normalize(it))
        }
    }

    @Test
    fun `parse gives formatted number and region`() {
        val p = de.parse("037521 437183")
        assertNotNull(p)
        assertEquals("+49 375 21437183", p!!.formatted)
        assertEquals("DE", p.region)
    }
}
