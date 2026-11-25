package cat.inslaferreria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

class TelloClientTest {

    @Test
    fun `executa la sequencia completa del quadrat`() = runBlocking {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val received = Collections.synchronizedList(mutableListOf<String>())

        // Servidor UDP fals que imita el Tello.
        val serverJob = launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    server.receive(packet)
                } catch (_: SocketException) {
                    break
                }

                val command = String(
                    packet.data,
                    packet.offset,
                    packet.length,
                    StandardCharsets.US_ASCII,
                ).trim()
                received.add(command)

                val body = if (command == "battery?") "87" else "ok"
                val response = body.toByteArray(StandardCharsets.US_ASCII)
                server.send(DatagramPacket(response, response.size, packet.socketAddress))
            }
        }

        val tello = TelloClient(
            host = "127.0.0.1",
            port = server.localPort,
            commandDelayMs = 0,
        )

        try {
            tello.connect()
            assertEquals(87, tello.battery())

            tello.takeoff()
            repeat(4) {
                tello.forward(100)
                tello.rotateClockwise(90)
            }
            tello.land()
        } finally {
            tello.close()
            server.close()
            serverJob.cancelAndJoin()
        }

        assertEquals(
            listOf(
                "command",
                "battery?",
                "takeoff",
                "forward 100", "cw 90",
                "forward 100", "cw 90",
                "forward 100", "cw 90",
                "forward 100", "cw 90",
                "land",
            ),
            received.toList(),
        )
    }
}
