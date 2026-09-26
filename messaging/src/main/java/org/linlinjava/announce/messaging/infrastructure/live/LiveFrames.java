package org.linlinjava.announce.messaging.infrastructure.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.messaging.application.live.BulletinLive;
import org.linlinjava.announce.messaging.application.live.ConversationStatusLive;
import org.linlinjava.announce.messaging.application.live.MessageLive;
import org.linlinjava.announce.messaging.domain.conversation.PostResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JSON frames a WebSocket client receives. Every frame carries a {@code type}: {@code message},
 * {@code status}, {@code bulletin} (from the live topic), {@code ack}, {@code error}, {@code pong} (answers to the
 * client's own frames). See {@code doc/messaging-context.md}.
 */
@Component
public class LiveFrames {

    public static final String MESSAGE = "message";
    public static final String STATUS = "status";
    public static final String BULLETIN = "bulletin";
    public static final String ACK = "ack";
    public static final String ERROR = "error";
    public static final String PONG = "pong";

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public LiveFrames(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String message(MessageLive live) {
        return frame(MESSAGE, live);
    }

    public String status(ConversationStatusLive live) {
        return frame(STATUS, live);
    }

    public String bulletin(BulletinLive live) {
        return frame(BULLETIN, live);
    }

    /** Acknowledges a frame the client sent; {@code action} is the client's frame type. */
    public String ack(String action, PostResult result) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", ACK);
        frame.put("action", action);
        if (result != null) {
            frame.putAll(objectMapper.convertValue(result, MAP));
        }
        return write(frame);
    }

    public String error(String action, String clientMsgId, int status, String code, String detail) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", ERROR);
        frame.put("action", action);
        if (clientMsgId != null) {
            frame.put("clientMsgId", clientMsgId);
        }
        frame.put("status", status);
        frame.put("code", code);
        frame.put("detail", detail);
        return write(frame);
    }

    public String pong() {
        return write(Map.of("type", PONG));
    }

    private String frame(String type, Object payload) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        frame.putAll(objectMapper.convertValue(payload, MAP));
        return write(frame);
    }

    private String write(Map<String, Object> frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise live frame", e);
        }
    }
}
