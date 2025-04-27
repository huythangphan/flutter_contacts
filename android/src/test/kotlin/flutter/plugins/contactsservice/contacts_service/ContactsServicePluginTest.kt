package flutter.plugins.contactsservice.contactsservice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContactTest {

    @Test
    fun compareTo_nullParam() {
        val contact1 = Contact("id")
        contact1.givenName = "givenName"

        val contact2 = Contact("id2")

        assertThat(contact1.compareTo(contact2))
            .isGreaterThan(0)
    }

    @Test
    fun compareTo_largerParam() {
        val contact1 = Contact("id")
        contact1.givenName = "a"

        val contact2 = Contact("id2")
        contact2.givenName = "b"

        assertThat(contact1.compareTo(contact2))
            .isLessThan(0)
    }

    @Test
    fun compareTo_smallerParam() {
        val contact1 = Contact("id")
        contact1.givenName = "b"

        val contact2 = Contact("id2")
        contact2.givenName = "a"

        assertThat(contact1.compareTo(contact2))
            .isGreaterThan(0)
    }

    @Test
    fun compareTo_givenNameNull() {
        val contact1 = Contact("id")
        contact1.givenName = null

        val contact2 = Contact("id2")
        contact2.givenName = null

        assertThat(contact1.compareTo(contact2))
            .isEqualTo(0)
    }

    @Test
    fun compareTo_currentContactGivenNameNull() {
        val contact1 = Contact("id")
        contact1.givenName = null

        val contact2 = Contact("id2")
        contact2.givenName = "b"

        assertThat(contact1.compareTo(contact2))
            .isLessThan(0)
    }

    @Test
    fun compareTo_nullContact() {
        val contact1 = Contact("id")
        contact1.givenName = "a"

        assertThat(contact1.compareTo(null))
            .isGreaterThan(0)
    }

    @Test
    fun compareTo_transitiveCompare() {
        val contact1 = Contact("id")
        contact1.givenName = "b"

        val contact2 = Contact("id2")
        contact2.givenName = "a"

        val contact3 = Contact("id3")
        contact3.givenName = null

        // b > a
        assertThat(contact1.compareTo(contact2))
            .isGreaterThan(0)

        // a > null
        assertThat(contact2.compareTo(contact3))
            .isGreaterThan(0)

        // This implies => b > null
        assertThat(contact1.compareTo(contact3))
            .isGreaterThan(0)
    }
}