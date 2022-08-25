package dev.proxyfox.database

import dev.proxyfox.common.printStep
import dev.proxyfox.database.DatabaseUtil.databaseFromString

lateinit var database: Database

suspend fun main(database: String?) = DatabaseMain.main(database)

object DatabaseMain {
    suspend fun main(db: String?) {
        printStep("Setup database", 1)
        database = try {
            databaseFromString(db)
        } catch (err: Throwable) {
            printStep("Database setup failed. Falling back to JSON", 2)
            JsonDatabase()
        }.setup()
        printStep("Registering shutdown hook for database", 2)
        // Allows the database to shut down & save correctly.
        Runtime.getRuntime().addShutdownHook(Thread(database::close))
    }
}