package com.anilili.manga
import org.junit.Assert.*
import org.junit.Test
class MangaTitleProviderMatchTest {
    private fun title(name: String, year: Int? = null, alternatives: List<String> = emptyList()) =
        MangaTitle("fixture", name, name, "", null, year = year, alternativeTitles = alternatives)
    @Test fun exactAndAlternateNamesOnly() {
        assertTrue(titleMatches(title("Solo Leveling"), title("Solo-Leveling")))
        assertTrue(titleMatches(title("Example", alternatives = listOf("別名")), title("別名")))
        assertFalse(titleMatches(title("Example"), title("Example sequel")))
        assertFalse(titleMatches(title("Example", 2010), title("Example", 2020)))
    }
}
