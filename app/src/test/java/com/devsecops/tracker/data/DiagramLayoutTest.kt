package com.devsecops.tracker.data

import com.devsecops.tracker.ui.MdBlock
import com.devsecops.tracker.ui.bitRows
import com.devsecops.tracker.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagramLayoutTest {
    @Test
    fun bitRows_packsIntoThirtyTwoBitLines() {
        val rows = listOf(
            listOf("Src port", "16"), listOf("Dst port", "16"),
            listOf("Seq", "32"),
            listOf("Off", "4"), listOf("Rsv", "6"), listOf("Flags", "6"), listOf("Win", "16")
        )
        val lines = bitRows(rows)
        assertEquals(3, lines.size)
        assertEquals(listOf("Src port" to 16, "Dst port" to 16), lines[0])
        assertEquals(listOf("Seq" to 32), lines[1])
        assertEquals(32, lines[2].sumOf { it.second })
        assertEquals(listOf("Off" to 4, "Rsv" to 6, "Flags" to 6, "Win" to 16), lines[2])
    }

    @Test
    fun parse_newDiagramKindsBecomeDiagrams() {
        val md = "```compare TCP vs UDP\n | TCP | UDP\nConn | yes | no\n```\n" +
            "```bits Header\nA | 16\nB | 16\n```\n" +
            "```annot Output\nProtocol : TLSv1.3 | version used\n```\n"
        val kinds = parseMarkdown(md).filterIsInstance<MdBlock.Diagram>().map { it.kind }
        assertEquals(listOf("compare", "bits", "annot"), kinds)
    }

    @Test
    fun parse_pathAndFlowBecomeDiagrams() {
        val md = "```path Route\nLaptop | 192.168.1.5 | NAT\nRouter | 203.0.113.7 |\n```\n" +
            "```flow Check\n? Port open? | yes: SYN-ACK | no: RST\n> Done\n```\n"
        val d = parseMarkdown(md).filterIsInstance<MdBlock.Diagram>()
        assertEquals(listOf("path", "flow"), d.map { it.kind })
        assertEquals(2, d[0].rows.size)
        assertEquals("? Port open?", d[1].rows[0][0])
    }
}
