package com.wiwy.wiwytransfer

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.material3.Text
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---- Paleta de la marca ----
private val Cyan = Color(0xFF5BD0FF)
private val Blue = Color(0xFF2979FF)
private val DeepBlue = Color(0xFF1565C0)
private val DarkA = Color(0xFF05080F)
private val DarkB = Color(0xFF0A1426)

// ---- Geometría del avión (normalizada 0..1 dentro de su caja) ----
private val PLANE_TIP = Offset(0.86f, 0.16f)
private val PLANE_LEFT = Offset(0.10f, 0.52f)
private val PLANE_NOTCH = Offset(0.48f, 0.56f)
private val PLANE_BOTTOM = Offset(0.42f, 0.90f)

/**
 * Animación de inicio premium (~2.5 s) hecha 100% con Compose + Canvas + Animatable.
 * Llama a [onFinish] al terminar para mostrar la pantalla principal.
 */
@Composable
fun WiwySplash(onFinish: () -> Unit) {
    // Reloj maestro 0..1 que recorre toda la secuencia (no bloquea el hilo principal).
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(durationMillis = 2500, easing = LinearEasing))
        onFinish()
    }

    // Rotación continua de los anillos (independiente del reloj maestro).
    val spin = rememberInfiniteTransition(label = "spin")
    val ringA by spin.animateFloat(
        0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "ringA",
    )
    val ringB by spin.animateFloat(
        360f, 0f, infiniteRepeatable(tween(7600, easing = LinearEasing), RepeatMode.Restart),
        label = "ringB",
    )

    val density = LocalDensity.current

    // Transición de salida (fade + scale) aplicada a todo.
    val outP = phase(t.value, 0.95f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - outP
                val s = 1f + 0.06f * outP
                scaleX = s; scaleY = s
            }
            .background(
                Brush.radialGradient(listOf(DarkB, DarkA, Color.Black), radius = 1400f)
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val center = Offset(wPx / 2f, hPx * 0.40f)
            val side = min(wPx, hPx) * 0.40f
            val box = Rect(center.x - side / 2f, center.y - side / 2f, center.x + side / 2f, center.y + side / 2f)

            // Partículas generadas una sola vez por tamaño (memoria mínima ~140 objetos).
            val particles = remember(wPx, hPx) { buildParticles(center, side, box) }

            // Fases de la secuencia
            val tv = t.value
            val seed = phase(tv, 0f, 0.12f)
            val converge = phase(tv, 0.05f, 0.55f)
            val ringsAlpha = phase(tv, 0.18f, 0.42f)
            val planeForm = phase(tv, 0.40f, 0.70f)
            val bounceP = phase(tv, 0.62f, 0.80f)
            val trailP = phase(tv, 0.66f, 0.86f)
            val shineP = phase(tv, 0.80f, 0.93f)

            val planeAlpha = easeOutCubic(planeForm)
            val brightness = planeForm
            val scale = 0.95f + 0.05f * easeOutBack(bounceP)
            val thrust = side * 0.06f * impulse(bounceP)

            // Capa de bloom (desenfoque real con RenderEffect en API 31+; si no, glow dibujado).
            Canvas(Modifier.fillMaxSize().bloom()) {
                drawRings(center, side, ringA, ringB, ringsAlpha * 0.9f)
                drawPlane(box, planeAlpha * 0.9f, scale, thrust, brightness, glow = true)
                drawSeed(center, seed, side)
            }

            // Capa nítida: partículas + anillos + avión + estela + destello.
            Canvas(Modifier.fillMaxSize()) {
                drawSeed(center, seed, side)
                drawParticles(particles, converge, tv, 1f - planeForm * 0.5f)
                drawRings(center, side, ringA, ringB, ringsAlpha)
                drawTrail(box, trailP, thrust)
                drawPlane(box, planeAlpha, scale, thrust, brightness, glow = false)
                drawShine(box, scale, thrust, shineP)
            }
        }

        // Texto con fade de abajo hacia arriba.
        val textP = phase(t.value, 0.70f, 0.90f)
        val textEase = easeOutCubic(textP)
        Column(
            Modifier
                .align(BiasAlignment(0f, 0.46f))
                .graphicsLayer {
                    alpha = textP
                    translationY = (1f - textEase) * with(density) { 30.dp.toPx() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                Text("Wiwy", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Transfer", color = Blue, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "COMPARTE SIN LÍMITES",
                color = Color(0xFF7FA8D9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.35.em,
            )
        }
    }
}

// ---------- Modifiers de apoyo ----------

/** Desenfoque real (bloom) cuando está disponible (API 31+). En APIs previas, sin coste extra. */
private fun Modifier.bloom(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.then(
            Modifier.graphicsLayer {
                renderEffect = android.graphics.RenderEffect
                    .createBlurEffect(26f, 26f, android.graphics.Shader.TileMode.DECAL)
                    .asComposeRenderEffect()
            }
        )
    } else this

// ---------- Dibujo (DrawScope) ----------

/** Semilla: punto de luz central que crece al inicio. */
private fun DrawScope.drawSeed(center: Offset, p: Float, side: Float) {
    if (p <= 0f || p >= 1f) return
    val r = side * (0.04f + 0.10f * p)
    val a = (1f - p) * 0.9f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = a), Cyan.copy(alpha = a * 0.6f), Color.Transparent),
            center = center, radius = r,
        ),
        radius = r, center = center, blendMode = BlendMode.Plus,
    )
}

