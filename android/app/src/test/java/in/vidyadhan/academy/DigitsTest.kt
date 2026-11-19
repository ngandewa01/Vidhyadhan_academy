package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.ui.onDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitsTest {
    @Test fun pastedNumbersKeepTheirDigits() {
        assertEquals("9876543210", onDigits("+91 98765 43210", "", 10, phone = true))
        assertEquals("9876543210", onDigits("098765-43210", "", 10, phone = true))
        assertEquals("123456789012", onDigits("1234 5678 9012", "", 12, phone = false))
        assertEquals("1500", onDigits("₹1,500", "", 6, phone = false))
        assertNull(onDigits("98765432101", "9876543210", 10, phone = true)) // typing an 11th digit is ignored
        assertEquals("98765", onDigits("98765", "9876", 10, phone = true))
    }
}
