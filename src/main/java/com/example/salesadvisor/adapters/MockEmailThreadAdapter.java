package com.example.salesadvisor.adapters;

import com.example.salesadvisor.domain.EmailMessage;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.ports.EmailThreadPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Email thread adapter that reads a single thread from a JSON file
 * on disk. The thread is loaded once at construction time. The match
 * against {@code customerEmail} is case-insensitive.
 */
public final class MockEmailThreadAdapter implements EmailThreadPort {

    private final EmailThread thread;

    public MockEmailThreadAdapter(Path jsonPath) {
        this.thread = loadThread(jsonPath);
    }

    private static EmailThread loadThread(Path jsonPath) {
        String text;
        try {
            text = Files.readString(jsonPath, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new UncheckedIOException(
                    "Failed to read email thread JSON at " + jsonPath, ioe);
        }
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(text));

        List<EmailMessage> messages = new ArrayList<>();
        for (Map<String, Object> m : MiniJson.asObjectList(root.get("messages"))) {
            messages.add(new EmailMessage(
                    MiniJson.asString(m.get("messageId")),
                    MiniJson.asString(m.get("from")),
                    MiniJson.asStringList(m.get("to")),
                    MiniJson.asString(m.get("sentAt")),
                    MiniJson.asString(m.get("direction")),
                    MiniJson.asString(m.get("body"))
            ));
        }

        return new EmailThread(
                MiniJson.asString(root.get("threadId")),
                MiniJson.asString(root.get("subject")),
                MiniJson.asString(root.get("customerEmail")),
                messages
        );
    }

    @Override
    public Optional<EmailThread> loadLatestForCustomer(String customerEmail) {
        if (thread == null || customerEmail == null) {
            return Optional.empty();
        }
        if (thread.customerEmail() != null
                && thread.customerEmail().equalsIgnoreCase(customerEmail)) {
            return Optional.of(thread);
        }
        return Optional.empty();
    }
}
