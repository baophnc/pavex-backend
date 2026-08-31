package com.pavex.operations.repository

import com.pavex.operations.entity.OperationsOutboxMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OperationsOutboxRepository : JpaRepository<OperationsOutboxMessage, UUID>
