package org.sellipi.companion.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.sellipi.companion.agent.EpigraphicAgentOrchestrator
import org.sellipi.companion.agent.EpigraphicCriticAgent
import org.sellipi.companion.agent.EpigraphicIdentifyAgent
import org.sellipi.companion.agent.EpigraphicLearningAgent
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.navigation.Screen
import org.sellipi.companion.data.local.database.SellipiDatabase
import org.sellipi.companion.data.repository.EvolutionRepositoryImpl
import org.sellipi.companion.data.repository.InscriptionRepositoryImpl
import org.sellipi.companion.data.repository.ResearcherCaptureRepositoryImpl
import org.sellipi.companion.data.repository.SiteRepositoryImpl
import org.sellipi.companion.domain.usecase.GetInscriptionDetailsUseCase
import org.sellipi.companion.domain.usecase.GetLetterEvolutionUseCase
import org.sellipi.companion.domain.usecase.GetNearbySitesUseCase
import org.sellipi.companion.domain.usecase.SaveResearcherCaptureUseCase
import org.sellipi.companion.engine.ar.ArCoreSessionManager
import org.sellipi.companion.engine.sensor.SensorFusionEngine
import org.sellipi.companion.engine.slm.LocalEpigraphicSlmEngine
import org.sellipi.companion.ui.ar.ArViewModel
import org.sellipi.companion.ui.ar.ArViewScreen
import org.sellipi.companion.ui.evolution.LetterEvolutionScreen
import org.sellipi.companion.ui.evolution.LetterEvolutionViewModel
import org.sellipi.companion.ui.home.HomeScreen
import org.sellipi.companion.ui.home.HomeViewModel
import org.sellipi.companion.ui.inscription.InscriptionDetailScreen
import org.sellipi.companion.ui.inscription.InscriptionDetailViewModel
import org.sellipi.companion.ui.overlay.CameraOverlayScreen
import org.sellipi.companion.ui.overlay.CameraOverlayViewModel
import org.sellipi.companion.ui.researcher.ResearcherCaptureScreen
import org.sellipi.companion.ui.researcher.ResearcherCaptureViewModel

