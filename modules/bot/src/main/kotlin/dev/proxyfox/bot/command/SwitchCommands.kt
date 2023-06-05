/*
 * Copyright (c) 2022-2023, The ProxyFox Group
 *
 * This Source Code is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.proxyfox.bot.command

import dev.kord.rest.builder.interaction.SubCommandBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.proxyfox.bot.Emojis
import dev.proxyfox.bot.command.context.DiscordContext
import dev.proxyfox.bot.command.context.InteractionCommandContext
import dev.proxyfox.bot.command.context.runs
import dev.proxyfox.bot.deferChatInputCommand
import dev.proxyfox.bot.parseDuration
import dev.proxyfox.command.CommandParser
import dev.proxyfox.command.NodeHolder
import dev.proxyfox.command.node.builtin.greedy
import dev.proxyfox.command.node.builtin.literal
import dev.proxyfox.command.node.builtin.stringList
import dev.proxyfox.common.trimEach
import dev.proxyfox.database.database
import dev.proxyfox.database.records.system.SystemRecord
import dev.proxyfox.database.records.system.SystemSwitchRecord
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

object SwitchCommands : CommandRegistrar {
    var interactionExecutors: HashMap<String, suspend InteractionCommandContext.() -> Boolean> = hashMapOf()

    fun SubCommandBuilder.runs(action: suspend InteractionCommandContext.() -> Boolean) {
        interactionExecutors[name] = action
    }

    override val displayName: String = "Switch"

    override suspend fun registerSlashCommands() {
        deferChatInputCommand("switch", "Create or manage switches!") {
            subCommand("create", "Create a switch") {
                string("members", "The members to use, comma separated") {
                    required = true
                }
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val members = value.interaction.command.strings["members"]!!.split(",").toTypedArray()
                    members.trimEach()
                    switch(this, system, members)
                }
            }
            subCommand("out", "Marks that no-one's fronting") {
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    out(this, system)
                }
            }
            subCommand("delete", "Deletes the latest switch") {
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val switch = database.fetchLatestSwitch(system.id)
                    if (!checkSwitch(this, switch)) return@runs false
                    val oldSwitch = database.fetchSecondLatestSwitch(system.id)
                    delete(this, system, switch, oldSwitch)
                }
            }
            subCommand("move", "Moves the latest switch") {
                name("time")
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    val switch = database.fetchLatestSwitch(system.id)
                    if (!checkSwitch(this, switch)) return@runs false
                    val oldSwitch = database.fetchSecondLatestSwitch(system.id)
                    val time = value.interaction.command.strings["time"]!!
                    move(this, system, switch, oldSwitch, time)
                }
            }
            subCommand("list", "Lists your switches") {
                system()
                runs {
                    val system = getSystem()
                    if (!checkSystem(this, system)) return@runs false
                    list(this, system)
                }
            }
        }
    }

    suspend fun <T, C : DiscordContext<T>> NodeHolder<T, C>.registerSwitchCommands(getSys: suspend DiscordContext<T>.() -> SystemRecord?) {
        literal("switch", "sw") {
            runs {
                val system = getSys()
                if (!checkSystem(this, system)) return@runs false
                respondFailure("Please provide a switch subcommand.")
                false
            }
            literal("out", "o") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    out(this, system)
                }
            }
            literal("delete", "del", "remove", "rem") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val switch = database.fetchLatestSwitch(system.id)
                    if (!checkSwitch(this, switch)) return@runs false
                    val oldSwitch = database.fetchSecondLatestSwitch(system.id)
                    delete(this, system, switch, oldSwitch)
                }
            }
            literal("move","mv","m") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    val switch = database.fetchLatestSwitch(system.id)
                    if (!checkSwitch(this, switch)) return@runs false
                    val oldSwitch = database.fetchSecondLatestSwitch(system.id)
                    move(this, system, switch, oldSwitch, null)
                }
                greedy("time") { getTime ->
                    runs {
                        val system = getSys()
                        if (!checkSystem(this, system)) return@runs false
                        val switch = database.fetchLatestSwitch(system.id)
                        if (!checkSwitch(this, switch)) return@runs false
                        val oldSwitch = database.fetchSecondLatestSwitch(system.id)
                        move(this, system, switch, oldSwitch, getTime())
                    }
                }
            }
            literal("list", "l") {
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    list(this, system)
                }
            }
            stringList("members") { getMembers ->
                runs {
                    val system = getSys()
                    if (!checkSystem(this, system)) return@runs false
                    switch(this, system, getMembers().toTypedArray())
                }
            }
        }
    }

    override suspend fun CommandParser<Any, DiscordContext<Any>>.registerTextCommands() {
        registerSwitchCommands {
            database.fetchSystemFromUser(getUser())
        }
    }

    private suspend fun <T> out(ctx: DiscordContext<T>, system: SystemRecord): Boolean {
        if (!system.canEditSwitches(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        database.createSwitch(system.id, listOf())
        ctx.respondSuccess("Switch registered. Take care!")
        return true
    }

    private suspend fun <T> move(ctx: DiscordContext<T>, system: SystemRecord, switch: SystemSwitchRecord, oldSwitch: SystemSwitchRecord?, time: String?): Boolean {
        if (!system.canEditSwitches(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        time ?: run {
            ctx.respondFailure("Please provide a time to move the switch back")
            return false
        }

        val either = time.parseDuration()
        either.right?.let {
            ctx.respondFailure(it)
            return false
        }

        val nowMinus = Clock.System.now().minus(either.left!!.inWholeMilliseconds, DateTimeUnit.MILLISECOND)
        if (oldSwitch != null && oldSwitch.timestamp > nowMinus) {
            ctx.respondFailure("It looks like you're trying to break the space-time continuum..\n" +
                    "The provided time is set before the previous switch")
            return false
        }

        val members = switch.memberIds.map {
            database.fetchMemberFromSystem(system.id, it)?.showDisplayName() ?: "*Unknown*"
        }.joinToString(", ")

        ctx.timedYesNoPrompt(
            message = "Are you sure you want to move the switch $members back to <t:${nowMinus.epochSeconds}>?",
            yes = "Move switch" to {
                switch.timestamp = nowMinus
                database.updateSwitch(switch)
                content = "Switch updated."
            },
            yesEmoji = Emojis.move
        )

        return true
    }

    private suspend fun <T> delete(ctx: DiscordContext<T>, system: SystemRecord, switch: SystemSwitchRecord, oldSwitch: SystemSwitchRecord?): Boolean {
        if (!system.canEditSwitches(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        val epoch = switch.timestamp.epochSeconds

        ctx.timedYesNoPrompt(
            message = """
                Are you sure you want to delete the latest switch (${switch.membersAsString()}, <t:$epoch:R>)? ${if (oldSwitch != null) "\nThe previous switch would be at <t:${oldSwitch.timestamp.epochSeconds}:R>" else ""}
                The data will be lost forever (A long time!)
                """.trimIndent(),
            yes = "Delete switch" to {
                database.dropSwitch(switch)
                content = "Switch deleted."
            },
            yesEmoji = Emojis.wastebasket,
            danger = true
        )

        return true
    }

    private suspend fun <T> list(ctx: DiscordContext<T>, system: SystemRecord): Boolean {
        if (!system.canAccess(ctx.getUser()!!.id.value)) {
            // Force the bot to treat the system as nonexistent
            return checkSystem(ctx, null)
        }

        // We know the system exists here, will be non-null
        val switches = database.fetchSortedSwitchesFromSystem(system.id)!!

        ctx.pager(
            switches,
            20,
            { title = "[$it] Front history of ${system.showName}" },
            { membersAsString("**", "**") + " (<t:${timestamp.epochSeconds}:R>)\n" },
            false
        )

        return true
    }

    private suspend fun <T> switch(ctx: DiscordContext<T>, system: SystemRecord, members: Array<String>): Boolean {
        if (!system.canEditSwitches(ctx.getUser()!!.id.value)) {
            ctx.respondFailure("You don't have access to edit this information.")
            return false
        }

        val membersOut = ArrayList<String>()
        var memberString = ""
        members.forEach {
            val member = database.findMember(system.id, it) ?: run {
                ctx.respondFailure("Couldn't find member `$it`, do they exist?")
                return false
            }
            membersOut += member.id
            memberString += "`${member.showDisplayName()}`, "
        }
        memberString = memberString.substring(0, memberString.length - 2)
        database.createSwitch(system.id, membersOut)

        ctx.respondSuccess("Switch registered! Current fronters: $memberString")
        return true
    }

    private suspend fun SystemSwitchRecord.membersAsString(prefix: String = "", postfix: String = ""): String {
        return if (memberIds.isEmpty()) {
            "*None*"
        } else {
            memberIds.map {
                database.fetchMemberFromSystem(systemId, it)?.showDisplayName() ?: "*Unknown*"
            }.joinToString(", ", prefix, postfix)
        }
    }
}