package com.photosentinel.health.data.repository.ble

import com.photosentinel.health.domain.model.HardwareFrame
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

internal object BlePacketProtocol {
    const val protocolVersion: Int = 0x01
    const val dataMessageType: Int = 0xA1
    const val framePayloadBytes: Int = 132
    const val segmentHeaderBytes: Int = 8
    const val ecgSamplesPerFrame: Int = 10
    const val ppgSamplesPerFrame: Int = 16
    const val ecgSamplePeriodUs: Long = 4_000L
    const val ppgSamplePeriodUs: Long = 2_500L
    const val defaultPpgPhaseUs: Long = 1_250L

    val serviceUuid: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
}

internal data class SegmentPacket(
    val frameId: Int,
    val segmentIndex: Int,
    val segmentCount: Int,
    val payload: ByteArray
)

internal object BleFrameDecoder {
    fun parseSegmentPacket(packet: ByteArray): SegmentPacket? {
        if (packet.size < BlePacketProtocol.segmentHeaderBytes) {
            return null
        }

        val headerBuffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val protocol = headerBuffer.get().toInt() and 0xFF
        val messageType = headerBuffer.get().toInt() and 0xFF
        val frameId = headerBuffer.short.toInt() and 0xFFFF
        val segmentIndex = headerBuffer.get().toInt() and 0xFF
        val segmentCount = headerBuffer.get().toInt() and 0xFF
        val payloadLength = headerBuffer.short.toInt() and 0xFFFF

        if (protocol != BlePacketProtocol.protocolVersion) {
            return null
        }
        if (messageType != BlePacketProtocol.dataMessageType) {
            return null
        }
        if (segmentCount <= 0 || segmentIndex >= segmentCount) {
            return null
        }

        val payloadStart = BlePacketProtocol.segmentHeaderBytes
        if (payloadLength <= 0 || payloadStart + payloadLength > packet.size) {
            return null
        }

        return SegmentPacket(
            frameId = frameId,
            segmentIndex = segmentIndex,
            segmentCount = segmentCount,
            payload = packet.copyOfRange(payloadStart, payloadStart + payloadLength)
        )
    }

    fun decodeFrame(framePayload: ByteArray): HardwareFrame? {
        if (framePayload.size != BlePacketProtocol.framePayloadBytes) {
            return null
        }

        val crcExpected = readUint16LE(framePayload, framePayload.size - 2)
        val crcCalculated = crc16CcittFalse(framePayload, framePayload.size - 2)
        if (crcCalculated != crcExpected) {
            return null
        }

        val buffer = ByteBuffer.wrap(framePayload).order(ByteOrder.LITTLE_ENDIAN)
        val frameId = buffer.short.toInt() and 0xFFFF
        val baseTimestampMicros = buffer.long
        val ecgCount = buffer.get().toInt() and 0xFF
        val ppgCount = buffer.get().toInt() and 0xFF
        val stateFlags = buffer.short.toInt() and 0xFFFF

        val ecgRawValues = IntArray(BlePacketProtocol.ecgSamplesPerFrame) {
            buffer.short.toInt() and 0xFFFF
        }

        val redBytes = ByteArray(BlePacketProtocol.ppgSamplesPerFrame * 3)
        val irBytes = ByteArray(BlePacketProtocol.ppgSamplesPerFrame * 3)
        buffer.get(redBytes)
        buffer.get(irBytes)
        val crc = buffer.short.toInt() and 0xFFFF

        val ecgValues = ecgRawValues
            .take(ecgCount.coerceIn(0, BlePacketProtocol.ecgSamplesPerFrame))

        val redValues = decodeU24Array(redBytes)
            .take(ppgCount.coerceIn(0, BlePacketProtocol.ppgSamplesPerFrame))
        val irValues = decodeU24Array(irBytes)
            .take(ppgCount.coerceIn(0, BlePacketProtocol.ppgSamplesPerFrame))

        return HardwareFrame(
            frameId = frameId,
            baseTimestampMicros = baseTimestampMicros,
            ecgSamples = ecgValues,
            ppgRedSamples = redValues,
            ppgIrSamples = irValues,
            stateFlags = stateFlags,
            crc = crc
        )
    }

    fun crc16CcittFalse(data: ByteArray, length: Int = data.size): Int {
        var crc = 0xFFFF
        for (index in 0 until length.coerceIn(0, data.size)) {
            crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    private fun decodeU24Array(raw: ByteArray): List<Int> {
        return raw
            .asList()
            .chunked(3)
            .mapNotNull { chunk ->
                if (chunk.size < 3) {
                    null
                } else {
                    ((chunk[0].toInt() and 0xFF) shl 16) or
                        ((chunk[1].toInt() and 0xFF) shl 8) or
                        (chunk[2].toInt() and 0xFF)
                }
            }
    }

    private fun readUint16LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
}

internal class FrameSegmentAssembler(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val staleThresholdMillis: Long = 2_000L
) {
    private data class Bucket(
        val createdAtMillis: Long,
        val segmentCount: Int,
        val segments: MutableMap<Int, ByteArray>
    )

    private val buckets = mutableMapOf<Int, Bucket>()

    fun push(segment: SegmentPacket): ByteArray? {
        dropStale()
        val current = buckets[segment.frameId]
        val bucket = if (current == null || current.segmentCount != segment.segmentCount) {
            Bucket(
                createdAtMillis = nowMillis(),
                segmentCount = segment.segmentCount,
                segments = mutableMapOf()
            ).also { buckets[segment.frameId] = it }
        } else {
            current
        }

        bucket.segments[segment.segmentIndex] = segment.payload

        if (bucket.segments.size != bucket.segmentCount) {
            return null
        }

        val ordered = ArrayList<ByteArray>(bucket.segmentCount)
        for (index in 0 until bucket.segmentCount) {
            val payload = bucket.segments[index] ?: return null
            ordered += payload
        }
        buckets.remove(segment.frameId)
        return ByteArrayOutputStream().use { stream ->
            ordered.forEach(stream::write)
            stream.toByteArray()
        }
    }

    fun clear() {
        buckets.clear()
    }

    private fun dropStale() {
        val now = nowMillis()
        val expiredFrameIds = buckets
            .filterValues { now - it.createdAtMillis > staleThresholdMillis }
            .keys
        expiredFrameIds.forEach { buckets.remove(it) }
    }
}
