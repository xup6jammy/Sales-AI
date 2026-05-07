package com.example.salesai.adapters.email;

import com.example.salesai.domain.EmailThread;
import com.example.salesai.mcp.client.McpClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McpEmailThreadAdapterTest {
    public static void main(String[] args) throws Exception {
        new McpEmailThreadAdapterTest().run();
    }

    void run() throws Exception {
        testReturnsEmptyWhenMcpRespondsWithNoMessages();
        testParsesSingleMessageThread();
        System.out.println("McpEmailThreadAdapterTest: 2 passed");
    }

    void testReturnsEmptyWhenMcpRespondsWithNoMessages() {
        FakeMcpClient mc = new FakeMcpClient("[]");
        McpEmailThreadAdapter a = new McpEmailThreadAdapter(
            mc, EmailMcpToolMapping.GMAIL);
        Optional<EmailThread> t = a.loadLatestForCustomer("nobody@example.com");
        assert t.isEmpty();
    }

    void testParsesSingleMessageThread() {
        String mcpReply = """
            [
              {
                "thread_id": "thr-001",
                "subject": "Order ETA?",
                "messages": [
                  {
                    "message_id": "m-1",
                    "from": "alice@acme.com",
                    "to": ["support@vendor.com"],
                    "sent_at": "2026-05-07T10:30:00Z",
                    "direction": "INBOUND",
                    "body": "When does my order ship?"
                  }
                ]
              }
            ]
            """;
        FakeMcpClient mc = new FakeMcpClient(mcpReply);
        McpEmailThreadAdapter a = new McpEmailThreadAdapter(
            mc, EmailMcpToolMapping.GMAIL);
        Optional<EmailThread> t = a.loadLatestForCustomer("alice@acme.com");
        assert t.isPresent();
        assert "thr-001".equals(t.get().threadId());
        assert t.get().messages().size() == 1;
        assert "m-1".equals(t.get().messages().get(0).messageId());
        assert "INBOUND".equals(t.get().messages().get(0).direction());
    }

    /** Test double — subclasses McpClient, overrides only what the adapter calls. */
    static class FakeMcpClient extends McpClient {
        private final String reply;
        FakeMcpClient(String reply) {
            super();
            this.reply = reply;
        }
        @Override
        public String callToolText(String name, Map<String, Object> args, long timeoutMs) {
            return reply;
        }
        @Override
        public List<String> listToolNames(long timeoutMs) {
            return List.of("search_emails", "read_email");
        }
        @Override
        public boolean isInitialized() { return true; }
        @Override
        public void close() { /* no-op for tests */ }
    }
}
