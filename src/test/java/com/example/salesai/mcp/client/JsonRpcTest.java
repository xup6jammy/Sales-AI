package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.util.Map;

public final class JsonRpcTest {
    public static void main(String[] args) {
        new JsonRpcTest().run();
    }

    void run() {
        testBuildRequestEnvelope();
        testParseSuccessResponse();
        testParseErrorResponse();
        System.out.println("JsonRpcTest: 3 passed");
    }

    void testBuildRequestEnvelope() {
        String line = JsonRpc.request(7, "tools/list", Map.of());
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(line));
        assert "2.0".equals(root.get("jsonrpc"));
        assert ((Number) root.get("id")).intValue() == 7;
        assert "tools/list".equals(root.get("method"));
    }

    void testParseSuccessResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"x\":1}}";
        JsonRpc.Response r = JsonRpc.parseResponse(body);
        assert ((Number) r.id()).intValue() == 3;
        assert r.error() == null;
        assert MiniJson.asObject(r.result()).get("x") instanceof Number;
    }

    void testParseErrorResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,"
                + "\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
        JsonRpc.Response r = JsonRpc.parseResponse(body);
        assert r.result() == null;
        assert r.error() != null;
        assert r.error().code() == -32601;
        assert "Method not found".equals(r.error().message());
    }
}
