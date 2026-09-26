package org.sellipi.companion.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sellipi.companion.domain.lessons.LessonRepository
import org.sellipi.companion.domain.lessons.identificationRecoveryOf
import org.sellipi.companion.domain.model.GeoPoint
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.Site
import org.sellipi.companion.domain.repository.InscriptionRepository
import org.sellipi.companion.domain.usecase.GetNearbySitesUseCase
import org.sellipi.companion.domain.usecase.orderByProximity
import org.sellipi.companion.engine.sensor.SensorFusionEngine

data class HomeUiState(
    val sites: List<Site> = emptyList(),
    val allInscriptions: List<Inscription> = emptyList(),
    val searchQuery: String = "",
    val selectedSiteId: String? = null,
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val getNearbySitesUseCase: GetNearbySitesUseCase,
    private val inscriptionRepo: InscriptionRepository,
    private val sensorEngine: SensorFusionEngine,
    private val lessonRepo: LessonRepository
) : ViewModel() {

    private var shownAt = System.currentTimeMillis()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedSiteId = MutableStateFlow<String?>(null)
    private val _userLocation = MutableStateFlow<GeoPoint?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        getNearbySitesUseCase(_userLocation),
        inscriptionRepo.getAllInscriptions(),
        _searchQuery,
        _selectedSiteId
    ) { sites, inscriptions, query, siteId ->
        val filtered = if (query.isBlank()) {
            if (siteId == null) inscriptions else inscriptions.filter { it.siteId == siteId }
        } else {
            inscriptions.filter {
                it.nameEn.contains(query, ignoreCase = true) ||
                it.nameSi.contains(query, ignoreCase = true) ||
                it.sourceCitation.contains(query, ignoreCase = true)
            }
        }

        HomeUiState(
            sites = sites,
            allInscriptions = orderByProximity(filtered, sites),
            searchQuery = query,
            selectedSiteId = siteId,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState(isLoading = true))

    fun refreshLocation() {
        viewModelScope.launch {
            val loc = sensorEngine.getCurrentLocation()
            if (loc != null) {
                _userLocation.value = GeoPoint(loc.latitude, loc.longitude)
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onSiteSelected(siteId: String?) {
        _selectedSiteId.value = siteId
    }

    /** Call each time Home enters composition, so selection time excludes time spent elsewhere. */
    fun onScreenShown() {
        shownAt = System.currentTimeMillis()
    }

    /** No automatic identification exists yet, so every pick from this list is a manual recovery. */
    fun onInscriptionSelected(inscriptionId: String) {
        val lesson = identificationRecoveryOf(
            chosenId = inscriptionId,
            visibleIds = uiState.value.allInscriptions.map { it.id },
            searchQuery = _searchQuery.value,
            selectedSiteId = _selectedSiteId.value,
            locationAvailable = _userLocation.value != null,
            msToSelect = System.currentTimeMillis() - shownAt
        )
        lessonRepo.record(lesson, inscriptionId)
    }

    class Factory(
        private val getNearbySitesUseCase: GetNearbySitesUseCase,
        private val inscriptionRepo: InscriptionRepository,
        private val sensorEngine: SensorFusionEngine,
        private val lessonRepo: LessonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(getNearbySitesUseCase, inscriptionRepo, sensorEngine, lessonRepo) as T
        }
    }
}
