/*
 * Copyright (c) 2022, The ProxyFox Group
 *
 * This Source Code is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.proxyfox.importer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import dev.kord.core.entity.Entity
import dev.proxyfox.database.Database
import dev.proxyfox.database.database
import dev.proxyfox.database.records.member.MemberProxyTagRecord
import dev.proxyfox.database.records.member.MemberRecord
import dev.proxyfox.database.records.system.SystemRecord
import java.io.InputStreamReader
import java.io.Reader

/**
 * Imports a system file from a [String]. Supports both PluralKit and TupperBox formats.
 *
 * @param string The JSON to import
 * @return The importer used
 * @throws ImporterException When importing fails for any reason
 * @author Oliver
 * */
suspend fun import(string: String, user: Entity?) = import(database, string, user)

/**
 * Imports a system file from an [InputStreamReader]. Supports both PluralKit and TupperBox formats.
 *
 * @param reader The JSON to import
 * @return The importer used
 * @throws ImporterException When importing fails for any reason
 * @author Oliver
 * */
suspend fun import(reader: Reader, user: Entity?) = import(database, reader, user)

/**
 * Imports a system file from a [String]. Supports both PluralKit and TupperBox formats.
 *
 * @param string The JSON to import
 * @return The importer used
 * @throws ImporterException When importing fails for any reason
 * @author Oliver
 * */
suspend fun import(database: Database, string: String, user: Entity?): Importer {
    try {
        return import(database, JsonParser.parseString(string), user)
    } catch (syntax: JsonSyntaxException) {
        throw ImporterException("Not a JSON file", syntax)
    }
}

/**
 * Imports a system file from an [InputStreamReader]. Supports both PluralKit and TupperBox formats.
 *
 * @param reader The JSON to import
 * @return The importer used
 * @throws ImporterException When importing fails for any reason
 * @author Oliver
 * */
suspend fun import(database: Database, reader: Reader, user: Entity?): Importer {
    try {
        return import(database, JsonParser.parseReader(reader), user)
    } catch (syntax: JsonSyntaxException) {
        throw ImporterException("Not a JSON file", syntax)
    }
}

/**
 * Imports a system file from a [JsonElement]. Supports both PluralKit and TupperBox formats.
 *
 * @param element The JSON to import
 * @return The importer used
 * @throws ImporterException When importing fails for any reason
 * @author Oliver
 * */
suspend fun import(database: Database, element: JsonElement, user: Entity?): Importer {
    if (!element.isJsonObject) throw ImporterException("Not a JSON object")
    val map = element.asJsonObject
    if (map.size() == 0) throw ImporterException("No data to import.")
    val importer = if (map.has("tuppers")) TupperBoxImporter() else PluralKitImporter()
    importer.import(database, map, user!!.id.value)
    return importer
}

/**
 * Interface to import a JSON file into the database
 *
 * @author Oliver
 * */
interface Importer {
    suspend fun import(json: JsonObject, userId: ULong) = import(database, json, userId)
    suspend fun import(database: Database, json: JsonObject, userId: ULong)

    // Getters:
    suspend fun getSystem(): SystemRecord
    suspend fun getMembers(): List<MemberRecord>
    suspend fun getMemberProxyTags(member: MemberRecord): List<MemberProxyTagRecord>
    suspend fun getNewMembers(): Int
    suspend fun getUpdatedMembers(): Int
}