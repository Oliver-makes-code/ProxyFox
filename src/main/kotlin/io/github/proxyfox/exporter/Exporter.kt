package io.github.proxyfox.exporter

import dev.kord.common.entity.Snowflake
import io.github.proxyfox.database
import io.github.proxyfox.fromColor
import io.github.proxyfox.importer.gson
import io.github.proxyfox.types.PkMember
import io.github.proxyfox.types.PkSystem

object Exporter {
    suspend fun export(userId: Snowflake): String {
        val system = database.getSystemByHost(userId) ?: return ""

        val pkSystem = PkSystem()
        pkSystem.name = system.name
        pkSystem.description = system.description
        pkSystem.tag = system.tag
        pkSystem.avatar_url = system.avatarUrl

        val members = database.getMembersBySystem(system.id) ?: ArrayList()
        pkSystem.members = Array(members.size) {
            val member = members[it]
            val pkMember = PkMember()
            pkMember.name = member.name
            pkMember.display_name = member.displayName
            pkMember.description = member.description
            pkMember.pronouns = member.pronouns
            pkMember.color = member.color.fromColor()
            pkMember.keep_proxy = member.keepProxy
            pkMember.message_count = member.messageCount

            // TODO: Get proxies

            pkMember
        }

        return gson.toJson(pkSystem)
    }
}