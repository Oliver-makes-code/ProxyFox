/*
 * Copyright (c) 2022-2023, The ProxyFox Group
 *
 * This Source Code is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.proxyfox.bot.command

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.builder.interaction.SubCommandBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.proxyfox.bot.command.context.DiscordContext
import dev.proxyfox.bot.command.context.InteractionCommandContext
import dev.proxyfox.bot.command.context.guild
import dev.proxyfox.bot.command.context.runs
import dev.proxyfox.bot.command.node.attachment
import dev.proxyfox.bot.kord
import dev.proxyfox.bot.kordColor
import dev.proxyfox.bot.member
import dev.proxyfox.bot.prompts.Button
import dev.proxyfox.bot.prompts.TimedYesNoPrompt
import dev.proxyfox.command.NodeHolder
import dev.proxyfox.command.node.CommandNode
import dev.proxyfox.command.node.builtin.greedy
import dev.proxyfox.command.node.builtin.literal
import dev.proxyfox.command.node.builtin.string
import dev.proxyfox.command.node.builtin.unixLiteral
import dev.proxyfox.common.*
import dev.proxyfox.database.database
import dev.proxyfox.database.displayDate
import dev.proxyfox.database.records.member.MemberProxyTagRecord
import dev.proxyfox.database.records.member.MemberRecord
import dev.proxyfox.database.records.member.MemberServerSettingsRecord
import dev.proxyfox.database.records.system.SystemRecord
import dev.proxyfox.database.tryParseLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * Commands for accessing and changing system  settings
 * @author Oliver
 * */
object MemberCommands {
    var interactionExecutors: HashMap<String, suspend InteractionCommandContext.() -> Boolean> = hashMapOf()

    fun SubCommandBuilder.runs(action: suspend InteractionCommandContext.() -> Boolean) {
        interactionExecutors[name] = action
    }

