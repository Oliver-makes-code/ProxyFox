package io.github.proxyfox.database

// Created 2022-09-04T15:17:43

/**
 * A mutable record representing a member's proxy tags.
 *
 * @author Ampflower
 **/
data class MemberProxyTagRecord(
    val systemId: String,
    val memberId: String,
    var prefix: String?,
    var suffix: String?
)
