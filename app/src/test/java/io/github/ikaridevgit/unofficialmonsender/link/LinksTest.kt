package io.github.ikaridevgit.unofficialmonsender.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun firstLinkStripsSentencePunctuation() {
        assertEquals("https://e621.net/posts/1", Links.firstLink("look: https://e621.net/posts/1."))
        assertEquals("https://example.com/a_(b)", Links.firstLink("(see https://example.com/a_(b))"))
        assertNull(Links.firstLink("no links here"))
        assertNull(Links.firstLink("ftp://example.com/file"))
    }

    @Test
    fun hostRulesMatchSubdomainsOnly() {
        assertTrue(Links.matchesHostRule("static1.e621.net", "e621.net"))
        assertTrue(Links.matchesHostRule("e621.net", "e621.net"))
        assertFalse(Links.matchesHostRule("e621.net.evil.example", "e621.net"))
        assertFalse(Links.matchesHostRule("note621.net", "e621.net"))
    }

    @Test
    fun normalizesUserInput() {
        assertEquals("http://192.168.1.20:8456", Links.normalizeServerUrl(" 192.168.1.20:8456/ "))
        assertEquals("https://ml.example", Links.normalizeServerUrl("https://ml.example"))
        assertNull(Links.normalizeServerUrl("   "))
        assertEquals("example.com", Links.normalizeHostRule("https://www.Example.com/path"))
        assertNull(Links.normalizeHostRule("not a host"))
    }

    @Test
    fun imageUrlsMatchMonbooruFormats() {
        assertTrue(Links.isImageUrl("https://cdn.example/a/b.JPG"))
        assertFalse(Links.isImageUrl("https://cdn.example/a/b.svg"))
    }
}
