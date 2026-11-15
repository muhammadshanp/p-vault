package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAccountTest {
    @Test
    fun readsEmailNameAndPicture() {
        val user = parseGoogleUser("""{"email":"a@b.com","name":" Shan P ","picture":"https://x/y.jpg"}""")
        assertEquals(GoogleUser("a@b.com", "Shan P", "https://x/y.jpg"), user)
    }

    @Test
    fun nameAndPictureAreOptional() {
        assertEquals(GoogleUser("a@b.com", "", ""), parseGoogleUser("""{"email":"a@b.com"}"""))
    }

    @Test
    fun noEmailMeansNoAccount() {
        assertNull(parseGoogleUser("""{"name":"Shan"}"""))
        assertNull(parseGoogleUser("not json"))
    }
}
