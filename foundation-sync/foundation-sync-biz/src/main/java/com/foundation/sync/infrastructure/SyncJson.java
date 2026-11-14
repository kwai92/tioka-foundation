package com.foundation.sync.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.sync.spi.IncrementalCursor;
import com.foundation.sync.spi.IncrementalMode;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步侧 JSON 编解码（F-08 BAS-11 §8 的 {@code capabilities_json} / {@code config_json} /
 * {@code cursor_json} / {@code detail_json} 列）。
 */
final class SyncJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SyncJson() {
    }

    static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    static SourceCapabilities readCapabilities(String json) {
        if (json == null || json.isBlank()) {
            return SourceCapabilities.none();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Set<SyncObjectType> objects = new LinkedHashSet<>();
            Object objectsRaw = raw.get("objects");
            if (objectsRaw instanceof List<?> list) {
                for (Object item : list) {
                    SyncObjectType type = SyncObjectType.of(String.valueOf(item));
                    if (type != null) {
                        objects.add(type);
                    }
                }
            }
            Set<IncrementalMode> modes = new LinkedHashSet<>();
            Object modesRaw = raw.get("incrementalModes");
            if (modesRaw instanceof List<?> list) {
                for (Object item : list) {
                    IncrementalMode mode = IncrementalMode.of(String.valueOf(item));
                    if (mode != null) {
                        modes.add(mode);
                    }
                }
            }
            return new SourceCapabilities(objects, modes,
                    Boolean.TRUE.equals(raw.get("providesExternalIdentity")),
                    Boolean.TRUE.equals(raw.get("supportsFieldConversion")));
        } catch (Exception ex) {
            return SourceCapabilities.none();
        }
    }

    static String writeCapabilities(SourceCapabilities capabilities) {
        SourceCapabilities caps = capabilities == null ? SourceCapabilities.none() : capabilities;
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("objects", caps.objects().stream().map(Enum::name).toList());
        raw.put("incrementalModes", caps.incrementalModes().stream().map(Enum::name).toList());
        raw.put("providesExternalIdentity", caps.providesExternalIdentity());
        raw.put("supportsFieldConversion", caps.supportsFieldConversion());
        return write(raw);
    }

    static String writeCursor(IncrementalCursor cursor) {
        if (cursor == null) {
            return null;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("mode", cursor.mode() == null ? null : cursor.mode().name());
        raw.put("value", cursor.value());
        raw.put("timestamp", cursor.timestamp() == null ? null : cursor.timestamp().toString());
        raw.put("deleteCursor", cursor.deleteCursor());
        return write(raw);
    }

    static IncrementalCursor readCursor(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> raw = readMap(json);
        IncrementalMode mode = IncrementalMode.of(raw.get("mode") == null ? null : String.valueOf(raw.get("mode")));
        String value = raw.get("value") == null ? null : String.valueOf(raw.get("value"));
        Instant timestamp = raw.get("timestamp") == null ? null
                : Instant.parse(String.valueOf(raw.get("timestamp")));
        return new IncrementalCursor(mode, value, timestamp, Boolean.TRUE.equals(raw.get("deleteCursor")));
    }
}
