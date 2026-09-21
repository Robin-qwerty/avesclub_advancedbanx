package net.hnt8.advancedban.redis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JSON envelope for Avesban Redis pub/sub. Keep this class free of plugin types
 * so BackendLink can shade it without pulling the rest of core.
 */
public final class RedisEnvelope {

    public static final String TYPE_EXECUTE_COMMAND = "EXECUTE_COMMAND";
    public static final String TYPE_VOICE_PERM = "VOICE_PERM";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_ERROR = "ERROR";

    public static final String ACTION_SET = "SET";
    public static final String ACTION_UNSET = "UNSET";

    public String id;
    public String type;
    public long ts;
    public String token;
    public String source;
    public String operator;
    public String command;
    public String message;
    public Boolean ok;
    public List<String> args = new ArrayList<String>();
    public JsonObject payload = new JsonObject();

    public static RedisEnvelope parse(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        RedisEnvelope envelope = new RedisEnvelope();
        envelope.id = str(object, "id");
        envelope.type = str(object, "type");
        envelope.ts = object.has("ts") && object.get("ts").isJsonPrimitive()
                ? object.get("ts").getAsLong()
                : 0L;
        envelope.token = str(object, "token");
        envelope.source = str(object, "source");
        envelope.operator = str(object, "operator");
        envelope.command = str(object, "command");
        envelope.message = str(object, "message");
        if (object.has("ok") && object.get("ok").isJsonPrimitive()) {
            envelope.ok = object.get("ok").getAsBoolean();
        }
        envelope.args = stringList(object.get("args"));
        if (object.has("payload") && object.get("payload").isJsonObject()) {
            envelope.payload = object.getAsJsonObject("payload");
        }
        if (envelope.operator == null) {
            envelope.operator = str(envelope.payload, "operator");
        }
        if (envelope.command == null) {
            envelope.command = str(envelope.payload, "command");
        }
        if (envelope.args.isEmpty()) {
            envelope.args = stringList(envelope.payload.get("args"));
        }
        return envelope;
    }

    public static RedisEnvelope create(String type, String token, String source) {
        RedisEnvelope envelope = new RedisEnvelope();
        envelope.id = UUID.randomUUID().toString();
        envelope.type = type;
        envelope.ts = System.currentTimeMillis();
        envelope.token = token;
        envelope.source = source;
        return envelope;
    }

    public String toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("type", type);
        object.addProperty("ts", ts);
        object.addProperty("token", token);
        object.addProperty("source", source);
        if (operator != null) {
            object.addProperty("operator", operator);
        }
        if (command != null) {
            object.addProperty("command", command);
        }
        if (message != null) {
            object.addProperty("message", message);
        }
        if (ok != null) {
            object.addProperty("ok", ok);
        }
        if (args != null && !args.isEmpty()) {
            JsonArray array = new JsonArray();
            for (String arg : args) {
                array.add(arg);
            }
            object.add("args", array);
        }
        if (payload != null && payload.size() > 0) {
            object.add("payload", payload);
        }
        return object.toString();
    }

    public String payloadString(String key) {
        return str(payload, key);
    }

    public boolean payloadBoolean(String key, boolean def) {
        if (payload == null || !payload.has(key) || !payload.get(key).isJsonPrimitive()) {
            return def;
        }
        return payload.get(key).getAsBoolean();
    }

    public static String str(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static List<String> stringList(JsonElement element) {
        List<String> list = new ArrayList<String>();
        if (element == null || !element.isJsonArray()) {
            return list;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive()) {
                list.add(item.getAsString());
            }
        }
        return list;
    }
}
