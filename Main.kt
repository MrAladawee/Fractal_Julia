import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlin.math.sqrt
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class Scales(var width: Float = 0f, var height: Float = 0f,
             var xMin: Double = -5.0, var xMax: Double = 5.0,
             var yMin: Double = -5.0, var yMax: Double = 5.0) {

    fun rescale(cursor: Offset, scaleRatio: Double): Scales {
        val cursorX = xMin + cursor.x / width * (xMax - xMin)
        val cursorY = yMax - cursor.y / height * (yMax - yMin)

        val newWidth = (xMax - xMin) * scaleRatio
        val newHeight = (yMax - yMin) * scaleRatio

        val newXMin = cursorX - cursor.x / width * newWidth
        val newXMax = newXMin + newWidth
        val newYMax = cursorY + cursor.y / height * newHeight
        val newYMin = newYMax - newHeight

        return Scales(width, height, newXMin, newXMax, newYMin, newYMax)
    }
}

class Decart(var x: Float,var y: Float)

class Screen(var x: Float, var y: Float){

    fun scrToDec(scales: Scales):Decart{
        val x = this.x*(scales.xMax-scales.xMin)/scales.width+scales.xMin
        val y = scales.yMax - this.y*(scales.yMax-scales.yMin)/scales.height
        return Decart(x.toFloat(),y.toFloat())
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun Draw_Fractal() {
    // Изменяемое состояние для scales
    var scales by remember { mutableStateOf(Scales(0f, 0f, -5.0, 5.0, -5.0, 5.0)) }
    var scroll by remember { mutableStateOf(false) }
    var drawData by remember { mutableStateOf(listOf<Pair<Offset, Color>>()) }

    val maxIterations = 300

    // Флаг для непрорисовки недоделанного фрактала
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = maxIterations, key2 = scales) {
        isLoading = true
        drawData = Julia(300, scales)
        isLoading = false
    }

    Canvas(modifier = Modifier
        .fillMaxSize().onSizeChanged { newSize ->
            scales = Scales(newSize.width.toFloat(), newSize.height.toFloat(), scales.xMin, scales.xMax, scales.yMin, scales.yMax)
        }
        .onPointerEvent(PointerEventType.Scroll) { event ->
            val change = event.changes.first()
            scales = scales.rescale(change.position, 0.9)
            scroll = true
        })
    {
        scales.width = this.size.width
        scales.height = this.size.height

        if (!isLoading) {
            for ((offset, color) in drawData) {
                drawRect(color = color, topLeft = offset, size = Size(1f, 1f))
            }
        }
        scroll = false

    }
}

fun createColor(i: Int, maxIterations: Int): Color {
    // Сам фрактал
    return if (i == maxIterations) Color.Black
    // Внешняя зона
    else Color.hsv((i.toDouble() / maxIterations.toDouble() * 360.0).toFloat(), 0.1F, 0.7F)

}

suspend fun Julia(maxIterations: Int, scales: Scales): List<Pair<Offset, Color>> {
    return withContext(Dispatchers.Default) {

        val c = Complex(-0.549653, 0.003)

        val height = scales.height.toInt()
        val width = scales.width.toInt()

        val drawData = ConcurrentLinkedQueue<Pair<Offset, Color>>()

        // Берем все доступные потоки
        val numThreads = Runtime.getRuntime().availableProcessors()
        //print(numThreads)
        // Разбиваем по зонам относительно доступных потоков
        val threadSize = height / numThreads

        // Построение множества взято из Вики.
        val thread = List(numThreads) { threadIndex ->
            launch {

                val start_Y = threadIndex * threadSize
                val end_Y = if (threadIndex == numThreads - 1) height else start_Y + threadSize

                for (y in start_Y until end_Y) {
                    for (x in 0 until width) {

                        val screen_X = Screen(x.toFloat(), y.toFloat())
                        val cartesian_X = screen_X.scrToDec(scales)
                        var z = Complex(cartesian_X.x.toDouble(), cartesian_X.y.toDouble())
                        var i = 0

                        while (i < maxIterations && z.abs() < 2.0) {
                            z = z * z * z* z * z + c
                            i++
                        }

                        drawData.add(Pair(Offset(x.toFloat(), y.toFloat()),
                            createColor(i, maxIterations)))
                    }
                }
            }
        }
        thread.forEach { it.join() }
        drawData.toList()
    }
}

class Complex(var Re: Double, var Im: Double){

    operator fun times(c: Complex): Complex{
        val re = this.Re * c.Re - this.Im*c.Im
        val im = this.Re * c.Im + this.Im*c.Re
        return Complex(re,im)
    }

    operator fun plus(c: Complex): Complex{
        return Complex(this.Re + c.Re, this.Im + c.Im)
    }

    fun abs():Double{
        return sqrt(this.Re * this.Re + this.Im * this.Im)
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        Draw_Fractal()
    }
}
