package com.devsecops.tracker.data

import com.devsecops.tracker.ui.MdBlock
import com.devsecops.tracker.ui.pairCallouts
import com.devsecops.tracker.ui.parseMarkdown
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {

    @Test
    fun parse_headingBulletNumberedAndParagraph() {
        val b = parseMarkdown("## Title\n\nHello\nworld\n\n- one\n* two\n1. first\n")
        assertEquals(MdBlock.Heading(2, "Title"), b[0])
        assertEquals(MdBlock.Para("Hello world"), b[1])
        assertEquals(MdBlock.Bullet("one", "•"), b[2])
        assertEquals(MdBlock.Bullet("two", "•"), b[3])
        assertEquals(MdBlock.Bullet("first", "1."), b[4])
    }

    @Test
    fun parse_fencedCodeKeepsPipesAndHashes() {
        val b = parseMarkdown("```\ncat a | grep b\n# not a heading\n```\nafter")
        assertEquals(MdBlock.Code("cat a | grep b\n# not a heading"), b[0])
        assertEquals(MdBlock.Para("after"), b[1])
    }

    @Test
    fun parse_tableSkipsSeparatorAndKeepsEmptyCells() {
        val b = parseMarkdown("| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | |\n")
        val t = b[0] as MdBlock.Table
        assertEquals(listOf("A", "B"), t.header)
        assertEquals(2, t.rows.size)
        assertEquals(listOf("3", ""), t.rows[1])
    }

    @Test
    fun note_jsonRoundTripAndShareText() {
        val n = NoteFull(id = 7, module_id = 1, topic_id = 2, title = "T", content_md = "body", my_words = "mine")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val back = json.decodeFromString<NoteFull>(json.encodeToString(n))
        assertEquals(n, back)
        val s = Notes.shareText(listOf(n))
        assertTrue(s.startsWith("# T"))
        assertTrue(s.contains("Mere shabdon mein"))
        assertTrue(!Notes.shareText(listOf(n.copy(my_words = null))).contains("Mere shabdon mein"))
    }
}

class ResourcesTest {
    @Test
    fun openableUrl_onlyHttpLinks() {
        assertEquals("https://x.org/a", Resources.openableUrl(" https://x.org/a "))
        assertEquals("http://x.org", Resources.openableUrl("http://x.org"))
        assertEquals(null, Resources.openableUrl(null))
        assertEquals(null, Resources.openableUrl("javascript:alert(1)"))
        assertEquals(null, Resources.openableUrl("content://x"))
    }

    @Test
    fun parse_diagramBlocksAndCallouts() {
        val md = "```stack OSI\nApplication | HTTP | data\nTransport | TCP | segment\n```\n" +
            "```seq handshake\nclient | server\nclient -> server | SYN\n```\n" +
            "```callout attacker\nARP spoofing\n```\n```callout defender\nfirewall\n```\n```bash\nls | wc\n```"
        val b = parseMarkdown(md)
        val st = b[0] as MdBlock.Diagram
        assertEquals("stack", st.kind)
        assertEquals("OSI", st.title)
        assertEquals(listOf("Transport", "TCP", "segment"), st.rows[1])
        val sq = b[1] as MdBlock.Diagram
        assertEquals(listOf("client -> server", "SYN"), sq.rows[1])
        assertEquals(MdBlock.Callout("attacker", "ARP spoofing"), b[2])
        assertEquals(MdBlock.Code("ls | wc"), b[4])
    }

    @Test
    fun pairCallouts_attackerThenDefenderShareARow() {
        val a = MdBlock.Callout("attacker", "x")
        val d = MdBlock.Callout("defender", "y")
        val p = MdBlock.Para("p")
        val g = pairCallouts(listOf(p, a, d, a))
        assertEquals(3, g.size)
        assertEquals(listOf<MdBlock>(a, d), g[1])
        assertEquals(listOf<MdBlock>(a), g[2])
    }
}
