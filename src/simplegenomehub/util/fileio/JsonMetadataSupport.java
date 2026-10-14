package simplegenomehub.util.fileio;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.Map;
import simplegenomehub.model.GeneAnnotationData;

/** UTF-8 complete-file metadata writes that retain fields from newer producers. */
public final class JsonMetadataSupport {
    private JsonMetadataSupport() { }
    public static JsonObject read(File file) throws IOException {
        try (Reader reader = GeneAnnotationData.openAnnotationReader(file)) {
            JsonElement value = new JsonParser().parse(reader);
            if (!value.isJsonObject()) throw new IOException("Expected JSON object: " + file);
            return value.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException failure) {
            throw new IOException("Invalid metadata: " + file, failure);
        }
    }
    public static JsonObject copy(JsonObject object) {
        return object == null ? new JsonObject() : new JsonParser().parse(object.toString()).getAsJsonObject();
    }
    public static JsonElement merge(JsonElement old, JsonElement update) {
        if (old != null && old.isJsonObject() && update.isJsonObject()) {
            JsonObject out = copy(old.getAsJsonObject());
            for (Map.Entry<String, JsonElement> field : update.getAsJsonObject().entrySet())
                out.add(field.getKey(), merge(out.get(field.getKey()), field.getValue()));
            return out;
        }
        if (old != null && old.isJsonArray() && update.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement entry : update.getAsJsonArray()) {
                JsonElement previous = null;
                if (entry.isJsonObject()) {
                    for (String key : new String[]{"sample_id", "vcfName"}) {
                        JsonElement id = entry.getAsJsonObject().get(key);
                        if (id == null) continue;
                        for (JsonElement candidate : old.getAsJsonArray())
                            if (candidate.isJsonObject() && id.equals(candidate.getAsJsonObject().get(key))) previous = candidate;
                        break;
                    }
                }
                out.add(merge(previous, entry));
            }
            return out;
        }
        return update;
    }
    public static void write(File target, JsonObject value) throws IOException {
        AtomicFiles.write(target.toPath(), writer -> new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(value, writer));
    }
    public static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