/** Partículas atraídas hacia el centro formando el avión (energía digital). */
private fun DrawScope.drawParticles(particles: List<Particle>, converge: Float, time: Float, presence: Float) {
    if (converge <= 0f) return
    for (p in particles) {
        val local = easeOutCubic((converge * (0.55f + 0.9f * p.speed)).coerceIn(0f, 1f))
        val pos = lerp(p.start, p.target, local)
        val twinkle = 0.65f + 0.35f * sin(time * p.twinkle * 40f + p.phase)
        val a = p.alpha * (0.25f + 0.75f * local) * twinkle * presence
        if (a <= 0.01f) continue
        val rad = p.size * (1.3f - 0.5f * local)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(p.color.copy(alpha = a), p.color.copy(alpha = 0f)),
                center = pos, radius = rad * 2.2f,
            ),
            radius = rad * 2.2f, center = pos, blendMode = BlendMode.Plus,
        )
    }
}

/** Dos arcos luminosos (no círculos completos) girando a distinta velocidad. */
private fun DrawScope.drawRings(center: Offset, side: Float, angA: Float, angB: Float, alpha: Float) {
    if (alpha <= 0f) return
    val rA = side * 0.78f
    val rB = side * 0.62f
    drawGlowArc(center, rA, startAngle = angA, sweep = 210f, color = Blue, alpha = alpha, width = side * 0.018f)
    drawGlowArc(center, rB, startAngle = angB, sweep = 140f, color = Cyan, alpha = alpha * 0.9f, width = side * 0.014f)
}

private fun DrawScope.drawGlowArc(center: Offset, radius: Float, startAngle: Float, sweep: Float, color: Color, alpha: Float, width: Float) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val size = Size(radius * 2, radius * 2)
    // varias pasadas = glow aditivo (también se ve sin RenderEffect)
    drawArc(color = color.copy(alpha = alpha * 0.18f), startAngle = startAngle, sweepAngle = sweep,
        useCenter = false, topLeft = topLeft, size = size, style = Stroke(width = width * 3.5f), blendMode = BlendMode.Plus)
    drawArc(color = color.copy(alpha = alpha * 0.9f), startAngle = startAngle, sweepAngle = sweep,
        useCenter = false, topLeft = topLeft, size = size, style = Stroke(width = width), blendMode = BlendMode.Plus)
}

/** Estela luminosa detrás del avión (al impulsarse). */
private fun DrawScope.drawTrail(box: Rect, p: Float, thrust: Float) {
    if (p <= 0f) return
    val a = p * (1f - p) * 4f  // pico a mitad
    if (a <= 0.01f) return
    val from = planePoint(box, PLANE_LEFT)
    val tip = planePoint(box, PLANE_TIP)
    // dirección opuesta al morro
    val dir = (from - tip)
    val len = box.width * 0.7f
    val end = from + Offset(dir.x, dir.y).normalizedTimes(len)
    drawLine(
        brush = Brush.linearGradient(
            listOf(Cyan.copy(alpha = a * 0.7f), Color.Transparent),
            start = from, end = end,
        ),
        start = from, end = end, strokeWidth = box.height * 0.10f, blendMode = BlendMode.Plus,
    )
}

