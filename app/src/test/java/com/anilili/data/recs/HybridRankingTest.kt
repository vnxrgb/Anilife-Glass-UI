package com.anilili.data.recs
import org.junit.Assert.*
import org.junit.Test
class HybridRankingTest {
    @Test fun independentAgreementOutranksSingleSource() {
        assertEquals(listOf(2,1,3), HybridRanking.rank(listOf(listOf(1,2) to 1.0, listOf(3,2) to .8), emptySet(), 3))
    }
    @Test fun excludesKnownAndIgnoresDuplicateVotes() {
        assertEquals(listOf(3), HybridRanking.rank(listOf(listOf(2,2,3,-1) to 1.0), setOf(2), 10))
    }
    @Test fun localOnlyRemainsAvailable() {
        assertEquals(listOf(7,8), HybridRanking.rank(listOf(listOf(7,8) to 1.6, emptyList<Int>() to .8), emptySet(), 5))
    }
}
