package dev.proxyfox.database.records.misc

import dev.kord.common.entity.Snowflake
import org.bson.types.ObjectId

// Created 2022-10-04T21:06:30

/**
 * @author KJP12
 * @since ${version}
 **/
class ServerSettingsRecord {
    var _id: ObjectId = ObjectId()
    var serverId: String = ""
    var proxyRole: String? = null
    var disabledChannels: List<Snowflake>? = null

    fun writeTo(other: ServerSettingsRecord) {
        other.proxyRole = proxyRole
        other.disabledChannels = disabledChannels
    }
}