/** El avión: dos facetas con degradado (efecto papel plegado) y brillo creciente. */
private fun DrawScope.drawPlane(box: Rect, alpha: Float, scale: Float, thrust: Float, brightness: Float, glow: Boolean) {
    if (alpha <= 0f) return
    val pivot = box.center
    // empuje hacia adelante (dirección del morro)
    val tip = planePoint(box, PLANE_TIP)
    val dir = (tip - pivot).normalizedTimes(thrust)
    transformed(pivot, scale, dir) {
        val top = facetPath(box, listOf(PLANE_TIP, PLANE_LEFT, PLANE_NOTCH))
        val bottom = facetPath(box, listOf(PLANE_TIP, PLANE_NOTCH, PLANE_BOTTOM))
        val b = 0.5f + 0.5f * brightness
        drawPath(
            top,
            Brush.linearGradient(
                listOf(Cyan.copy(alpha = alpha), Blue.copy(alpha = alpha)),
                start = planePoint(box, PLANE_LEFT), end = tip,
            ),
            blendMode = if (glow) BlendMode.Plus else BlendMode.SrcOver,
        )
        drawPath(
            bottom,
            Brush.linearGradient(
                listOf(Blue.copy(alpha = alpha), DeepBlue.copy(alpha = alpha)),
                start = planePoint(box, PLANE_NOTCH), end = planePoint(box, PLANE_BOTTOM),
            ),
            blendMode = if (glow) BlendMode.Plus else BlendMode.SrcOver,
        )
        // realce del pliegue central
        if (!glow) {
            drawLine(
                Color.White.copy(alpha = alpha * 0.5f * b),
                start = tip, end = planePoint(box, PLANE_NOTCH),
                strokeWidth = box.width * 0.012f, blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Destello tipo reflejo metálico que cruza el logo de izquierda a derecha. */
private fun DrawScope.drawShine(box: Rect, scale: Float, thrust: Float, p: Float) {
    if (p <= 0f || p >= 1f) return
    val pivot = box.center
    val dir = (planePoint(box, PLANE_TIP) - pivot).normalizedTimes(thrust)
    transformed(pivot, scale, dir) {
        val clip = facetPath(box, listOf(PLANE_TIP, PLANE_LEFT, PLANE_NOTCH, PLANE_BOTTOM))
        clipPath(clip) {
            val w = box.width
            val x = box.left - w * 0.4f + p * (w * 1.8f)
            val bandW = w * 0.28f
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.75f),
                    1f to Color.Transparent,
                    startX = x - bandW, endX = x + bandW,
                ),
                topLeft = Offset(box.left - w * 0.5f, box.top - box.height * 0.5f),
                size = Size(w * 2f, box.height * 2f),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

// ---------- Helpers ----------

private class Particle(
    val start: Offset,
    val target: Offset,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val color: Color,
    val twinkle: Float,
    val phase: Float,
)

private fun buildParticles(center: Offset, side: Float, box: Rect): List<Particle> {
    val rnd = Random(42)
    val targets = sampleInsidePlane(box, 140, rnd)
    return targets.map { tgt ->
        val ang = rnd.nextFloat() * (2 * Math.PI).toFloat()
        val dist = side * (0.9f + rnd.nextFloat() * 1.4f)
        val start = center + Offset(cos(ang) * dist, sin(ang) * dist)
        Particle(
            start = start,
            target = tgt,
            size = side * (0.006f + rnd.nextFloat() * 0.014f),
            speed = rnd.nextFloat(),
            alpha = 0.5f + rnd.nextFloat() * 0.5f,
            color = if (rnd.nextFloat() < 0.5f) Cyan else Blue,
            twinkle = 0.5f + rnd.nextFloat(),
            phase = rnd.nextFloat() * 6.28f,
        )
    }
}

/** Muestrea puntos dentro de la silueta del avión (para que las partículas lo formen). */
private fun sampleInsidePlane(box: Rect, count: Int, rnd: Random): List<Offset> {
    val pts = listOf(PLANE_TIP, PLANE_LEFT, PLANE_NOTCH, PLANE_BOTTOM).map { planePoint(box, it) }
    val path = android.graphics.Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
    val bounds = android.graphics.RectF()
    path.computeBounds(bounds, true)
    val region = android.graphics.Region().apply {
        setPath(path, android.graphics.Region(
            bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
    }
    val out = ArrayList<Offset>(count)
    var tries = 0
    while (out.size < count && tries < count * 40) {
        tries++
        val x = bounds.left + rnd.nextFloat() * bounds.width()
        val y = bounds.top + rnd.nextFloat() * bounds.height()
        if (region.contains(x.toInt(), y.toInt())) out.add(Offset(x, y))
    }
    // por si la región fuese degenerada
    while (out.size < count) out.add(box.center)
    return out
}

private fun planePoint(box: Rect, n: Offset) =
    Offset(box.left + n.x * box.width, box.top + n.y * box.height)

private fun facetPath(box: Rect, pts: List<Offset>): Path =
    Path().apply {
        val p0 = planePoint(box, pts[0]); moveTo(p0.x, p0.y)
        for (i in 1 until pts.size) { val p = planePoint(box, pts[i]); lineTo(p.x, p.y) }
        close()
    }

private inline fun DrawScope.transformed(pivot: Offset, scale: Float, move: Offset, crossinline block: DrawScope.() -> Unit) {
    withTransform({
        translate(move.x, move.y)
        scale(scale, scale, pivot)
    }) { block() }
}

private fun phase(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOutCubic(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    return 1f + c3 * (x - 1f) * (x - 1f) * (x - 1f) + c1 * (x - 1f) * (x - 1f)
}
/** Pico suave en la mitad (0→1→0) para el impulso. */
private fun impulse(x: Float) = (4f * x * (1f - x)).coerceIn(0f, 1f)

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
private fun Offset.normalizedTimes(len: Float): Offset {
    val m = kotlin.math.hypot(x, y)
    return if (m < 0.0001f) Offset.Zero else Offset(x / m * len, y / m * len)
}