@Composable
fun SellipiNavHost(
    navController: NavHostController,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isSunlightMode: Boolean,
    onToggleSunlightMode: () -> Unit
) {
    val context = LocalContext.current
    val db = SellipiDatabase.getInstance(context)

    val siteRepo = SiteRepositoryImpl(db)
    val inscriptionRepo = InscriptionRepositoryImpl(db)
    val evolutionRepo = EvolutionRepositoryImpl(db)
    val captureRepo = ResearcherCaptureRepositoryImpl(db)

    val sensorEngine = SensorFusionEngine(context)
    val arSessionManager = ArCoreSessionManager(context)

    // SLM & Multi-Agent Workflow Engine
    val slmEngine = LocalEpigraphicSlmEngine()
    val identifyAgent = EpigraphicIdentifyAgent(slmEngine)
    val criticAgent = EpigraphicCriticAgent(slmEngine)
    val learningAgent = EpigraphicLearningAgent(db.learnedGlyphDao(), db.letterFormDao())
    val agentOrchestrator = EpigraphicAgentOrchestrator(identifyAgent, criticAgent, learningAgent)

    val getNearbySitesUseCase = GetNearbySitesUseCase(siteRepo)
    val getInscriptionDetailsUseCase = GetInscriptionDetailsUseCase(inscriptionRepo, evolutionRepo)
    val getLetterEvolutionUseCase = GetLetterEvolutionUseCase(evolutionRepo)
    val saveResearcherCaptureUseCase = SaveResearcherCaptureUseCase(captureRepo)

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(animationSpec = tween(250)) },
        exitTransition = { fadeOut(animationSpec = tween(250)) }
    ) {
        // 1. Home Explorer Screen
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(getNearbySitesUseCase, inscriptionRepo, sensorEngine)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToInscription = { id -> navController.navigate(Screen.InscriptionDetail.createRoute(id)) },
                onNavigateToEvolution = { navController.navigate(Screen.LetterEvolution.createRoute("L01")) },
                onNavigateToResearcher = { navController.navigate(Screen.ResearcherCapture.createRoute("INSC_ACTIVE")) },
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                isSunlightMode = isSunlightMode,
                onToggleSunlightMode = onToggleSunlightMode
            )
        }

        // 2. Inscription Detail Screen
        composable(
            route = Screen.InscriptionDetail.route,
            arguments = listOf(navArgument("inscriptionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val inscriptionId = backStackEntry.arguments?.getString("inscriptionId") ?: "INSC_ANP_01"
            val detailViewModel: InscriptionDetailViewModel = viewModel(
                factory = InscriptionDetailViewModel.Factory(inscriptionId, getInscriptionDetailsUseCase)
            )
            InscriptionDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLaunchOverlay = { id -> navController.navigate(Screen.OverlayMode.createRoute(id)) },
                onLaunchAr = { id -> navController.navigate(Screen.ArMode.createRoute(id)) },
                onNavigateToLetter = { letterId -> navController.navigate(Screen.LetterEvolution.createRoute(letterId)) },
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                isSunlightMode = isSunlightMode,
                onToggleSunlightMode = onToggleSunlightMode
            )
        }

        // 3. CameraX 4-Point Overlay Alignment Screen (With Agentic Identification)
        composable(
            route = Screen.OverlayMode.route,
            arguments = listOf(navArgument("inscriptionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val inscriptionId = backStackEntry.arguments?.getString("inscriptionId") ?: "INSC_ANP_01"
            val overlayViewModel: CameraOverlayViewModel = viewModel(
                factory = CameraOverlayViewModel.Factory(inscriptionId, getInscriptionDetailsUseCase, agentOrchestrator)
            )
            CameraOverlayScreen(
                viewModel = overlayViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLetterEvolution = { letterId ->
                    navController.navigate(Screen.LetterEvolution.createRoute(letterId))
                }
            )
        }

        // 4. ARCore 3D View Screen
        composable(
            route = Screen.ArMode.route,
            arguments = listOf(navArgument("inscriptionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val inscriptionId = backStackEntry.arguments?.getString("inscriptionId") ?: "INSC_ANP_01"
            val arViewModel: ArViewModel = viewModel(
                factory = ArViewModel.Factory(inscriptionId, getInscriptionDetailsUseCase, arSessionManager)
            )
            ArViewScreen(
                viewModel = arViewModel,
                onNavigateBack = { navController.popBackStack() },
                onFallbackToOverlay = { id ->
                    navController.popBackStack()
                    navController.navigate(Screen.OverlayMode.createRoute(id))
                }
            )
        }

        // 5. Letter Evolution Screen
        composable(
            route = Screen.LetterEvolution.route,
            arguments = listOf(navArgument("letterId") { type = NavType.StringType; defaultValue = "L01" })
        ) { backStackEntry ->
            val letterId = backStackEntry.arguments?.getString("letterId") ?: "L01"
            val evolutionViewModel: LetterEvolutionViewModel = viewModel(
                factory = LetterEvolutionViewModel.Factory(letterId, evolutionRepo, getLetterEvolutionUseCase)
            )
            LetterEvolutionScreen(
                viewModel = evolutionViewModel,
                onNavigateBack = { navController.popBackStack() },
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                isSunlightMode = isSunlightMode,
                onToggleSunlightMode = onToggleSunlightMode
            )
        }

        // 6. Researcher Field Capture Screen
        composable(
            route = Screen.ResearcherCapture.route,
            arguments = listOf(navArgument("inscriptionId") { type = NavType.StringType; defaultValue = "INSC_ACTIVE" })
        ) { backStackEntry ->
            val inscriptionId = backStackEntry.arguments?.getString("inscriptionId") ?: "INSC_ACTIVE"
            val researcherViewModel: ResearcherCaptureViewModel = viewModel(
                factory = ResearcherCaptureViewModel.Factory(inscriptionId, saveResearcherCaptureUseCase, sensorEngine)
            )
            ResearcherCaptureScreen(
                viewModel = researcherViewModel,
                onNavigateBack = { navController.popBackStack() },
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                isSunlightMode = isSunlightMode,
                onToggleSunlightMode = onToggleSunlightMode
            )
        }
    }
}
