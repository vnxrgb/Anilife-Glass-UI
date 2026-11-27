package com.anilumos.manga.engine
import org.junit.Assert.*
import org.junit.Test
class OpenComicReaderTest {
    @Test fun indexValidationAndCreditPage() {
        val episodes = OpenComicReader.parseEpisodes("""[
          {"name":"ep01_Potion-of-Flight","total_pages":4,"translated_languages":["en"]},
          {"name":"../ep02","total_pages":4,"translated_languages":["en"]},
          {"name":"ep02_No-English","total_pages":4,"translated_languages":["fr"]},
          {"name":"ep03_Bad","total_pages":999,"translated_languages":["en"]}
        ]""")
        assertEquals(1, episodes.size)
        assertEquals(5, episodes.single().pageCount)
        assertEquals("https://www.peppercarrot.com/0_sources/ep01_Potion-of-Flight/hi-res/en_Pepper-and-Carrot_by-David-Revoy_E01P04.jpg", OpenComicReader.pageUrl(episodes.single(), 4))
    }
}
