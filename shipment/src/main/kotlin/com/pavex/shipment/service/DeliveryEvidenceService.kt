package com.pavex.shipment.service

import com.pavex.common.security.PavexActor
import com.pavex.shipment.dto.DeliveryEvidenceResponse
import com.pavex.shipment.entity.DeliveryEvidence
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.DeliveryEvidenceRepository
import com.pavex.storage.ObjectKeys
import com.pavex.storage.ObjectStorage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/** Stores proof-of-delivery images; a delivery attempt then cites "evidence:<id>". */
@Service
class DeliveryEvidenceService(
    private val evidence: DeliveryEvidenceRepository,
    private val storage: ObjectStorage,
    @Value("\${pavex.storage.evidence-bucket}") private val bucket: String,
) {
    @Transactional
    fun upload(actor: PavexActor, contentType: String?, bytes: ByteArray): DeliveryEvidenceResponse {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type !in ALLOWED_TYPES) {
            throw InvalidShipmentRequestException("invalid_evidence_type", "Chỉ nhận ảnh JPG, PNG hoặc WEBP.")
        }
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) {
            throw InvalidShipmentRequestException("invalid_evidence_size", "Ảnh bằng chứng phải nhỏ hơn 3 MB.")
        }
        val id = UUID.randomUUID()
        val now = Instant.now()
        val key = ObjectKeys.dated("evidence", id, now)
        // The photo is stored first, so a saved record always points at a stored photo.
        storage.put(bucket, key, bytes, type!!)
        val saved = try {
            evidence.save(
                DeliveryEvidence(
                    id = id,
                    uploadedByUserId = actor.userId,
                    contentType = type,
                    sizeBytes = bytes.size,
                    sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    objectKey = key,
                    createdAt = now,
                ),
            )
        } catch (exception: RuntimeException) {
            runCatching { storage.delete(bucket, key) }
            throw exception
        }
        return DeliveryEvidenceResponse("$REFERENCE_PREFIX${saved.id}", saved.sizeBytes)
    }

    /** The uploader, or anyone reviewing shipments, may view a proof image. */
    @Transactional(readOnly = true)
    fun read(actor: PavexActor, id: UUID): EvidenceImage {
        val item = evidence.findById(id).orElseThrow(::notFound)
        val reviewer = actor.hasAuthority(WorkforceViewService.ADMIN_READ) || actor.hasAuthority(WorkforceViewService.HUB_READ)
        if (item.uploadedByUserId != actor.userId && !reviewer) throw notFound()
        val bytes = item.objectKey?.let { storage.get(bucket, it) } ?: item.data ?: throw notFound()
        return EvidenceImage(item.contentType, bytes)
    }

    private fun notFound() = ShipmentNotFoundException("evidence_not_found", "Không tìm thấy ảnh bằng chứng.")

    companion object {
        const val REFERENCE_PREFIX = "evidence:"
        private const val MAX_BYTES = 3 * 1024 * 1024
        private val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

data class EvidenceImage(
    val contentType: String,
    val bytes: ByteArray,
)
