package org.sellipi.companion.core.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    
    data object InscriptionDetail : Screen("inscription/{inscriptionId}") {
        fun createRoute(inscriptionId: String) = "inscription/$inscriptionId"
    }

    data object OverlayMode : Screen("overlay/{inscriptionId}") {
        fun createRoute(inscriptionId: String) = "overlay/$inscriptionId"
    }

    data object ArMode : Screen("ar/{inscriptionId}") {
        fun createRoute(inscriptionId: String) = "ar/$inscriptionId"
    }

    data object LetterEvolution : Screen("evolution/{letterId}") {
        fun createRoute(letterId: String = "L01") = "evolution/$letterId"
    }

    data object ResearcherCapture : Screen("researcher/{inscriptionId}") {
        fun createRoute(inscriptionId: String) = "researcher/$inscriptionId"
    }
}
