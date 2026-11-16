package dev.kodepocket.chat

import dev.kodepocket.server.OpenCodeEvent
import dev.kodepocket.server.ServerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventReducerTest {

    private var state = ChatState(sessionId = "ses_1", streaming = true)

    private fun apply(type: String, data: String): ChatState {
        val event = ServerJson.decodeFromString(
            OpenCodeEvent.serializer(),
            """{"id":"evt_1","created":1000,"type":"$type","data":$data}""",
        )
        state = EventReducer.reduce(state, event)
        return state
    }

    @Test
    fun `text deltas accumulate into one assistant message`() {
        apply("session.text.started", """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0}""")
        apply("session.text.delta", """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0,"delta":"hel"}""")
        val result = apply(
            "session.text.delta",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0,"delta":"lo"}""",
        )

        val assistant = result.messages.single() as AssistantItem
        assertEquals("hello", assistant.text)
        assertTrue(result.streaming)
    }

    @Test
    fun `ended text is authoritative even when a delta was missed`() {
        apply("session.text.started", """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0}""")
        apply("session.text.delta", """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0,"delta":"par"}""")
        val result = apply(
            "session.text.ended",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0,"text":"partial then fixed"}""",
        )

        assertEquals("partial then fixed", (result.messages.single() as AssistantItem).text)
    }

    @Test
    fun `tool lifecycle moves from running to completed`() {
        apply(
            "session.tool.input.started",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","id":"call_1","name":"shell"}""",
        )
        apply(
            "session.tool.called",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","id":"call_1","input":{"command":"echo hi"},"executed":false}""",
        )
        val result = apply(
            "session.tool.success",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","id":"call_1","content":[{"type":"text","text":"hi"}]}""",
        )

        val tool = (result.messages.single() as AssistantItem).parts.single() as ToolPart
        assertEquals("shell", tool.tool.name)
        assertEquals(ToolStatus.Completed, tool.tool.status)
        assertEquals("hi", tool.tool.output)
        assertTrue(tool.tool.input.contains("echo hi"))
    }

    @Test
    fun `failed tool records the runtime error`() {
        apply(
            "session.tool.input.started",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","id":"call_1","name":"shell"}""",
        )
        val result = apply(
            "session.tool.failed",
            """{"sessionID":"ses_1","assistantMessageID":"msg_a","id":"call_1","error":{"type":"unknown","message":"Command failed"},"executed":false}""",
        )

        val tool = (result.messages.single() as AssistantItem).parts.single() as ToolPart
        assertEquals(ToolStatus.Failed, tool.tool.status)
        assertEquals("Command failed", tool.tool.output)
    }

    @Test
    fun `permission asked then replied updates the queue`() {
        val asked = apply(
            "permission.asked",
            """{"sessionID":"ses_1","id":"per_1","action":"shell","resources":["echo hi"],"message":"Run echo hi"}""",
        )
        assertEquals(1, asked.permissions.size)
        assertEquals("per_1", asked.pendingPermission?.id)

        val replied = apply("permission.replied", """{"sessionID":"ses_1","id":"per_1"}""")
        assertTrue(replied.permissions.isEmpty())
    }

    @Test
    fun `idle ends streaming`() {
        apply("session.text.started", """{"sessionID":"ses_1","assistantMessageID":"msg_a","ordinal":0}""")
        val result = apply("session.idle", """{"sessionID":"ses_1"}""")

        assertFalse(result.streaming)
    }

    @Test
    fun `events for another session do not leak into the open chat`() {
        val result = apply(
            "session.text.delta",
            """{"sessionID":"ses_2","assistantMessageID":"msg_other","ordinal":0,"delta":"nope"}""",
        )

        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun `a failed execution becomes a visible notice`() {
        val result = apply(
            "session.execution.failed",
            """{"sessionID":"ses_1","error":{"type":"unknown","message":"Out of credits"}}""",
        )

        val notice = result.messages.single() as NoticeItem
        assertEquals("Out of credits", notice.text)
        assertTrue(notice.isError)
        assertFalse(result.streaming)
    }
}
