package ai.tamacape
import org.junit.Assert.*
import org.junit.Test
class SongTimelineTest {
    @Test fun timelineRespectsVerseChorusAndEnd() {
        val lyrics = "가\n나\n\n다\n라"
        assertEquals(0, estimatedLyricLine(lyrics, 0, 30000))
        assertEquals(1, estimatedLyricLine(lyrics, 14999, 30000))
        assertEquals(2, estimatedLyricLine(lyrics, 15000, 30000))
        assertEquals(3, estimatedLyricLine(lyrics, 29999, 30000))
        assertEquals(-1, estimatedLyricLine(lyrics, 30000, 30000))
        assertEquals(-1, estimatedLyricLine("", 0, 30000))
        assertEquals("0:00", songTime(-1))
        assertEquals("1:05", songTime(65000))
    }
}
