package dev.cyclone.cloak.root

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

class PrivilegedSessionTest {
    /** Simulated su transport: framing and deadlines are real; approval outcomes are controlled. */
    private class Shell(private val answer: (Int) -> Pair<String, Int>?, private val delayAt: Int = -1) : Process() {
        private val bytes = LinkedBlockingQueue<Int>()
        private val input = object : InputStream() {
            override fun read(): Int = bytes.take()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                val first = read()
                if (first == -1) return -1
                buffer[offset] = first.toByte()
                var count = 1
                while (count < length) {
                    val next = bytes.poll() ?: break
                    buffer[offset + count++] = next.toByte()
                }
                return count
            }
        }
        private val output = object : OutputStream() {
            override fun write(value: Int) { bytes.put(value and 255) }
            override fun close() { bytes.offer(-1) }
        }
        val commands = mutableListOf<String>()
        var closed = false
        private val stdin = object : ByteArrayOutputStream() {
            override fun flush() {
                val command = toString("UTF-8")
                reset()
                if (command.isEmpty()) return
                if (command == "exit\n") { closed = true; return }
                commands.add(command)
                val index = commands.size - 1
                val reply = answer(index) ?: return
                val marker = Regex("CLOAK_SESSION_[a-f0-9]+:").find(command)!!.value
                val deliver = {
                    output.write((reply.first + "\n" + marker + reply.second + "\n").toByteArray())
                    output.flush()
                }
                if (index == delayAt) Thread { Thread.sleep(1200); deliver() }.apply { isDaemon = true; start() }
                else deliver()
            }
        }
        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream() = input
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun destroy() { closed = true; output.close() }
    }

    @Test fun sevenCommandsUseOneSessionAndPreserveOutputAndExitCodes() {
        var starts = 0
        val shell = Shell({ if (it == 0) "CLOAK_PRIVILEGE_OK" to 0 else "CLOAK_DOCTOR=READY" to 0 })
        val result = RootSessions.batch(PrivilegedSession(startProcess = { starts++; shell })) {
            repeat(7) {
                val reply = RootSessions.current()!!.execute("set -eu; echo CLOAK_DOCTOR=READY; exit 0")
                assertEquals("CLOAK_DOCTOR=READY", reply.output)
                assertEquals(0, reply.exitCode)
            }
        }
        assertTrue(result.published)
        assertEquals(1, starts)
        assertEquals(8, shell.commands.size) // one preflight plus seven commands
        assertTrue(shell.closed)
        assertNull(RootSessions.current())
    }

    @Test fun deniedPreflightDoesNotTouchBindings() {
        var writes = 0
        val shell = Shell({ "Permission denied" to 1 })
        val result = RootSessions.batch(PrivilegedSession(startProcess = { shell })) { writes++ }
        assertEquals(RootDoctorCode.ROOT_REQUIRED, result.code)
        assertEquals(0, writes)
        assertTrue(result.message.contains("Magisk"))
    }

    @Test fun preflightTimeoutAbortsBeforeWorkAndExplainsApproval() {
        var writes = 0
        val result = RootSessions.batch(PrivilegedSession(budgetMs = 50, startProcess = { Shell({ null }) })) { writes++ }
        assertEquals(RootDoctorCode.ROOT_TIMEOUT, result.code)
        assertEquals(0, writes)
        assertEquals("Approval timed out", result.title)
        assertTrue(result.message.contains("this Android profile"))
    }

    @Test fun midBatchWaitResumesInSameSessionAndRunsRemainingCommands() {
        var waiting = 0
        var resumed = 0
        val shell = Shell({ if (it == 0) "CLOAK_PRIVILEGE_OK" to 0 else "done" to 0 }, delayAt = 2)
        RootSessions.batch(PrivilegedSession(waiting = { waiting++ }, resumed = { resumed++ }, startProcess = { shell })) {
            repeat(3) { assertEquals(0, RootSessions.current()!!.execute("echo done").exitCode) }
        }
        assertEquals(1, waiting)
        assertEquals(1, resumed)
        assertEquals(4, shell.commands.size)
    }

    @Test fun deadlineIsSharedAndDoesNotResetForNextCommand() {
        val shell = Shell({ if (it == 0) "CLOAK_PRIVILEGE_OK" to 0 else null })
        PrivilegedSession(budgetMs = 100, startProcess = { shell }).use { session ->
            assertTrue(session.preflight().published)
            assertTrue(session.execute("blocked").timedOut)
            assertTrue(session.execute("must not send").timedOut)
            assertEquals(2, shell.commands.size)
        }
    }

    @Test fun failedScriptDoesNotKillParentSessionAndOutputIsBounded() {
        val shell = Shell({ "0123456789" to if (it == 0) 7 else 0 })
        PrivilegedSession(startProcess = { shell }).use {
            assertEquals(7, it.execute("exit 7", maxOutput = 4).exitCode)
            assertEquals("0123", it.execute("echo again", maxOutput = 4).output)
        }
    }

    @Test fun exceptionsClearScopeAndCloseSession() {
        val shell = Shell({ "CLOAK_PRIVILEGE_OK" to 0 })
        try {
            RootSessions.batch(PrivilegedSession(startProcess = { shell })) { error("test failure") }
            fail("Expected work failure")
        } catch (_: IllegalStateException) { }
        assertNull(RootSessions.current())
        assertTrue(shell.closed)
    }

    @Test fun secondaryUserHintOnlyAppearsForProfileCopy() {
        assertNull(RootSessions.profileHint(0))
        assertTrue(RootSessions.profileHint(16)!!.contains("grant until reboot"))
    }

    @Test fun realShellPreservesQuotesMultilineOutputAndSurvivesScriptExit() {
        val wsl = System.getenv("CLOAK_TEST_WSL") == "1"
        assumeTrue(wsl || File("/bin/sh").canExecute())
        PrivilegedSession(startProcess = {
            ProcessBuilder(if (wsl) listOf("wsl", "--exec", "sh") else listOf("/bin/sh"))
                .redirectErrorStream(true).start()
        }).use { session ->
            assertEquals(17, session.execute("set -eu; echo failed; exit 17").exitCode)
            val result = session.execute("set -eu; printf '%s\\n' \"one's value\" 'second line'; exit 0")
            assertEquals(0, result.exitCode)
            assertEquals("one's value\nsecond line", result.output)
            assertFalse(result.timedOut)
        }
    }
    @Test fun grantedPublicationOutlivesApprovalBudgetWithoutApprovalNotifications() {
        var waiting = 0
        val shell = Shell({ if (it == 0) "CLOAK_PRIVILEGE_OK" to 0 else "published" to 0 }, delayAt = 1)
        PrivilegedSession(budgetMs = 500, waiting = { waiting++ }, startProcess = { shell }).use { session ->
            assertTrue(session.preflight().published)
            val result = session.executeWithBudget("publish", 2500)
            assertFalse(result.timedOut)
            assertEquals("published", result.output)
            assertEquals(0, session.execute("next command").exitCode)
        }
        assertEquals(0, waiting)
        assertEquals(3, shell.commands.size)
    }

    @Test fun publicationCannotReviveAnExpiredSession() {
        val shell = Shell({ if (it == 0) "CLOAK_PRIVILEGE_OK" to 0 else null })
        PrivilegedSession(budgetMs = 200, startProcess = { shell }).use { session ->
            assertTrue(session.preflight().published)
            assertTrue(session.executeWithBudget("publish", 300).timedOut)
            assertTrue(session.executeWithBudget("must not send", 120000).timedOut)
            assertEquals(2, shell.commands.size)
        }
    }

    @Test fun publicationBudgetIsAnchoredToGrantAndNotResetByEachCommand() {
        val shell = Shell({ if (it == 0) "CLOAK_PRIVILEGE_OK" to 0 else "done" to 0 })
        PrivilegedSession(budgetMs = 50, startProcess = { shell }).use { session ->
            assertTrue(session.preflight().published)
            assertEquals(0, session.executeWithBudget("first", 200).exitCode)
            Thread.sleep(250)
            assertTrue(session.executeWithBudget("must not send", 200).timedOut)
            assertEquals(2, shell.commands.size)
        }
    }

}
