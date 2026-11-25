package cat.inslaferreria

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private const val SQUARE_SIDE_CM = 100
private const val SQUARE_SIDES = 4
private const val BATTERY_INTERVAL_MS = 10_000L
private const val MIN_BATTERY_PERCENT = 15

// El Tello retorna "ok" de seguida; aquestes pauses donen temps a acabar el moviment.
private const val TAKEOFF_STABILIZATION_MS = 5_000L
private const val FORWARD_DELAY_MS = 2_500L
private const val ROTATION_DELAY_MS = 1_500L
private const val LANDING_DELAY_MS = 5_000L

private const val EMERGENCY_LAND_TIMEOUT_MS = 15_000L

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val LOG_LOCK = Any()

private fun log(message: String) {
    synchronized(LOG_LOCK) {
        println("[${LocalTime.now().format(TIME_FORMATTER)}] $message")
    }
}

fun main() = runBlocking {
    val tello = TelloClient()
    val flying = AtomicBoolean(false)
    var batteryMonitor: Job? = null
    var exitCode = 0

    val shutdownHook = Thread({
        if (flying.get()) {
            log("Tancament detectat. Intentant aterrar...")
            runBlocking {
                withTimeoutOrNull(EMERGENCY_LAND_TIMEOUT_MS) {
                    runCatching { tello.land() }
                        .onFailure { log("No s'ha pogut aterrar en tancar: ${it.message}") }
                }
            }
        }
        tello.close()
    }, "tello-shutdown")

    Runtime.getRuntime().addShutdownHook(shutdownHook)

    try {
        log("Connectant amb el Tello a ${tello.target}...")
        tello.connect()
        log("Connectat. Mode SDK actiu.")

        val initialBattery = tello.battery()
        log("Bateria inicial: $initialBattery%")
        if (initialBattery < MIN_BATTERY_PERCENT) {
            log("Bateria per sota del $MIN_BATTERY_PERCENT%. No s'enlaira per seguretat.")
            return@runBlocking
        }

        batteryMonitor = launch(Dispatchers.Default) {
            while (isActive) {
                delay(BATTERY_INTERVAL_MS)
                runCatching { tello.battery() }
                    .onSuccess { log("Bateria: $it%") }
                    .onFailure { log("No s'ha pogut llegir la bateria: ${it.message}") }
            }
        }

        flying.set(true)
        log("Enlairant...")
        tello.takeoff()
        log("Enlairat. Estabilitzant...")
        delay(TAKEOFF_STABILIZATION_MS)

        repeat(SQUARE_SIDES) { index ->
            log("Quadrat: costat ${index + 1}/$SQUARE_SIDES...")
            tello.forward(SQUARE_SIDE_CM)
            delay(FORWARD_DELAY_MS)

            tello.rotateClockwise(90)
            delay(ROTATION_DELAY_MS)
        }
        log("Quadrat completat.")

        log("Aterrant...")
        tello.land()
        delay(LANDING_DELAY_MS)
        flying.set(false)
        log("Aterrat correctament.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Error durant el vol: ${e.message}")
        exitCode = 1

        if (flying.get()) {
            log("Intentant aterrar per seguretat...")
            withContext(NonCancellable) {
                withTimeoutOrNull(EMERGENCY_LAND_TIMEOUT_MS) {
                    runCatching { tello.land() }
                        .onSuccess {
                            delay(LANDING_DELAY_MS)
                            flying.set(false)
                            log("Aterrat per seguretat.")
                        }
                        .onFailure { log("No s'ha pogut aterrar: ${it.message}") }
                }
            }
        }
    } finally {
        batteryMonitor?.cancelAndJoin()
        tello.close()
    }

    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
