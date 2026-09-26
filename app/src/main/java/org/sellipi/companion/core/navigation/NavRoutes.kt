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

    /** Optional inscription context boosts cards linked to what the visitor is standing at. */
    data object Ask : Screen("ask?inscriptionId={inscriptionId}") {
        fun createRoute(inscriptionId: String? = null) =
            if (inscriptionId == null) "ask" else "ask?inscriptionId=$inscriptionId"
    }

    data object OnDeviceAi : Screen("ondevice-ai")

    /** Debug builds only. */
    data object KnowledgeEval : Screen("knowledge-eval")

    data object ResearcherCapture : Screen("researcher/{inscriptionId}") {
        fun createRoute(inscriptionId: String) = "researcher/$inscriptionId"
    }
}
