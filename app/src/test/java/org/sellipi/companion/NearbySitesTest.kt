package org.sellipi.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sellipi.companion.domain.model.GeoPoint
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.Site
import org.sellipi.companion.domain.repository.SiteRepository
import org.sellipi.companion.domain.usecase.GetNearbySitesUseCase
import org.sellipi.companion.domain.usecase.haversineMeters
import org.sellipi.companion.domain.usecase.orderByProximity

class NearbySitesTest {

    private val anuradhapura = site("ANP", 8.3114, 80.4037)
    private val mihintale = site("MHT", 8.3508, 80.5097)

    private val repo = object : SiteRepository {
        override fun getSites() = flowOf(listOf(anuradhapura, mihintale))
        override suspend fun getSiteById(siteId: String) = null
    }

    @Test
    fun haversineMatchesKnownDistance() {
        // Anuradhapura to Mihintale is roughly 12.5 km as the crow flies.
        val d = haversineMeters(8.3114, 80.4037, 8.3508, 80.5097)
        assertEquals(12_460.0, d, 150.0)
    }

    @Test
    fun sitesStayUnsortedWithoutLocation() = runTest {
        val sites = GetNearbySitesUseCase(repo)(flowOf(null)).first()
        assertEquals(listOf("ANP", "MHT"), sites.map { it.id })
        assertNull(sites.first().distanceFromUserM)
    }

    @Test
    fun sitesResortWhenLocationArrivesLater() = runTest {
        val location = MutableStateFlow<GeoPoint?>(null)
        val flow = GetNearbySitesUseCase(repo)(location)

        assertEquals("ANP", flow.first().first().id)

        location.value = GeoPoint(8.3500, 80.5100) // standing at Mihintale
        val sorted = flow.first()
        assertEquals(listOf("MHT", "ANP"), sorted.map { it.id })
        assertEquals(0f, sorted.first().distanceFromUserM!!, 200f)
    }

    @Test
    fun inscriptionsFollowSiteDistanceAndKeepDateOrderWithinSite() {
        val sites = listOf(mihintale.copy(distanceFromUserM = 50f), anuradhapura.copy(distanceFromUserM = 12_000f))
        val chronological = listOf(
            inscription("A1", "ANP"), inscription("M1", "MHT"),
            inscription("X1", "UNKNOWN"), inscription("M2", "MHT"), inscription("A2", "ANP")
        )

        val ordered = orderByProximity(chronological, sites)

        assertEquals(listOf("M1", "M2", "A1", "A2", "X1"), ordered.map { it.id })
    }

    @Test
    fun inscriptionOrderUnchangedWithoutDistances() {
        val chronological = listOf(inscription("A1", "ANP"), inscription("M1", "MHT"))
        assertEquals(chronological, orderByProximity(chronological, listOf(anuradhapura, mihintale)))
    }

    private fun site(id: String, lat: Double, lon: Double) =
        Site(id, id, id, id, lat, lon, geofenceRadiusM = 200f, permitReference = "")

    private fun inscription(id: String, siteId: String) = Inscription(
        id = id, siteId = siteId, nameSi = id, nameTa = id, nameEn = id,
        dateRangeStart = -200, dateRangeEnd = -100, datingBasis = "", primaryPeriodId = "P01",
        referencePhoto = null, arcoreTargetScore = 0, alignmentStrategy = "overlay", sourceCitation = ""
    )
}
