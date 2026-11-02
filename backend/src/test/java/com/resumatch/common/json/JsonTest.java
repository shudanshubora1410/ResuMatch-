package com.resumatch.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.exception.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonTest {

  record Sample(String name, int count, Instant when, LocalDateTime localTime) {
  }

  record Dated(LocalDate day) {
  }

  @Test
  @DisplayName("java.time values are written as ISO-8601 strings, not objects")
  void timeTypesAreIso() {
    Sample sample = new Sample("abc", 3, Instant.parse("2026-01-02T03:04:05Z"),
        LocalDateTime.parse("2026-01-02T03:04:05"));

    String json = Json.toJson(sample);

    assertTrue(json.contains("\"when\":\"2026-01-02T03:04:05Z\""), json);
    assertTrue(json.contains("\"localTime\":\"2026-01-02T03:04:05\""), json);
  }

  @Test
  @DisplayName("LocalDate round-trips as an ISO date, which score history and reports rely on")
  void localDateRoundTrip() {
    String json = Json.toJson(new Dated(LocalDate.parse("2026-11-02")));

    assertEquals("{\"day\":\"2026-11-02\"}", json);
    assertEquals(LocalDate.parse("2026-11-02"), Json.fromJson(json, Dated.class).day());
  }

  @Test
  @DisplayName("HTML characters are escaped, which is defence in depth against XSS")
  void htmlIsEscaped() {
    String json = Json.toJson(new Sample("<script>alert(1)</script>", 0,
        Instant.parse("2026-01-02T03:04:05Z"), LocalDateTime.now()));

    assertTrue(json.contains("\\u003cscript\\u003e"), json);
  }

  @Test
  @DisplayName("a malformed body becomes a 400, never a 500")
  void malformedJsonIsBadRequest() {
    ApiException failure = assertThrows(ApiException.class,
        () -> Json.fromJson("{not json", Sample.class));

    assertEquals(400, failure.getStatus());
    assertEquals("BAD_REQUEST", failure.getError());
  }

  @Test
  @DisplayName("an empty body becomes a 400")
  void emptyBodyIsBadRequest() {
    ApiException failure = assertThrows(ApiException.class, () -> Json.fromJson("  ", Sample.class));

    assertEquals(400, failure.getStatus());
  }

  @Test
  @DisplayName("a valid body is mapped onto the DTO")
  void validBodyIsMapped() {
    Sample parsed = Json.fromJson("{\"name\":\"x\",\"count\":5}", Sample.class);

    assertEquals("x", parsed.name());
    assertEquals(5, parsed.count());
  }
}
