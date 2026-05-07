package com.example.salesai.adapters.email;

public final class EmailMcpToolMappingTest {
    public static void main(String[] args) {
        new EmailMcpToolMappingTest().run();
    }

    void run() {
        testGmailToolName();
        testOutlookToolName();
        testFromConfigName();
        System.out.println("EmailMcpToolMappingTest: 3 passed");
    }

    void testGmailToolName() {
        assert "search_emails".equals(
            EmailMcpToolMapping.GMAIL.searchToolName());
    }

    void testOutlookToolName() {
        assert "list-messages".equals(
            EmailMcpToolMapping.OUTLOOK.searchToolName());
    }

    void testFromConfigName() {
        assert EmailMcpToolMapping.fromConfigName("gmail")
            == EmailMcpToolMapping.GMAIL;
        assert EmailMcpToolMapping.fromConfigName("outlook")
            == EmailMcpToolMapping.OUTLOOK;
        try {
            EmailMcpToolMapping.fromConfigName("unknown");
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException ok) {}
    }
}
