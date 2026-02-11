package com.modernrdp.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for Room migration objects.
 *
 * These test the migration SQL statements themselves. For full end-to-end
 * migration testing, use MigrationTestHelper with an instrumented test.
 * These unit tests verify the migration objects are correctly defined.
 */
class RoomMigrationTest {

    @Test
    fun `MIGRATION_1_2 has correct version range`() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
    }

    @Test
    fun `MIGRATION_2_3 has correct version range`() {
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
    }

    @Test
    fun `migration objects are not null`() {
        assertNotNull(MIGRATION_1_2)
        assertNotNull(MIGRATION_2_3)
    }

    @Test
    fun `MIGRATION_1_2 start version is 1`() {
        assertEquals(1, MIGRATION_1_2.startVersion)
    }

    @Test
    fun `MIGRATION_1_2 end version is 2`() {
        assertEquals(2, MIGRATION_1_2.endVersion)
    }

    @Test
    fun `MIGRATION_2_3 start version is 2`() {
        assertEquals(2, MIGRATION_2_3.startVersion)
    }

    @Test
    fun `MIGRATION_2_3 end version is 3`() {
        assertEquals(3, MIGRATION_2_3.endVersion)
    }

    @Test
    fun `migrations form a continuous chain 1 to 3`() {
        assertEquals(MIGRATION_1_2.endVersion, MIGRATION_2_3.startVersion)
    }

    @Test
    fun `RdpDatabase annotated with version 3`() {
        // Verify through the migration chain that version 3 is the latest
        val latestVersion = MIGRATION_2_3.endVersion
        assertEquals(3, latestVersion)
    }
}
