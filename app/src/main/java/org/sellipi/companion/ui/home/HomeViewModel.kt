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
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.Site
import org.sellipi.companion.domain.repository.InscriptionRepository
import org.sellipi.companion.domain.usecase.GetNearbySitesUseCase
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
    private val sensorEngine: SensorFusionEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedSiteId = MutableStateFlow<String?>(null)
    private val _userLat = MutableStateFlow<Double?>(null)
    private val _userLon = MutableStateFlow<Double?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        getNearbySitesUseCase(_userLat.value, _userLon.value),
        inscriptionRepo.getAllInscriptions(),
        _searchQuery,
        _selectedSiteId
    ) { sites, inscriptions, query, siteId ->
        val filteredInscriptions = if (query.isBlank()) {
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
            allInscriptions = filteredInscriptions,
            searchQuery = query,
            selectedSiteId = siteId,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState(isLoading = true))

    init {
        refreshLocation()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            val loc = sensorEngine.getCurrentLocation()
            if (loc != null) {
                _userLat.value = loc.latitude
                _userLon.value = loc.longitude
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onSiteSelected(siteId: String?) {
        _selectedSiteId.value = siteId
    }

    class Factory(
        private val getNearbySitesUseCase: GetNearbySitesUseCase,
        private val inscriptionRepo: InscriptionRepository,
        private val sensorEngine: SensorFusionEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(getNearbySitesUseCase, inscriptionRepo, sensorEngine) as T
        }
    }
}
