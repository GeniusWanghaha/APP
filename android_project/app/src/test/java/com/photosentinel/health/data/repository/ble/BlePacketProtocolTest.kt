package com.photosentinel.health.data.repository.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlePacketProtocolTest {
    @Test
    fun `decodeFrame should parse esp32 payload with valid crc`() {
        val payload = buildFramePayload()

        val frame = BleFrameDecoder.decodeFrame(payload)
        assertNotNull(frame)

        requireNotNull(frame)
        assertEquals(42, frame.frameId)
        assertEquals(1_234_567_890L, frame.baseTimestampMicros)
        assertEquals(10, frame.ecgSamples.size)
        assertEquals(16, frame.ppgRedSamples.size)
        assertEquals(16, frame.ppgIrSamples.size)
        assertEquals(0x0180, frame.stateFlags)
    }

    @Test
    fun `segment assembler should reassemble out of order packets`() {
        val payload = buildFramePayload()
        val segments = splitToSegments(payload, chunkSize = 40)
        val assembler = FrameSegmentAssembler(
            nowMillis = { 0L }
        )

        var reassembled: ByteArray? = null
        segments
            .asReversed()
            .forEach { packet ->
                val parsed = BleFrameDecoder.parseSegmentPacket(packet)
                requireNotNull(parsed)
                val full = assembler.push(parsed)
                if (full != null) {
                    reassembled = full
                }
            }

        assertNotNull(reassembled)
        assertEquals(payload.toList(), requireNotNull(reassembled).toList())
    }

    private fun buildFramePayload(): ByteArray {
        val payload = ByteBuffer.allocate(BlePacketProtocol.framePayloadBytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        payload.putShort(42.toShort())
        payload.putLong(1_234_567_890L)
        payload.put(10.toByte())
        payload.put(16.toByte())
        payload.putShort(0x0180.toShort())

        repeat(10) { index ->
            payload.putShort((900 + index * 3).toShort())
        }

        repeat(16) { index ->
            payload.put(encodeU24(52_000 + index))
        }
        repeat(16) { index ->
            payload.put(encodeU24(50_000 + index))
        }

        val body = payload.array()
        val crc = BleFrameDecoder.crc16CcittFalse(body, body.size - 2)
        payload.putShort(body.size - 2, crc.toShort())
        return payload.array()
    }

    private fun splitToSegments(framePayload: ByteArray, chunkSize: Int): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        var segmentIndex = 0
        val segmentCount = ((framePayload.size + chunkSize - 1) / chunkSize)

        while (offset < framePayload.size) {
            val len = minOf(chunkSize, framePayload.size - offset)
            val header = ByteBuffer.allocate(BlePacketProtocol.segmentHeaderBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(BlePacketProtocol.protocolVersion.toByte())
                .put(BlePacketProtocol.dataMessageType.toByte())
                .putShort(42.toShort())
                .put(segmentIndex.toByte())
                .put(segmentCount.toByte())
                .putShort(len.toShort())
                .array()

            val packet = ByteArray(header.size + len)
            System.arraycopy(header, 0, packet, 0, header.size)
            System.arraycopy(framePayload, offset, packet, header.size, len)
            chunks += packet

            offset += len
            segmentIndex += 1
        }
        return chunks
    }

    private fun encodeU24(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}
