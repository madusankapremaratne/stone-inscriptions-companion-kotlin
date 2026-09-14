package org.sellipi.companion.ui.overlay

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.sellipi.companion.R
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone900
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.domain.model.QuadPoint
import org.sellipi.companion.engine.homography.HomographyCalculator
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraOverlayScreen(
    viewModel: CameraOverlayViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLetterEvolution: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val homographyCalculator = remember { HomographyCalculator() }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // CameraX Preview
            if (!state.isFrozen) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            } catch (e: Exception) {
                                // Camera binding fallback
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Freeze Frame Simulated Slate Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Stone900),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏸ Frame Frozen for Alignment",
                        color = GoldPatina,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Interactive 4-Point Draggable Canvas Overlay
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            viewModel.onTouchScreen(offset.x, offset.y)
                        }
                    }
                    .pointerInput(Unit) {
                        var draggingCorner = -1
                        detectDragGestures(
                            onDragStart = { offset ->
                                val corners = listOf(
                                    state.quad.topLeft,
                                    state.quad.topRight,
                                    state.quad.bottomRight,
                                    state.quad.bottomLeft
                                )
                                draggingCorner = corners.indexOfFirst { pt ->
                                    hypot((pt.x - offset.x).toDouble(), (pt.y - offset.y).toDouble()) < 100.0
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggingCorner != -1) {
                                    val currentCorner = when (draggingCorner) {
                                        0 -> state.quad.topLeft
                                        1 -> state.quad.topRight
                                        2 -> state.quad.bottomRight
                                        3 -> state.quad.bottomLeft
                                        else -> QuadPoint(0f, 0f)
                                    }
                                    viewModel.updateCorner(
                                        draggingCorner,
                                        currentCorner.x + dragAmount.x,
                                        currentCorner.y + dragAmount.y
                                    )
                                }
                            },
                            onDragEnd = { draggingCorner = -1 },
                            onDragCancel = { draggingCorner = -1 }
                        )
                    }
            ) {
                val q = state.quad

                // 1. Draw Quad Polygon Boundary
                val path = Path().apply {
                    moveTo(q.topLeft.x, q.topLeft.y)
                    lineTo(q.topRight.x, q.topRight.y)
                    lineTo(q.bottomRight.x, q.bottomRight.y)
                    lineTo(q.bottomLeft.x, q.bottomLeft.y)
                    close()
                }
                drawPath(path, color = TerracottaPrimary.copy(alpha = 0.15f))
                drawPath(path, color = TerracottaPrimary, style = Stroke(width = 3.dp.toPx()))

                // 2. Draw Transformed Glyph Bounding Boxes
                val matrix = state.homographyMatrix
                for (line in state.lines) {
                    for (glyph in line.glyphs) {
                        val pTL = homographyCalculator.mapPoint(matrix, glyph.bboxX, glyph.bboxY)
                        val pTR = homographyCalculator.mapPoint(matrix, glyph.bboxX + glyph.bboxW, glyph.bboxY)
                        val pBR = homographyCalculator.mapPoint(matrix, glyph.bboxX + glyph.bboxW, glyph.bboxY + glyph.bboxH)
                        val pBL = homographyCalculator.mapPoint(matrix, glyph.bboxX, glyph.bboxY + glyph.bboxH)

                        val glyphPath = Path().apply {
                            moveTo(pTL.x, pTL.y)
                            lineTo(pTR.x, pTR.y)
                            lineTo(pBR.x, pBR.y)
                            lineTo(pBL.x, pBL.y)
                            close()
                        }
                        drawPath(glyphPath, color = GoldPatina.copy(alpha = 0.25f))
                        drawPath(glyphPath, color = GoldPatina, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }

                // 3. Draw Draggable Handles on 4 Corners
                val corners = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft)
                corners.forEachIndexed { index, corner ->
                    drawCircle(color = TerracottaPrimary, radius = 22.dp.toPx(), center = Offset(corner.x, corner.y))
                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(corner.x, corner.y))
                }
            }

            // Top Overlay Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(Stone900.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Stone900.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "Touch any letter to inspect evolution",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = GoldPatina
                    )
                }

                IconButton(
                    onClick = viewModel::toggleFreezeFrame,
                    modifier = Modifier.background(Stone900.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (state.isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Freeze",
                        tint = TerracottaPrimary
                    )
                }
            }

            // Bottom Alignment Prompt Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Stone900.copy(alpha = 0.9f))
            ) {
                Text(
                    text = stringResource(R.string.align_instruction),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }

        // Letter Evolution Modal Bottom Sheet
        if (state.selectedGlyph != null) {
            val glyph = state.selectedGlyph!!
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissGlyphSheet,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected Letter: ${glyph.letter?.modernSinhalaCodepoint ?: glyph.letterId}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TerracottaPrimary
                            )
                            Text(
                                text = "Traditional: ${glyph.letter?.letterName ?: "Brahmi Glyph"} (${glyph.letter?.romanisation ?: ""})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val letterId = glyph.letterId
                                viewModel.dismissGlyphSheet()
                                onNavigateToLetterEvolution(letterId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Full Evolution")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
