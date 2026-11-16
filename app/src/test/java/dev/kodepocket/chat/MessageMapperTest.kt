package dev.kodepocket.chat

import dev.kodepocket.server.ServerJson
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMapperTest {

    private fun objectOf(json: String): JsonObject =
        ServerJson.parseToJsonElement(json) as JsonObject

    @Test
    fun `server order is newest first so the mapper reverses it`() {
        val messages = listOf(
            objectOf("""{"id":"msg_2","time":{"created":200},"type":"assistant","content":[]}"""),
            objectOf("""{"id":"msg_1","time":{"created":100},"type":"user","text":"hello"}"""),
        )

        val items = MessageMapper.fromServer(messages)

        assertEquals(2, items.size)
        assertEquals("msg_1", items[0].id)
        assertEquals("hello", (items[0] as UserItem).text)
        assertEquals("msg_2", items[1].id)
    }

    @Test
    fun `assistant content becomes text, reasoning and tools`() {
        val message = objectOf(
            """
            {
              "id": "msg_a",
              "time": {"created": 10},
              "type": "assistant",
              "agent": "build",
              "model": {"id": "big-pickle", "providerID": "opencode"},
              "content": [
                {"type": "reasoning", "text": "plan"},
                {"type": "text", "text": "done"},
                {
                  "type": "tool",
                  "id": "call_1",
                  "name": "shell",
                  "executed": true,
                  "state": {
                    "status": "completed",
                    "input": {"command": "echo hi"},
                    "content": [{"type": "text", "text": "hi"}]
                  },
                  "time": {"created": 11}
                }
              ]
            }
            """.trimIndent(),
        )

        val item = MessageMapper.fromServer(listOf(message)).single() as AssistantItem

        assertEquals("big-pickle", item.model)
        assertEquals("done", item.text)
        assertEquals(3, item.parts.size)
        assertEquals("plan", (item.parts[0] as ReasoningPart).text)
        assertEquals("done", (item.parts[1] as TextPart).text)
        val tool = (item.parts[2] as ToolPart).tool
        assertEquals("shell", tool.name)
        assertEquals(ToolStatus.Completed, tool.status)
        assertTrue(tool.input.contains("echo hi"))
        assertEquals("hi", tool.output)
    }

    @Test
    fun `failed tool keeps its error message`() {
        val message = objectOf(
            """
            {
              "id": "msg_b",
              "time": {"created": 10},
              "type": "assistant",
              "content": [{
                "type": "tool",
                "id": "call_2",
                "name": "shell",
                "state": {
                  "status": "error",
                  "input": {"command": "boom"},
                  "error": {"type": "unknown", "message": "Command failed"}
                },
                "time": {"created": 11}
              }]
            }
            """.trimIndent(),
        )

        val item = MessageMapper.fromServer(listOf(message)).single() as AssistantItem
        val tool = (item.parts.single() as ToolPart).tool

        assertEquals(ToolStatus.Failed, tool.status)
        assertEquals("Command failed", tool.output)
    }

    @Test
    fun `a turn is in progress until the newest assistant message completes`() {
        val running = objectOf(
            """{"id":"msg_a","time":{"created":10},"type":"assistant","content":[]}""",
        )
        val finished = objectOf(
            """{"id":"msg_a","time":{"created":10,"completed":20},"type":"assistant","content":[]}""",
        )
        val idle = objectOf(
            """{"id":"msg_i","time":{"created":21},"type":"idle","outcome":"succeeded"}""",
        )

        assertTrue(MessageMapper.inProgress(listOf(running)))
        assertFalse(MessageMapper.inProgress(listOf(finished)))
        assertFalse(MessageMapper.inProgress(listOf(idle)))
        assertFalse(MessageMapper.inProgress(emptyList()))
    }
}
