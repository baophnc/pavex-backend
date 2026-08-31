package com.pavex.operations.repository

import com.pavex.operations.entity.OperationsOutboxMessage
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OperationsOutboxRepository : JpaRepository<OperationsOutboxMessage, UUID> {
    fun countByProcessedAtIsNullAndDeadLetteredAtIsNullAndDiscardedAtIsNull(): Long
    fun countByDeadLetteredAtIsNotNullAndDiscardedAtIsNull(): Long
    fun countByDiscardedAtIsNotNull(): Long

    fun findFirstByProcessedAtIsNullAndDeadLetteredAtIsNullAndDiscardedAtIsNullOrderByOccurredAtAscIdAsc(): OperationsOutboxMessage?

    fun findAllByDeadLetteredAtIsNotNullAndDiscardedAtIsNull(pageable: Pageable): Page<OperationsOutboxMessage>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OperationsOutboxMessage m where m.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): OperationsOutboxMessage?
}
