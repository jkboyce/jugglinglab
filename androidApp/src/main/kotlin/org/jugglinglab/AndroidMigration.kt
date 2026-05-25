//
// AndroidMigration.kt
//
// Migrate user-edited patterns from the previous JL app.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab

import org.jugglinglab.jml.JmlPatternList
import android.content.Context
import java.io.File

sealed class MigrationResult {
    data class Success(val content: String, val numPatterns: Int) : MigrationResult()
    object NotFound : MigrationResult()
    data class Error(val errorCode: String) : MigrationResult()
}

private fun Context.copyAssetToCache(assetName: String): File? {
    return try {
        val dbFile = File(cacheDir, assetName)
        if (!dbFile.exists()) {
            assets.open(assetName).use { inputStream ->
                java.io.FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        dbFile
    } catch (e: Exception) {
        println("Error copying asset $assetName: ${e.message}")
        null
    }
}

// If Favorites.jml doesn't exist, try migrating from the SQLite-based
// format used by the previous Android Juggling Lab app.

fun Context.tryMigrateFavorites(): MigrationResult {
    // flag to control whether to use a cached test DB or the real DB.
    val migrationTest = false

    val fromFile = if (migrationTest) {
        copyAssetToCache("BDD_case1.db")
    } else {
        val dbFile = getDatabasePath("BDD.db")
        if (dbFile.exists()) dbFile else null
    } ?: return MigrationResult.NotFound
    val origFile = copyAssetToCache("BDD_orig.db") ?: return MigrationResult.NotFound

    var fromDb: android.database.sqlite.SQLiteDatabase? = null
    var origDb: android.database.sqlite.SQLiteDatabase? = null

    try {
        println("#### Starting migration")

        fromDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            fromFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        origDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            origFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )

        // 1. Read all Collections from FROM DB
        data class CollectionData(val id: Int, val name: String, val isStarred: Boolean)

        val collectionsMap = mutableMapOf<Int, CollectionData>()

        // default collection names mapped by their XML_LINE_NUMBER (0-indexed)
        val defaultNames = listOf(
            "3-Cascade Step By Step",
            "4-Fountain Step By Step",
            "5-Cascade Step By Step",
            "3-Cascade Tricks",
            "3-ball Tricks",
            "4-ball Tricks",
            "5-ball Tricks",
            "Shower",
            "Mills Mess",
            "Box",
            "Columns",
            "One Hand Tricks",
            "Siteswaps",
            "Multiplex",
            "Synchronous",
            "Numbers",
            "Are You God?",
            "Tricks by Isaac Orr",
            "Tricks by JAG",
            "Multiplex mills mess",
            "Patterns by PWN",
            "Patterns By Scotch Tom",
            "Stupid Patterns By Chunky Kibbles",
            "Passing 2 jugglers 5 balls",
            "Passing 2 jugglers 6 balls",
            "Passing 2 jugglers 7 balls",
            "Passing 2 jugglers 8 balls",
            "Passing 3 jugglers 9 balls",
            "Starred"
        )

        fromDb.rawQuery(
            "SELECT ID_COLLECTION, XML_LINE_NUMBER, CUSTOM_DISPLAY FROM Collection",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val xmlLineNum = cursor.getInt(1)
                val customDisplay = cursor.getString(2)

                val name = if (!customDisplay.isNullOrEmpty()) {
                    customDisplay
                } else {
                    defaultNames.getOrNull(xmlLineNum) ?: "Collection $id"
                }
                val isStarred = xmlLineNum == 28
                collectionsMap[id] = CollectionData(id, name, isStarred)
            }
        }

        // 2. Map tricks to their collections
        val trickCollections = mutableMapOf<Int, MutableList<CollectionData>>()
        fromDb.rawQuery("SELECT ID_TRICK, ID_COLLECTION FROM TrickCollection", null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val trickId = cursor.getInt(0)
                    val collectionId = cursor.getInt(1)
                    collectionsMap[collectionId]?.let {
                        trickCollections.getOrPut(trickId) { mutableListOf() }.add(it)
                    }
                }
            }

        // 3. Collect all tricks from ORIG into a set for comparison
        val origTricks = mutableSetOf<String>()

        val defaultTrickNames = listOf(
            "1 Ball out of a 3-Cascade",
            "Throw Twice",
            "2 Balls out of a 3-Cascade",
            "Throw 3 Times",
            "3-Cascade",
            "2 in One Hand",
            "4-Synchronous Fountain",
            "4-Fountain",
            "2 Balls out of a 5-Cascade",
            "3 Balls",
            "Chase",
            "Flash",
            "4 Balls",
            "4 Balls out of a 5-Cascade",
            "552",
            "5551",
            "5-Cascade",
            "3-ball Flash",
            "Chop",
            "Eating Apples",
            "Eating an Apple (for light eaters)",
            "Wide Cascade",
            "Juggler's Tennis",
            "Over the Head",
            "Reverse Cascade",
            "Wide Reverse Cascade",
            "Reachover",
            "Reachover (alternate)",
            "Reachunder",
            "Reachunder (alternate)",
            "Crossed Arm Reverse",
            "1Up-2Up A (Columns)",
            "1Up-2Up B",
            "1Up-2Up C",
            "1Up-2Up D",
            "1Up-2Up E",
            "1Up-2Up F",
            "Yo-Yo",
            "Oy-Oy",
            "Around The Yo-Yo",
            "Yo-Yo (Columns Fake A)",
            "Yo-Yo (Columns Fake B)",
            "Yo-Yo (Columns Fake C)",
            "Yo-Yo (Traverse)",
            "441",
            "Arches",
            "Statue of Liberty A",
            "Statue of Liberty B",
            "Shuffle (The Slam)",
            "See Saw Shuffle (Luke's Shuffle)",
            "441 Shuffle",
            "Both Side Slam",
            "Robot (Machine,Factory)",
            "Exchange (Pendulum,Drop)",
            "Carry",
            "Follow",
            "4-Reverse Synchronous Fountain",
            "4-Reverse Fountain",
            "4-Columns Switch",
            "4-ball Cross A",
            "4-ball Cross B",
            "444447333",
            "4-ball Tennis",
            "5-Reverse Cascade",
            "3-Cascade -> 5-Cascade",
            "555555744",
            "3-Shower Japanese OTEDAMA",
            "3-Half Shower A",
            "3-Half Shower B",
            "4-Shower",
            "4-Half Shower",
            "5-Half Shower A",
            "5-Half Shower B",
            "5-Half Shower C",
            "6-Half Shower",
            "3-Synchronous Shower",
            "4-Synchronous Shower",
            "3-High-Low Shower A",
            "3-High-Low Shower B",
            "3-High-Low Shower C",
            "4-High-Low Shower",
            "Under-the-Hand Shower",
            "Under-the-Hand Shower (alternate)",
            "4-Half Shower Like Trick",
            "High Half Shower",
            "Multi-Shower",
            "False Shower (Windmill)",
            "2 out of the Mills Mess",
            "Standard Mills Mess",
            "Mills Mess 423",
            "Mills Mess 414",
            "Mills Mess 315",
            "Mills Mess Box",
            "4-ball Mills Mess",
            "Mills Mess 534",
            "Mills Mess 552",
            "Mills Mess 642",
            "5-ball Mills Mess",
            "6-ball Mills Mess",
            "Mills Mess 864",
            "No Through Mills Mess",
            "Half Mess",
            "Reverse Mills Mess",
            "Mills 44133",
            "2-Mills Simultaneous",
            "3-Mills Simultaneous",
            "4-Mills Simultaneous",
            "2 Balls out of the Box",
            "Box (See Saw)",
            "A Box-like Pattern A",
            "A Box-like Pattern B",
            "Double Box",
            "4-ball Box A",
            "4-ball Box B",
            "4-ball Box C",
            "5-ball Box",
            "Boston Mess A",
            "Boston Mess B",
            "4-Columns (Pistons)",
            "4-Synchronous Columns (Asymmetry)",
            "4-Synchronous Columns (Symmetry)",
            "4-Synchronous Columns (Splits)",
            "5-Columns",
            "5-Mills Mess Columns A",
            "5-Mills Mess Columns B",
            "5-Mills Mess Columns C",
            "6-Columns",
            "Multi-Columns",
            "2-ball Columns in One Hand",
            "3-ball in One Hand",
            "3-Multiplex in One Hand",
            "Combination in One Hand",
            "3 Hi-Lo in One Hand",
            "3-ball Columns in One Hand",
            "Cascade in One Hand",
            "3-ball Combination",
            "53",
            "44453",
            "501",
            "531",
            "561",
            "Complete Waste of a 5 Ball Juggler",
            "453",
            "720",
            "753",
            "741",
            "744",
            "6424",
            "64",
            "66661",
            "61616",
            "(5201) & (0040)",
            "(70300) & (02012)",
            "7272712",
            "51414",
            "7161616",
            "88333",
            "75751",
            "123456789",
            "[34]1",
            "4[43]1 441+1",
            "5-ball Multiplex A step_1",
            "5-ball Multiplex A step_2",
            "5-ball Multiplex A",
            "5-Cascade -> 5-Multiplex A No.1",
            "5-Cascade -> 5-Multiplex A No.2",
            "5-ball Multiplex B step_1",
            "5-ball Multiplex B",
            "25[75]51",
            "7-ball Splits A",
            "7-ball Splits B",
            "26[76]",
            "[234]57",
            "9-ball Multiplex",
            "3-Synchronous Cascade A",
            "3-Synchronous Cascade B",
            "(2x,6x)(6x,2x)",
            "(4x,2x)(2,4)",
            "(4x,6)(0,2x)",
            "(2,6x)(2x,6)(6x,2)(6,2x)",
            "(2,4)([44x],2x)",
            "(2,[62])([22],6x)([62],2)(6x,[22])",
            "6-ball Synchronous Fountain",
            "5-Shower",
            "6-Shower",
            "7-Shower",
            "6-Fountain",
            "7-Cascade",
            "8-Fountain",
            "9-Cascade",
            "35-Cascade",
            "18-Shower",
            "35-Multi Shower",
            "12-Mills Mess",
            "[b9753]0020[22]0[222]0[2222]0",
            "123456789abcdefghijklmnopqrstuv",
            "9-ball Box",
            "xvtrpnljhfdb97531",
            "Ken",
            "Penta-Multiplex",
            "575151",
            "7141404",
            "(8x,2)(8,8)(8,8)(2,8x)(8,8)(8,8)",
            "(6x,2)(6,6)(2,6x)(6,6)",
            "([6x6],2)(2,[6x6])",
            "56702",
            "64244",
            "[34]",
            "4-ball Box variation 1",
            "4-ball Box variation 2",
            "6316131",
            "55514",
            "12345",
            "23456",
            "1234567",
            "633",
            "44633",
            "44444444633",
            "64514",
            "Orr Multiplex (3 balls)",
            "Orr Multiplex (4 balls)",
            "Reverse Orr Multiplex",
            "Macdonalds!",
            "123456",
            "303456",
            "63123",
            "6051",
            "63303",
            "64113",
            "70161",
            "83031",
            "612",
            "62313",
            "63141",
            "52413",
            "63501",
            "Alternate 3 Balls in Hand A",
            "Alternate 3 Balls in Hand B",
            "Alternate 3 Balls in Hand C",
            "56162",
            "6451",
            "5641",
            "5560",
            "6352",
            "83333",
            "845151",
            "83441",
            "83531",
            "83522",
            "7522",
            "83423",
            "7423",
            "804",
            "36362",
            "7531",
            "75314",
            "714",
            "73334",
            "5911",
            "831",
            "7045",
            "73451",
            "7441",
            "74414",
            "642",
            "4246",
            "62525",
            "5751613",
            "673175151",
            "773151",
            "746151",
            "661515",
            "751515",
            "6631",
            "72461",
            "72416",
            "73631",
            "75661",
            "66314",
            "63524",
            "7405 Transitions A",
            "7405 Transitions B",
            "7405 Transitions C",
            "7405 Transitions D",
            "7405 Transitions E",
            "555183333",
            "5551955500",
            "5551552",
            "55255550",
            "53633",
            "7731514",
            "7461514",
            "5661514",
            "35741",
            "4 Shower Transition 1",
            "4 Shower Transition 2",
            "4 Shower Transition 3",
            "Alternate 4 Shower 1",
            "Alternate 4 Shower 2",
            "Alternate 4 Shower 3",
            "4 Shower - Three heights",
            "4 Shower - Crazy",
            "4 Shower with a leak",
            "1 up 1 across",
            "Playing catch",
            "Looking idle",
            "5 Balls Peter Gunn",
            "726",
            "7346",
            "7463",
            "663",
            "88441",
            "88531",
            "8444",
            "8534",
            "8633",
            "84445",
            "85345",
            "94444",
            "94534",
            "96451",
            "95551",
            "96631",
            "771",
            "861",
            "645",
            "6662",
            "756615",
            "7562",
            "777171",
            "Full 5 Flash",
            "3 of 5 Flash",
            "97531",
            "x",
            "zxv",
            "itzik",
            "orr",
            "3-Balls Peter Gunn Theme",
            "5 ball Easier Variation",
            "5 ball Harder Variation",
            "4 Ball (Orinoco)",
            "Orr Multiplex MM",
            "¾ Norton's Demultiplexer",
            "23[34]",
            "20[34]",
            "53[34]",
            "26[34]20[34]23[34]",
            "Back & Forth",
            "Back & Forth 2",
            "High Triplex",
            "Low Triplex",
            "555504",
            "5524",
            "534",
            "666660",
            "6666605",
            "666615",
            "66625",
            "6635",
            "7777770",
            "777771",
            "77772",
            "7773",
            "774",
            "75",
            "77777706",
            "7777716",
            "777726",
            "77736",
            "7746",
            "756",
            "Long Name for Pointless Trick",
            "Random Doodle 1",
            "Random Doodle 2",
            "Ben Beaver at Chocfest",
            "Extended Sexta-Multiplex",
            "297 Balls 11-Multiplex",
            "528 Balls",
            "One-handed Extended Sexta-Multiplex",
            "Multiplexing Two-handed Shower (Simple)",
            "Multiplexing Two-handed Shower",
            "Multiplexing Two-handed Shower (Hard)",
            "1-count Ultimate",
            "3-count Ultimate",
            "4-count (Every others)",
            "3-count (Waltz)",
            "2-count (Everies)",
            "1-count (Ultimate)",
            "6-count",
            "PPS",
            "PPSS (Desmond Tutu)",
            "PSPSP",
            "PPSPS (Bookends)",
            "PSPS PPSS (Tango)",
            "Ultimate Waltz",
            "4 Countdown (PSSSPSSPSPPSPSS)",
            "3 Countdown (PSSPSPPS)",
            "2-count",
            "4-count",
            "PPS",
            "PPS (Double vs Simple)",
            "2-count",
            "3-count",
            "PPS",
            "Feed",
            "Ultimate",
            "PPS",
            "Triangle",
            "Triangle PPS",
            "Tarim Triangle",
        )

        val query = """
            SELECT Trick.PATTERN, Hands.CODE as hands, Body.CODE as body, Trick.CUSTOM_DISPLAY as title, Trick.ID_TRICK, Trick.XML_LINE_NUMBER
            FROM Trick
            LEFT JOIN Hands ON Trick.ID_HANDS = Hands.ID_HANDS
            LEFT JOIN Body ON Trick.ID_BODY = Body.ID_BODY
        """.trimIndent()

        origDb.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val pattern = cursor.getString(0) ?: ""
                val hands = cursor.getString(1) ?: ""
                val body = cursor.getString(2) ?: ""
                val titleCustom = cursor.getString(3)
                val xmlLineNum = if (cursor.isNull(5)) -1 else cursor.getInt(5)
                val title = if (!titleCustom.isNullOrEmpty()) {
                    titleCustom
                } else if (xmlLineNum >= 0) {
                    defaultTrickNames.getOrNull(xmlLineNum) ?: ""
                } else {
                    ""
                }
                origTricks.add("$pattern|$hands|$body|$title")
            }
        }

        data class MigratedTrick(
            val display: String,
            val animString: String,
            val collections: List<CollectionData>
        )

        val migratedTricks = mutableListOf<MigratedTrick>()

        // 4. Iterate over FROM database and collect any tricks not in ORIG,
        // plus those in Starred or custom categories
        fromDb.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val pattern = cursor.getString(0) ?: ""
                val hands = cursor.getString(1) ?: ""
                val body = cursor.getString(2) ?: ""
                val titleCustom = cursor.getString(3)
                val trickId = cursor.getInt(4)

                val xmlLineNum = if (cursor.isNull(5)) -1 else cursor.getInt(5)
                val title = if (!titleCustom.isNullOrEmpty()) {
                    titleCustom
                } else if (xmlLineNum >= 0) {
                    defaultTrickNames.getOrNull(xmlLineNum) ?: ""
                } else {
                    ""
                }

                val signature = "$pattern|$hands|$body|$title"
                val trickColls = trickCollections[trickId] ?: emptyList()

                val isStarred = trickColls.any { it.isStarred }
                val isInCustomCollection = trickColls.any { !defaultNames.contains(it.name) }
                val isNewOrModified = !origTricks.contains(signature)

                if (isStarred || isInCustomCollection || isNewOrModified) {
                    val animParts = mutableListOf<String>()
                    if (pattern.isNotEmpty()) animParts.add("pattern=$pattern")
                    if (hands.isNotEmpty()) animParts.add("hands=$hands")
                    if (body.isNotEmpty()) animParts.add("body=$body")
                    if (title.isNotEmpty()) animParts.add("title=$title")

                    val animString = animParts.joinToString(";")
                    val display = title.ifEmpty { pattern }

                    migratedTricks.add(
                        MigratedTrick(display, animString, trickColls)
                    )
                }
            }
        }

        // 5. Group and construct the JmlPatternList
        val groupedTricks = mutableMapOf<String, MutableList<MigratedTrick>>()
        for (trick in migratedTricks) {
            val categoryName = when {
                trick.collections.any { it.isStarred } -> "Starred"
                trick.collections.isNotEmpty() -> trick.collections.minByOrNull { it.id }!!.name
                else -> "Unsorted"
            }
            groupedTricks.getOrPut(categoryName) { mutableListOf() }.add(trick)
        }

        val categoryNames = mutableListOf<String>()
        if (groupedTricks.containsKey("Starred")) categoryNames.add("Starred")

        collectionsMap.values.filter { !it.isStarred }.sortedBy { it.id }.forEach { col ->
            if (groupedTricks.containsKey(col.name) && !categoryNames.contains(col.name)) {
                categoryNames.add(col.name)
            }
        }

        if (groupedTricks.containsKey("Unsorted")) categoryNames.add("Unsorted")

        val jmlList = JmlPatternList()

        var firstCategory = true
        for (catName in categoryNames) {
            val tricks = groupedTricks[catName] ?: continue

            if (!firstCategory) {
                jmlList.addLine(
                    -1,
                    JmlPatternList.PatternRecord(" ", null, null, null, null, null, null)
                )
            }
            firstCategory = false

            jmlList.addLine(
                -1,
                JmlPatternList.PatternRecord("$catName:", null, null, null, null, null, null)
            )
            for (trick in tricks) {
                jmlList.addLine(
                    -1,
                    JmlPatternList.PatternRecord(
                        trick.display,
                        null,
                        "siteswap",
                        trick.animString,
                        null,
                        null,
                        null
                    )
                )
            }
        }

        val sb = java.lang.StringBuilder()
        jmlList.writeJml(sb)

        if (migratedTricks.isNotEmpty()) {
            println("=== Migrated patterns ===")
            println(sb.toString())
            return MigrationResult.Success(sb.toString(), migratedTricks.size)
        } else {
            println("=== No patterns found to migrate ===")
            return MigrationResult.NotFound
        }
    } catch (e: Exception) {
        println("Error during migration: ${e.message}")
        return MigrationResult.Error(e.message ?: "Unknown error")
    } finally {
        fromDb?.close()
        origDb?.close()
        if (migrationTest) {
            fromFile.delete()
        }
        origFile.delete()
    }
}
