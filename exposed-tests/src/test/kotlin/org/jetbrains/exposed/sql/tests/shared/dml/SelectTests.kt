package org.jetbrains.exposed.sql.tests.shared.dml

import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.crypt.Algorithms
import org.jetbrains.exposed.crypt.encryptedBinary
import org.jetbrains.exposed.crypt.encryptedVarchar
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTests
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class SelectTests : DatabaseTestsBase() {
    @Test
    fun testMigrationToNewQueryDSL() {
        withCitiesAndUsers { cities, _, _ ->
            val logCaptor = LogCaptor.forName(exposedLogger.name)
            logCaptor.setLogLevelToDebug()

            // old query dsl
            cities.slice(cities.name).select { cities.name eq "andrey" }.toList()
            cities.slice(cities.name).select(Op.TRUE).toList()
            cities.slice(cities.name).selectAll().toList()

            cities.select { cities.name eq "andrey" }.toList()
            cities.select(Op.TRUE).toList()
            cities.selectAll().toList()

            cities.slice(cities.name).selectBatched(50) { cities.name eq "andrey" }.toList()
            cities.slice(cities.name).selectAllBatched(50).toList()
            cities.selectBatched(50) { cities.name eq "andrey" }.toList()
            cities.selectAllBatched(50).toList()

            val originalQuery1 = cities.select { cities.name eq "andrey" }.also { it.toList() }
            originalQuery1.adjustSlice { slice(cities.name) }.toList()

            val sqlLoggedWithOldDSL = logCaptor.debugLogs.toList()
            logCaptor.clearLogs()

            // new query dsl
            cities.select(cities.name).where { cities.name eq "andrey" }.toList()
            cities.select(cities.name).where(Op.TRUE).toList()
            cities.select(cities.name).toList()

            cities.selectAll().where { cities.name eq "andrey" }.toList()
            cities.selectAll().where(Op.TRUE).toList()
            cities.selectAll().toList()

            cities.select(cities.name).where { cities.name eq "andrey" }.fetchBatchedResults(50).toList()
            cities.select(cities.name).fetchBatchedResults(50).toList()
            cities.selectAll().where { cities.name eq "andrey" }.fetchBatchedResults(50).toList()
            cities.selectAll().fetchBatchedResults(50).toList()

            val originalQuery2 = cities.selectAll().where { cities.name eq "andrey" }.also { it.toList() }
            originalQuery2.adjustSelect { select(cities.name) }.toList()

            val sqlLoggedWithNewDSL = logCaptor.debugLogs.toList()
            logCaptor.clearLogs()
            logCaptor.resetLogLevel()
            logCaptor.close()

            assertContentEquals(sqlLoggedWithOldDSL, sqlLoggedWithNewDSL)
        }
    }

    @Test
    fun testSelect() {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { users.id.eq("andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectAnd() {
        withCitiesAndUsers { cities, users, userData ->
            users.selectAll().where { users.id.eq("andrey") and users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectOr() {
        withCitiesAndUsers { cities, users, userData ->
            users.selectAll().where { users.id.eq("andrey") or users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectNot() {
        withCitiesAndUsers { cities, users, userData ->
            users.selectAll().where { not(users.id.eq("andrey")) }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                if (userId == "andrey") {
                    error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSizedIterable() {
        withCitiesAndUsers { cities, users, userData ->
            assertEquals(false, cities.selectAll().empty())
            assertEquals(true, cities.selectAll().where { cities.name eq "Qwertt" }.empty())
            assertEquals(0L, cities.selectAll().where { cities.name eq "Qwertt" }.count())
            assertEquals(3L, cities.selectAll().count())
            val cityID: Int? = null
            assertEquals(2L, users.selectAll().where { users.cityId eq cityID }.count())
        }
    }

    @Test
    fun testInList01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().where {
                users.id inList listOf("andrey", "alex")
            }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testInList02() {
        withCitiesAndUsers { cities, users, userData ->
            val cityIds = cities.selectAll().map { it[cities.id] }.take(2)
            val r = cities.selectAll().where { cities.id inList cityIds }

            assertEquals(2L, r.count())
        }
    }

    @Test
    fun testInList03() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER)) { _, users, _ ->
            val r = users.selectAll().where {
                users.id to users.name inList listOf("andrey" to "Andrey", "alex" to "Alex")
            }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testInList04() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) { _, users, _ ->
            val r = users.selectAll().where { users.id to users.name inList listOf("andrey" to "Andrey") }.toList()

            assertEquals(1, r.size)
            assertEquals("Andrey", r[0][users.name])
        }
    }

    @Test
    fun testInList05() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) { _, users, _ ->
            val r = users.selectAll().where { users.id to users.name inList emptyList() }.toList()

            assertEquals(0, r.size)
        }
    }

    @Test
    fun testInList06() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) { _, users, _ ->
            val r = users.selectAll().where { users.id to users.name notInList emptyList() }.toList()

            assertEquals(users.selectAll().count().toInt(), r.size)
        }
    }

    @Test
    fun testInList07() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) { _, users, _ ->
            val r = users.selectAll().where {
                Triple(users.id, users.name, users.cityId) notInList listOf(Triple("alex", "Alex", null))
            }.toList()

            assertEquals(users.selectAll().count().toInt() - 1, r.size)
        }
    }

    @Test
    fun testInList08() {
        withTables(EntityTests.Posts) {
            val board1 = EntityTests.Board.new {
                this.name = "Board1"
            }

            val post1 = EntityTests.Post.new {
                this.board = board1
            }

            EntityTests.Post.new {
                category = EntityTests.Category.new { title = "Category1" }
            }

            val result = EntityTests.Posts.selectAll().where {
                EntityTests.Posts.board inList listOf(board1.id)
            }.singleOrNull()?.get(EntityTests.Posts.id)
            assertEquals(post1.id, result)
        }
    }

    @Test
    fun testInSubQuery01() {
        withCitiesAndUsers { cities, _, _ ->
            val r = cities.selectAll().where { cities.id inSubQuery cities.select(cities.id).where { cities.id eq 2 } }
            assertEquals(1L, r.count())
        }
    }

    @Test
    fun testNotInSubQueryNoData() {
        withCitiesAndUsers { cities, _, _ ->
            val r = cities.selectAll().where { cities.id notInSubQuery cities.select(cities.id) }
            // no data since all ids are selected
            assertEquals(0L, r.count())
        }
    }

    @Test
    fun testNotInSubQuery() {
        withCitiesAndUsers { cities, _, _ ->
            val cityId = 2
            val r = cities.selectAll().where {
                cities.id notInSubQuery cities.select(cities.id).where { cities.id eq cityId }
            }.map { it[cities.id] }.sorted()
            assertEquals(2, r.size)
            // only 2 cities with id 1 and 2 respectively
            assertEquals(1, r[0])
            assertEquals(3, r[1])
            // there is no city with id=2
            assertNull(r.find { it == cityId })
        }
    }

    @Test
    fun testSelectDistinct() {
        val tbl = DMLTestsData.Cities
        withTables(tbl) {
            tbl.insert { it[tbl.name] = "test" }
            tbl.insert { it[tbl.name] = "test" }

            assertEquals(2L, tbl.selectAll().count())
            assertEquals(2L, tbl.selectAll().withDistinct().count())
            assertEquals(1L, tbl.select(tbl.name).withDistinct().count())
            assertEquals("test", tbl.select(tbl.name).withDistinct().single()[tbl.name])
        }
    }

    @Test
    fun testCompoundOp() {
        withCitiesAndUsers { _, users, _ ->
            val allUsers = setOf(
                "Andrey",
                "Sergey",
                "Eugene",
                "Alex",
                "Something"
            )
            val orOp = allUsers.map { Op.build { users.name eq it } }.compoundOr()
            val userNamesOr = users.selectAll().where(orOp).map { it[users.name] }.toSet()
            assertEquals(allUsers, userNamesOr)

            val andOp = allUsers.map { Op.build { users.name eq it } }.compoundAnd()
            assertEquals(0L, users.selectAll().where(andOp).count())
        }
    }

    @Test
    fun `test select on nullable reference column`() {
        val firstTable = object : IntIdTable("firstTable") {}
        val secondTable = object : IntIdTable("secondTable") {
            val firstOpt = optReference("first", firstTable)
        }

        withTables(firstTable, secondTable) {
            val firstId = firstTable.insertAndGetId { }
            secondTable.insert {
                it[firstOpt] = firstId
            }
            secondTable.insert { }

            assertEquals(2L, secondTable.selectAll().count())
            assertEquals(1, secondTable.selectAll().where { secondTable.firstOpt eq firstId.value }.toList().size)
            assertEquals(0, secondTable.selectAll().where { secondTable.firstOpt neq firstId.value }.toList().size)
            assertEquals(1, secondTable.selectAll().where { secondTable.firstOpt eq null }.count())
            assertEquals(1, secondTable.selectAll().where { secondTable.firstOpt neq null }.count())
        }
    }

    @Test
    fun `test that column length check is not affects select queries`() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = varchar("name", 10)
        }

        withTables(stringTable) {
            stringTable.insert {
                it[name] = "TestName"
            }
            assertEquals(1, stringTable.selectAll().where { stringTable.name eq "TestName" }.count())

            val veryLongString = "1".repeat(255)
            assertEquals(0, stringTable.selectAll().where { stringTable.name eq veryLongString }.count())
        }
    }

    @Test
    fun testEncryptedColumnTypeWithAString() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = encryptedVarchar("name", 80, Algorithms.AES_256_PBE_CBC("passwd", "5c0744940b5c369b"))
            val city = encryptedBinary("city", 80, Algorithms.AES_256_PBE_GCM("passwd", "5c0744940b5c369b"))
            val address = encryptedVarchar("address", 100, Algorithms.BLOW_FISH("key"))
            val age = encryptedVarchar("age", 100, Algorithms.TRIPLE_DES("1".repeat(24)))
        }

        withTables(stringTable) {
            val id1 = stringTable.insertAndGetId {
                it[name] = "testName"
                it[city] = "testCity".toByteArray()
                it[address] = "testAddress"
                it[age] = "testAge"
            }

            assertEquals(1L, stringTable.selectAll().count())

            assertEquals("testName", stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.name])
            assertEquals("testCity", String(stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.city]))
            assertEquals("testAddress", stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.address])
            assertEquals("testAge", stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.age])
        }
    }
}
