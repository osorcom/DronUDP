package cat.inslaferreria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class TelloException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Client mínim del SDK del DJI Tello.
 *
 * Totes les comandes es serialitzen perquè el dron només processa una comanda
 * alhora i les respostes UDP no es barregin.
 */
class TelloClient(
    host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val commandDelayMs: Long = DEFAULT_COMMAND_DELAY_MS,
) : Closeable {

    private val address: InetAddress = InetAddress.getByName(host)
    private val socket = DatagramSocket()
    private val commandMutex = Mutex()

    val target: String = "$host:$port"

    /** Entra en mode SDK. Cal fer-ho abans de qualsevol altra comanda. */
    suspend fun connect() {
        expectOk("command", sendCommand("command", RESPONSE_TIMEOUT_MS))
    }

    /** Retorna el percentatge de bateria (0-100). */
    suspend fun battery(): Int {
        val response = sendCommand("battery?", RESPONSE_TIMEOUT_MS)
        return response.toIntOrNull()
            ?: throw TelloException("Resposta de bateria no vàlida: '$response'")
    }

    suspend fun takeoff() {
        expectOk("takeoff", sendCommand("takeoff", TAKEOFF_LAND_TIMEOUT_MS))
    }

    suspend fun land() {
        expectOk("land", sendCommand("land", TAKEOFF_LAND_TIMEOUT_MS))
    }

    /** Avança [centimeters] cm (entre 20 i 500). */
    suspend fun forward(centimeters: Int) {
        require(centimeters in 20..500) { "La distància ha d'estar entre 20 i 500 cm" }
        expectOk("forward", sendCommand("forward $centimeters", RESPONSE_TIMEOUT_MS))
    }

    /** Gira [degrees] graus en sentit horari (entre 1 i 360). */
    suspend fun rotateClockwise(degrees: Int) {
        require(degrees in 1..360) { "Els graus han d'estar entre 1 i 360" }
        expectOk("cw", sendCommand("cw $degrees", RESPONSE_TIMEOUT_MS))
    }

    private fun expectOk(command: String, response: String) {
        if (!response.equals("ok", ignoreCase = true)) {
            throw TelloException("Resposta inesperada a '$command': '$response'")
        }
    }

    private suspend fun sendCommand(command: String, timeoutMs: Int): String =
        commandMutex.withLock {
            val response = withContext(Dispatchers.IO) {
                val payload = command.toByteArray(StandardCharsets.US_ASCII)
                socket.soTimeout = timeoutMs
                socket.send(DatagramPacket(payload, payload.size, address, port))

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    throw TelloException(
                        "Temps d'espera exhaurit (${timeoutMs} ms) esperant resposta a '$command'",
                        e,
                    )
                }

                String(packet.data, packet.offset, packet.length, StandardCharsets.US_ASCII).trim()
            }

            if (response.startsWith("error", ignoreCase = true)) {
                throw TelloException("El dron ha retornat un error a '$command': '$response'")
            }

            if (commandDelayMs > 0) {
                delay(commandDelayMs)
            }
            response
        }

    override fun close() {
        socket.close()
    }

    companion object {
        const val DEFAULT_HOST = "192.168.10.1"
        const val DEFAULT_PORT = 8889
        private const val DEFAULT_COMMAND_DELAY_MS = 500L
        private const val RESPONSE_TIMEOUT_MS = 5_000
        private const val TAKEOFF_LAND_TIMEOUT_MS = 20_000
    }
}
