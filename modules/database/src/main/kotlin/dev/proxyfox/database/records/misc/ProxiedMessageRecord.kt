/*
 * Copyright (c) 2022, The ProxyFox Group
 *
 * This Source Code is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.proxyfox.database.records.misc

import org.bson.types.ObjectId
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ProxiedMessageRecord {
    var _id: ObjectId = ObjectId()
    var creationDate = OffsetDateTime.now(ZoneOffset.UTC)
    var oldMessageId: ULong = 0UL
    var newMessageId: ULong = 0UL
    var guildId: ULong = 0UL
    var channelId: ULong = 0UL
    var threadId: ULong? = null
    var memberId: String = ""
    var systemId: String = ""
    var deleted = false
}