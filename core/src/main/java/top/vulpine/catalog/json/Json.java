package top.vulpine.catalog.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * The Gson instance core uses.
 *
 * <p>Uses only Gson 2.8 features. The server's bundled Gson runs it.</p>
 */
public final class Json {

    private static final Gson GSON = builder().setPrettyPrinting().create();

    /** Without line breaks or indentation. */
    private static final Gson COMPACT = builder().create();

    private Json() {
    }

    /**
     * The shared Gson instance.
     *
     * @return the configured Gson
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * The shared Gson instance for JSON nobody reads by hand.
     */
    public static Gson compact() {
        return COMPACT;
    }

    private static GsonBuilder builder() {
        return new GsonBuilder()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(Instant.class, new InstantAdapter());
    }

    /**
     * Writes a value to a temporary file and moves it over {@code file}.
     *
     * @param type the type to serialize the value as
     */
    public static void write(Path file, Object value, Type type) throws IOException {

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");

        Files.createDirectories(file.getParent());
        Files.writeString(temporary, GSON.toJson(value, type), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Reads the ISO-8601 timestamps Modrinth returns.
     *
     * <p>Accepts both {@code Z} and {@code +00:00}.</p>
     */
    private static final class InstantAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {

            if (value == null) {
                out.nullValue();
                return;
            }

            out.value(value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {

            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            String text = in.nextString();

            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (Exception e) {
                throw new IOException("Unreadable timestamp from Modrinth: " + text, e);
            }
        }

    }

}
