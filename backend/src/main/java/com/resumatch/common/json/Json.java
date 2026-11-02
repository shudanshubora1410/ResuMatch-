package com.resumatch.common.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.resumatch.common.exception.ApiException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One place for JSON (de)serialization so every endpoint produces the same shape. */
public final class Json {

  private static final Gson GSON = new GsonBuilder()
      // java.time types get ISO-8601 strings instead of Gson's reflective field output.
      .registerTypeAdapter(Instant.class, new InstantAdapter())
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
      // Note: HTML escaping is intentionally left ON (Gson default) as defence in depth.
      .create();

  private Json() {
    // Utility class.
  }

  public static String toJson(Object value) {
    return GSON.toJson(value);
  }

  /**
   * Parses a request body into a DTO.
   *
   * <p>Malformed JSON becomes a 400 with the standard error envelope, so servlets never have to
   * write their own parse-error handling.</p>
   */
  public static <T> T fromJson(String json, Class<T> type) {
    if (json == null || json.isBlank()) {
      throw ApiException.badRequest("Request body is required.");
    }
    try {
      T parsed = GSON.fromJson(json, type);
      if (parsed == null) {
        throw ApiException.badRequest("Request body is required.");
      }
      return parsed;
    } catch (JsonParseException ex) {
      // The message of the parse exception can echo the body, so we replace it entirely.
      throw ApiException.badRequest("Request body is not valid JSON.");
    }
  }

  private static final class InstantAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.value(value.toString());
      }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return Instant.parse(in.nextString());
    }
  }

  private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
    @Override
    public void write(JsonWriter out, LocalDateTime value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.value(value.toString());
      }
    }

    @Override
    public LocalDateTime read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return LocalDateTime.parse(in.nextString());
    }
  }

  private static final class LocalDateAdapter extends TypeAdapter<LocalDate> {
    @Override
    public void write(JsonWriter out, LocalDate value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.value(value.toString());
      }
    }

    @Override
    public LocalDate read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return LocalDate.parse(in.nextString());
    }
  }
}
