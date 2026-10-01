package com.pavex.storage

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.errors.ErrorResponseException
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Files kept outside the database (photos, attachments, avatars), one bucket per kind.
 * Small files only: whole files are read into memory.
 */
interface ObjectStorage {
    fun put(bucket: String, key: String, bytes: ByteArray, contentType: String)

    /** The stored bytes, or null when no such object exists. */
    fun get(bucket: String, key: String): ByteArray?

    fun delete(bucket: String, key: String)
}

/** MinIO, or any S3-compatible store. A missing bucket is created on the first write to it. */
class MinioObjectStorage(endpoint: String, accessKey: String, secretKey: String) : ObjectStorage {
    private val client = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()
    private val readyBuckets = ConcurrentHashMap.newKeySet<String>()

    override fun put(bucket: String, key: String, bytes: ByteArray, contentType: String) {
        ensureBucket(bucket)
        client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build(),
        )
    }

    override fun get(bucket: String, key: String): ByteArray? = try {
        client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(key).build()).use { it.readAllBytes() }
    } catch (exception: ErrorResponseException) {
        if (exception.errorResponse().code() in MISSING_CODES) null else throw exception
    }

    override fun delete(bucket: String, key: String) {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
    }

    private fun ensureBucket(bucket: String) {
        if (bucket in readyBuckets) return
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
        readyBuckets += bucket
    }

    private companion object {
        val MISSING_CODES = setOf("NoSuchKey", "NoSuchBucket", "NoSuchObject")
    }
}

/** Object keys grouped by kind and month, e.g. "evidence/2026/10/1b4e…". */
object ObjectKeys {
    fun dated(kind: String, id: UUID, at: Instant): String {
        val date = at.atOffset(ZoneOffset.UTC)
        return "$kind/${date.year}/${date.monthValue.toString().padStart(2, '0')}/$id"
    }
}