    suspend fun Kord.registerMemberCommands() {
        createGlobalChatInputCommand("member", "Manage or create a system member!") {
            subCommand("create", "Create a member") {
                name()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val name = value.interaction.command.strings["name"]!!

                    create(this, system!!, name)
                }
            }
            subCommand("delete", "Delete a member") {
                member()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false

                    delete(this, system, member!!)
                }
            }
            subCommand("fetch", "Fetches the member's card") {
                member()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false

                    access(this, system, member!!)
                }
            }
            access("member", "name") {
                member()
                name(required = false)
                raw()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val name = value.interaction.command.strings["name"]
                    val raw = value.interaction.command.booleans["raw"] ?: false

                    rename(this, system, member!!, name, raw)
                }
            }
            access("member", "nickname") {
                member()
                name(required = false)
                raw()
                clear()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val name = value.interaction.command.strings["name"]
                    val raw = value.interaction.command.booleans["raw"] ?: false
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    nickname(this, system, member!!, name, raw, clear)
                }
            }
            access("member", "servernick") {
                member()
                name()
                guild()
                raw()
                clear()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val guildId =
                        value.interaction.command.integers["server"]?.toULong()?.let { Snowflake(it) } ?: getGuild()?.id
                    guildId ?: run {
                        respondFailure("Command not ran in server.")
                        return@runs false
                    }
                    val guild = kord.getGuildOrNull(guildId) ?: run {
                        respondFailure("Cannot find server. Am I in it?")
                        return@runs false
                    }
                    val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                    val name = value.interaction.command.strings["name"]
                    val raw = value.interaction.command.booleans["raw"] ?: false
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    servername(this, system, serverMember!!, name, raw, clear)
                }
            }
            access("member", "description") {
                member()
                raw()
                clear()
                name("description", required = false)
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val desc = value.interaction.command.strings["description"]
                    val raw = value.interaction.command.booleans["raw"] ?: false
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    description(this, system, member!!, desc, raw, clear)
                }
            }
            access("member", "avatar") {
                member()
                avatar()
                clear()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val avatar = value.interaction.command.attachments["avatar"]?.data?.url
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    avatar(this, system, member!!, avatar, clear)
                }
            }
            access("member", "serveravatar") {
                member()
                avatar()
                guild()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val guildId =
                        value.interaction.command.integers["server"]?.toULong()?.let { Snowflake(it) } ?: getGuild()?.id
                    guildId ?: run {
                        respondFailure("Command not ran in server.")
                        return@runs false
                    }
                    val guild = kord.getGuildOrNull(guildId) ?: run {
                        respondFailure("Cannot find server. Am I in it?")
                        return@runs false
                    }
                    val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)

                    val avatar = value.interaction.command.attachments["avatar"]?.data?.url
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    serverAvatar(this, system, serverMember!!, avatar, clear)
                }
            }
            access("member", "pronouns") {
                member()
                name("pronouns", required = false)
                raw()
                clear()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val pro = value.interaction.command.strings["pronouns"]
                    val raw = value.interaction.command.booleans["raw"] ?: false
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    pronouns(this, system, member!!, pro, raw, clear)
                }
            }
            access("member", "color") {
                member()
                name("color", required = false)
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val color = value.interaction.command.strings["color"]

                    color(this, system, member!!, color?.toColor())
                }
            }
            access("member", "birthday") {
                member()
                name("birthday", required = false)
                clear()
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val birthday = value.interaction.command.strings["birthday"]
                    val clear = value.interaction.command.booleans["clear"] ?: false

                    birthday(this, system, member!!, tryParseLocalDate(birthday)?.first, clear)
                }
            }
            subCommand("proxy-add", "Adds a proxy") {
                member()
                name("prefix", required = false)
                name("suffix", required = false)
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val prefix = value.interaction.command.strings["prefix"]
                    val suffix = value.interaction.command.strings["suffix"]
                    val proxy = if (prefix == null && suffix == null) null else Pair(prefix, suffix)

                    proxy(this, system, member!!, proxy)
                }
            }
            subCommand("proxy-delete", "Delete a proxy") {
                member()
                name("prefix", required = false)
                name("suffix", required = false)
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val prefix = value.interaction.command.strings["prefix"]
                    val suffix = value.interaction.command.strings["suffix"]
                    val proxy = if (prefix == null && suffix == null) null else Pair(prefix, suffix)
                    val exists = proxy != null
                    val proxyTag =
                        if (exists) database.fetchProxyTagFromMessage(getUser(), "${prefix}text$suffix") else null
                    removeProxy(this, system, member!!, exists, proxyTag)
                }
            }
            access("member", "autoproxy") {
                member()
                bool("value", "The value to set")
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, value.interaction.command.strings["member"]!!)
                    if (!checkMember(this, member)) return@runs false
                    val value = value.interaction.command.booleans["value"]

                    autoproxy(this, system, member!!, value)
                }
            }
        }
    }

    suspend fun <T, C : DiscordContext<T>> CommandNode<T, C>.registerMemberCommands(
        getMem: suspend DiscordContext<T>.() -> String,
        getSys: suspend DiscordContext<T>.() -> SystemRecord?
    ) {
        runs {
            val system = getSys()
            if (!checkSystem(this, system)) return@runs false
            val member = database.findMember(system!!.id, getMem())
            if (!checkMember(this, member)) return@runs false
            access(this, system, member!!)
        }

        literal("remame", "name") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                rename(this, system, member!!, null, false)
            }
            unixLiteral("raw") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    rename(this, system, member!!, null, true)
                }
            }
            greedy("name") { getName ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    rename(this, system, member!!, name, false)
                }
            }
        }

        literal("nickname", "nick", "displayname", "dn") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                nickname(this, system, member!!, null, false, false)
            }
            unixLiteral("clear", "remove") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    nickname(this, system, member!!, null, false, true)
                }
            }
            unixLiteral("raw") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    nickname(this, system, member!!, null, true, false)
                }
            }
            greedy("name") { getName ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    val name = getName()
                    nickname(this, system, member!!, name, false, false)
                }
            }
        }

        literal("servername", "servernick", "sn") {
            guild { getGuildId ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    val guildId = getGuildId() ?: run {
                        respondFailure("Command not ran in server.")
                        return@runs false
                    }
                    val guild = kord.getGuildOrNull(guildId) ?: run {
                        respondFailure("Cannot find server. Am I in it?")
                        return@runs false
                    }
                    val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                    servername(this, system, serverMember!!, null, false, false)
                }
                unixLiteral("clear", "remove") {
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val guildId = getGuildId() ?: run {
                            respondFailure("Command not ran in server.")
                            return@runs false
                        }
                        val guild = kord.getGuildOrNull(guildId) ?: run {
                            respondFailure("Cannot find server. Am I in it?")
                            return@runs false
                        }
                        val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                        servername(this, system, serverMember!!, null, false, true)
                    }
                }
                unixLiteral("raw") {
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val guildId = getGuildId() ?: run {
                            respondFailure("Command not ran in server.")
                            return@runs false
                        }
                        val guild = kord.getGuildOrNull(guildId) ?: run {
                            respondFailure("Cannot find server. Am I in it?")
                            return@runs false
                        }
                        val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                        servername(this, system, serverMember!!, null, true, false)
                    }
                }
                greedy("name") { getName ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val guildId = getGuildId() ?: run {
                            respondFailure("Command not ran in server.")
                            return@runs false
                        }
                        val guild = kord.getGuildOrNull(guildId) ?: run {
                            respondFailure("Cannot find server. Am I in it?")
                            return@runs false
                        }
                        val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                        servername(this, system, serverMember!!, getName(), false, false)
                    }
                }
            }
        }

        literal("description", "desc", "d") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                description(this, system, member!!, null, false, false)
            }
            unixLiteral("raw") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    description(this, system, member!!, null, true, false)
                }
            }
            unixLiteral("clear", "remove") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    description(this, system, member!!, null, false, true)
                }
            }
            greedy("description") { getDesc ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    description(this, system, member!!, getDesc(), false, false)
                }
            }
        }

        literal("avatar", "pfp") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                avatar(this, system, member!!, null, false)
            }
            unixLiteral("clear", "remove") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    avatar(this, system, member!!, null, true)
                }
            }
            attachment("avatar") { getAvatar ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    avatar(this, system, member!!, getAvatar().url, false)
                }
            }
            string("avatar") { getAvatar ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    avatar(this, system, member!!, getAvatar(), false)
                }
            }
        }

        literal("serveravatar", "serverpfp", "sp", "sa") {
            guild { getGuildId ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    val guildId = getGuildId() ?: run {
                        respondFailure("Command not ran in server.")
                        return@runs false
                    }
                    val guild = kord.getGuildOrNull(guildId) ?: run {
                        respondFailure("Cannot find server. Am I in it?")
                        return@runs false
                    }
                    val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                    serverAvatar(this, system, serverMember!!, null, false)
                }
                unixLiteral("clear", "remove") {
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val guildId = getGuildId() ?: run {
                            respondFailure("Command not ran in server.")
                            return@runs false
                        }
                        val guild = kord.getGuildOrNull(guildId) ?: run {
                            respondFailure("Cannot find server. Am I in it?")
                            return@runs false
                        }
                        val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                        serverAvatar(this, system, serverMember!!, null, true)
                    }
                }
                attachment("avatar") { getAvatar ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val guildId = getGuildId() ?: run {
                            respondFailure("Command not ran in server.")
                            return@runs false
                        }
                        val guild = kord.getGuildOrNull(guildId) ?: run {
                            respondFailure("Cannot find server. Am I in it?")
                            return@runs false
                        }
                        val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                        serverAvatar(this, system, serverMember!!, getAvatar().url, false)
                    }
                }
                greedy("avatar") { getAvatar ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val guildId = getGuildId() ?: run {
                            respondFailure("Command not ran in server.")
                            return@runs false
                        }
                        val guild = kord.getGuildOrNull(guildId) ?: run {
                            respondFailure("Cannot find server. Am I in it?")
                            return@runs false
                        }
                        val serverMember = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member!!.id)
                        serverAvatar(this, system, serverMember!!, getAvatar(), false)
                    }
                }
            }
        }

        literal("autoproxy", "ap") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                autoproxy(this, system, member!!, null)
            }
            // TODO: BooleanNode
            literal("disable", "off", "false", "0") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    autoproxy(this, system, member!!, false)
                }
            }
            literal("enable", "on", "true", "1") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    autoproxy(this, system, member!!, true)
                }
            }
        }

        literal("proxy", "p") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                proxy(this, system, member!!, null)
            }

            literal("remove", "rem", "delete", "del") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    removeProxy(this, system, member!!, false, null)
                }
                greedy("proxy") { getProxy ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        extractProxyFromTag(this, getProxy()) ?: return@runs false
                        val proxy = database.fetchProxyTagFromMessage(getUser(), getProxy())
                        proxy ?: run {
                            respondFailure("Proxy tag doesn't exist in this member.")
                            return@runs false
                        }
                        if (proxy.memberId != member!!.id) {
                            respondFailure("Proxy tag doesn't exist in this member.")
                            return@runs false
                        }
                        removeProxy(this, system, member, false, proxy)
                    }
                }
            }

            literal("add", "create") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    proxy(this, system, member!!, null)
                }

                greedy("proxy") { getProxy ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        val proxy = extractProxyFromTag(this, getProxy()) ?: return@runs false
                        proxy(this, system, member!!, proxy)
                    }
                }
            }

            greedy("proxy") { getProxy ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    val proxy = extractProxyFromTag(this, getProxy()) ?: return@runs false
                    proxy(this, system, member!!, proxy)
                }
            }
        }

        literal("pronouns") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                pronouns(this, system, member!!, null, false, false)
            }
            unixLiteral("clear", "remove") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    pronouns(this, system, member!!, null, false, true)
                }
            }
            unixLiteral("raw") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    pronouns(this, system, member!!, null, true, false)
                }
            }
            greedy("pronouns") { getPronouns ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    pronouns(this, system, member!!, getPronouns(), false, false)
                }
            }
        }

        literal("color", "colour", "c") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                color(this, system, member!!, null)
            }
            greedy("color") { getColor ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    color(this, system, member!!, getColor().toColor())
                }
            }
        }

        literal("birthday","bday","birth","bd") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                birthday(this, system, member!!, null, false)
            }
            unixLiteral("clear", "remove") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    birthday(this, system, member!!, null, true)
                }
            }
            greedy("birthday") { getBirthday ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val member = database.findMember(system!!.id, getMem())
                    if (!checkMember(this, member)) return@runs false
                    birthday(this, system, member!!, tryParseLocalDate(getBirthday())?.first, false)
                }
            }
        }
        literal("delete", "remove", "del", "rem") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                val member = database.findMember(system!!.id, getMem())
                if (!checkMember(this, member)) return@runs false
                delete(this, system, member!!)
            }
        }
    }

    suspend fun <T, C : DiscordContext<T>> NodeHolder<T, C>.registerBaseMemberCommands(getSys: suspend DiscordContext<T>.() -> SystemRecord?) {
        literal("member", "m") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                empty(this, system!!)
            }

            string("member") {
                registerMemberCommands(it, getSys)
            }
            literal("delete", "remove", "del") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    delete(this, system!!, null)
                }
                greedy("member") { getMem ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val member = database.findMember(system!!.id, getMem())
                        if (!checkMember(this, member)) return@runs false
                        delete(this, system, member!!)
                    }
                }
            }
            literal("create", "add", "new", "c") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    create(this, system!!, null)
                }
                greedy("member") { getMem ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        create(this, system!!, getMem())
                    }
                }
            }
        }
    }

    suspend fun register() {
        printStep("Registering member commands", 2)
        //TODO: Dedupe code
        Commands.parser.registerBaseMemberCommands {
            database.fetchSystemFromUser(getUser())
        }
    }

    suspend fun <T> empty(ctx: DiscordContext<T>, system: SystemRecord): Boolean {
        ctx.respondWarning("Make sure to provide a member command!")
        return false
    }

    suspend fun <T> access(ctx: DiscordContext<T>, system: SystemRecord, member: MemberRecord): Boolean {
        val guild = ctx.getGuild()
        val settings = database.fetchMemberServerSettingsFromSystemAndMember(guild, system.id, member.id)
        ctx.respondEmbed {
            val systemName = system.name ?: system.id
            author {
                name = member.displayName?.let { "$it (${member.name})\u2007•\u2007$systemName" }
                    ?: "${member.name}\u2007•\u2007$systemName"
                icon = member.avatarUrl.ifBlankThenNull()
            }
            member.avatarUrl?.let {
                thumbnail {
                    url = it
                }
            }
            color = member.color.kordColor()
            description = member.description
            settings?.nickname.notBlank {
                field {
                    name = "Server Name"
                    value = "> $it\n*For ${guild?.name}*"
                    inline = true
                }
            }
            member.pronouns.notBlank {
                field {
                    name = "Pronouns"
                    value = it
                    inline = true
                }
            }
            member.birthday?.let {
                field {
                    name = "Birthday"
                    value = it.toJavaLocalDate().displayDate()
                    inline = true
                }
            }
            if (member.messageCount > 0UL) {
                field {
                    name = "Message Count"
                    value = member.messageCount.toString()
                    inline = true
                }
            }
            database.fetchProxiesFromSystemAndMember(system.id, member.id).let { proxyTags ->
                if (proxyTags.isNullOrEmpty()) return@let
                field {
                    name = "Proxy Tags"
                    value = proxyTags.joinToString(
                        separator = "\n",
                        limit = 20,
                        truncated = "\n\u2026"
                    ) { "``${it.toString().replace("`", "\uFE0F`")}\uFE0F``" }
                    inline = true
                }
            }
            footer {
                text =
                    "Member ID \u2009• \u2009${member.id}\u2007|\u2007System ID \u2009• \u2009${system.id}\u2007|\u2007Created "
            }
            timestamp = member.timestamp
        }
        return true
    }

    suspend fun <T> rename(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        name: String?,
        raw: Boolean
    ): Boolean {
        name ?: run {
            if (raw)
                ctx.respondPlain("`${member.name}`")
            else ctx.respondSuccess("Member's name is `${member.name}`!")

            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.name = name
        database.updateMember(member)

        ctx.respondSuccess("Member's name is now `$name`!")

        return true
    }

    suspend fun <T> nickname(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        name: String?,
        raw: Boolean,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            member.displayName = null
            database.updateMember(member)

            ctx.respondSuccess("Member display name cleared!")

            return true
        }

        name ?: run {
            member.displayName ?: run {
                ctx.respondWarning("Member doesn't have a display name.")
                return true
            }

            if (raw)
                ctx.respondPlain("`${member.displayName}`")
            else ctx.respondSuccess("Member's display name is `${member.displayName}`!")

            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.displayName = name
        database.updateMember(member)

        ctx.respondSuccess("Member's display name is now `$name`!")

        return true
    }

    suspend fun <T> servername(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        serverMember: MemberServerSettingsRecord,
        name: String?,
        raw: Boolean,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            serverMember.nickname = null
            database.updateMemberServerSettings(serverMember)

            ctx.respondSuccess("Member's server name cleared!")
            return true
        }

        name ?: run {
            serverMember.nickname ?: run {
                ctx.respondWarning("Member doesn't have a server nickname.")

                return true
            }

            if (raw)
                ctx.respondPlain("`${serverMember.nickname}`")
            else ctx.respondSuccess("Member's server nickname is `${serverMember.nickname}`!")

            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        serverMember.nickname = name
        database.updateMemberServerSettings(serverMember)

        ctx.respondSuccess("Member's server nickname is now $name!")

        return true
    }

    suspend fun <T> description(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        description: String?,
        raw: Boolean,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            member.description = null
            database.updateMember(member)
            ctx.respondSuccess("Member's description cleared!")
            return true
        }

        description ?: run {
            member.description ?: run {
                ctx.respondWarning("Member has no description set")
                return true
            }

            if (raw)
                ctx.respondPlain("```md\n${member.description}```")
            else ctx.respondSuccess("Member's description is ${member.description}")

            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.description = description
        database.updateMember(member)
        ctx.respondSuccess("Member description updated!")

        return true
    }

    suspend fun <T> avatar(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        avatar: String?,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            member.avatarUrl = null
            database.updateMember(member)
            ctx.respondSuccess("Member's avatar cleared!")
            return true
        }

        avatar ?: run {
            member.avatarUrl ?: run {
                ctx.respondWarning("Member doesn't have an avatar set.")
                return true
            }

            ctx.respondEmbed {
                image = member.avatarUrl
                color = member.color.kordColor()
            }
            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.avatarUrl = avatar
        database.updateMember(member)
        ctx.respondSuccess("Member's avatar updated!")

        return true
    }

    suspend fun <T> serverAvatar(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        serverMember: MemberServerSettingsRecord,
        avatar: String?,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            serverMember.avatarUrl = null
            database.updateMemberServerSettings(serverMember)
            ctx.respondSuccess("Member's server avatar cleared!")
            return true
        }

        avatar ?: run {
            serverMember.avatarUrl ?: run {
                ctx.respondWarning("Member doesn't have a server avatar set.")
                return true
            }

            ctx.respondEmbed {
                image = serverMember.avatarUrl
            }
            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        serverMember.avatarUrl = avatar
        database.updateMemberServerSettings(serverMember)
        ctx.respondSuccess("Member's server avatar updated!")

        return true
    }

    suspend fun <T> removeProxy(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        exists: Boolean,
        proxy: MemberProxyTagRecord?
    ): Boolean {
        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        if (!exists) {
            ctx.respondWarning("Please provide a proxy tag to remove.")
            return true
        }

        proxy ?: run {
            ctx.respondFailure("Proxy tag doesn't exist in this member.")
            return false
        }

        if (proxy.memberId != member.id) {
            ctx.respondFailure("Proxy tag doesn't exist in this member.")
            return false
        }

        database.dropProxyTag(proxy)
        ctx.respondSuccess("Proxy removed!")

        return true
    }

    suspend fun <T> autoproxy(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        enabled: Boolean?
    ): Boolean {
        enabled ?: run {
            ctx.respondSuccess("AutoProxy for ${member.showDisplayName()} is set to ${if (member.autoProxy) "on" else "off"}!")
            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        database.updateMember(member.apply { autoProxy = enabled })
        ctx.respondSuccess("${if (enabled) "Enabled" else "Disabled"} front & latch autproxy for ${member.showDisplayName()}!")
        return true
    }

    suspend fun <T> extractProxyFromTag(ctx: DiscordContext<T>, proxy: String): Pair<String?, String?>? {
        if (!proxy.contains("text")) {
            ctx.respondFailure("Given proxy tag does not contain `text`.")
            return null
        }
        val prefix = proxy.substring(0, proxy.indexOf("text"))
        val suffix = proxy.substring(4 + prefix.length, proxy.length)
        if (prefix.isEmpty() && suffix.isEmpty()) {
            ctx.respondFailure("Proxy tag must contain either a prefix or a suffix.")
            return null
        }
        return Pair(prefix, suffix)
    }

    suspend fun <T> proxy(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        proxy: Pair<String?, String?>?
    ): Boolean {
        proxy ?: run {
            ctx.respondEmbed {
                member(member, ctx.getGuild()?.id?.value ?: 0UL)
                title = "${member.name}'s proxy tags"
                description = database.fetchProxiesFromSystemAndMember(member.systemId, member.id).run {
                    if (isNullOrEmpty())
                        "${member.name} has no tags set."
                    else
                        joinToString(
                            separator = "\n",
                            limit = 20,
                            truncated = "\n\u2026"
                        ) { "``$it``" }
                }
            }

            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        database.createProxyTag(member.systemId, member.id, proxy.first, proxy.second) ?: run {
            ctx.respondFailure("Proxy tag already exists in this system.")
            return false
        }
        ctx.respondSuccess("Proxy tag `${proxy.first ?: ""}text${proxy.second ?: ""}` created!")
        return true
    }

    suspend fun <T> pronouns(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        pronouns: String?,
        raw: Boolean,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            member.pronouns = null
            database.updateMember(member)
            ctx.respondSuccess("Member's pronouns cleared!")
            return true
        }

        pronouns ?: run {
            member.pronouns ?: run {
                ctx.respondWarning("Member has no pronouns set")
                return true
            }

            if (raw)
                ctx.respondPlain("`${member.pronouns}`")
            else ctx.respondSuccess("Member's pronouns are ${member.pronouns}")

            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.pronouns = pronouns
        database.updateMember(member)
        ctx.respondSuccess("Member's pronouns are now $pronouns!")
        return true
    }

    suspend fun <T> color(ctx: DiscordContext<T>, system: SystemRecord, member: MemberRecord, color: Int?): Boolean {
        color ?: run {
            ctx.respondSuccess("Member's color is `${member.color.fromColor()}`")
            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.color = color
        database.updateMember(member)
        ctx.respondSuccess("Member's color is now `${color.fromColor()}!")
        return true
    }

    suspend fun <T> birthday(
        ctx: DiscordContext<T>,
        system: SystemRecord,
        member: MemberRecord,
        birthday: LocalDate?,
        clear: Boolean
    ): Boolean {
        if (clear) {
            if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
                ctx.respondFailure("You don't have access to edit this information.")
                return false
            }

            member.birthday = null
            database.updateMember(member)
            ctx.respondSuccess("Member's birthday cleared!")
            return true
        }

        birthday ?: run {
            member.birthday ?: run {
                ctx.respondWarning("Member does not have a birthday.")
                return true
            }
            ctx.respondSuccess("Member's birthday is ${member.birthday}!")
            return true
        }

        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member.birthday = birthday
        database.updateMember(member)
        ctx.respondSuccess("Member's birthday is now $birthday!")
        return true
    }

    suspend fun <T> delete(ctx: DiscordContext<T>, system: SystemRecord, member: MemberRecord?): Boolean {
        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        member ?: run {
            ctx.respondFailure("Make sure to provide the name of the member to delete!")
            return false
        }

        TimedYesNoPrompt.build(
            runner = ctx.getUser()!!.id,
            channel = ctx.getChannel(),
            message = "Are you sure you want to delete member `${member.asString()}`?\n" +
                    "Their data will be lost forever (A long time!)",
            yes = Button("Delete Member", Button.wastebasket, ButtonStyle.Danger) {
                database.dropMember(member.systemId, member.id)
                content = "Member deleted"
            },
        )

        return true
    }

    suspend fun <T> create(ctx: DiscordContext<T>, system: SystemRecord, name: String?, ): Boolean {
        if (!system.canEditMembers(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        name ?: run {
            ctx.respondFailure("Make sure to provide a name for the new member!")
            return false
        }

        val member = database.fetchMemberFromSystemAndName(system.id, name, false)
        if (member != null) {
            TimedYesNoPrompt.build(
                runner = ctx.getUser()!!.id,
                channel = ctx.getChannel(),
                message = "You already have a member named \"${member.name}\" (`${member.id}`)." +
                        "\nDo you want to create another member with the same name?",
                yes = "Create $name" to {
                    val newMember = database.createMember(system.id, name)
                    content = if (newMember != null) {
                        "Member created with ID `${newMember.id}`."
                    } else {
                        "Something went wrong while creating your member. Try again?"
                    }
                }
            )
        } else {
            val newMember = database.createMember(system.id, name)
            newMember ?: run {
                ctx.respondFailure("Something went wrong while creating your member. Try again?")
                return false
            }
            ctx.respondSuccess("Member created with ID `${newMember.id}`.")
        }
        return true
    }
}
