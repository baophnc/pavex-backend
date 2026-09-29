package com.pavex.shipment.repository

import com.pavex.shipment.entity.DeliveryEvidence
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeliveryEvidenceRepository : JpaRepository<DeliveryEvidence, UUID>
