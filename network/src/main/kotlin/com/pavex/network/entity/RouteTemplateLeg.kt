package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "route_template_legs", schema = "network")
class RouteTemplateLeg(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "route_template_id", nullable = false)
    var routeTemplateId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var sequence: Int = 1,
    @Column(name = "lane_id", nullable = false)
    var laneId: UUID = UUID.randomUUID(),
)
