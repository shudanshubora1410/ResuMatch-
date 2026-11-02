package com.resumatch.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.resumatch.common.exception.ApiException;
import com.resumatch.testsupport.CapturedResponse;
import com.resumatch.testsupport.TestHttp;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseApiServletTest {

  record Payload(String name) {
  }

  /** Exposes the protected helpers so they can be tested directly. */
  private static final class TestServlet extends BaseApiServlet {
    private static final long serialVersionUID = 1L;

    <T> T parse(HttpServletRequest request, Class<T> type) {
      return readJson(request, type);
    }

    void ok(HttpServletResponse response, Object data) {
      writeOk(response, data);
    }

    void created(HttpServletResponse response, Object data) {
      writeCreated(response, data);
    }

    com.resumatch.common.startup.AppServices currentServices() {
      return services();
    }
  }

  @Test
  @DisplayName("a valid body is parsed into the DTO")
  void parsesBody() {
    TestServlet servlet = new TestServlet();

    Payload payload = servlet.parse(
        TestHttp.request("POST", "/api/v1/x", "{\"name\":\"Shudanshu\"}"), Payload.class);

    assertEquals("Shudanshu", payload.name());
  }

  @Test
  @DisplayName("an empty body is rejected with 400")
  void rejectsEmptyBody() {
    TestServlet servlet = new TestServlet();

    ApiException failure = assertThrows(ApiException.class,
        () -> servlet.parse(TestHttp.request("POST", "/api/v1/x", ""), Payload.class));

    assertEquals(400, failure.getStatus());
  }

  @Test
  @DisplayName("a body over the 64 KB limit is rejected with 413, not buffered")
  void rejectsOversizedBody() {
    TestServlet servlet = new TestServlet();
    String huge = "{\"name\":\"" + "a".repeat(70 * 1024) + "\"}";

    ApiException failure = assertThrows(ApiException.class,
        () -> servlet.parse(TestHttp.request("POST", "/api/v1/x", huge), Payload.class));

    assertEquals(413, failure.getStatus());
  }

  @Test
  @DisplayName("writeOk wraps data in {success:true,data:...}")
  void writesSuccessEnvelope() {
    TestServlet servlet = new TestServlet();
    CapturedResponse captured = new CapturedResponse();

    servlet.ok(captured.mock(), new Payload("ok"));

    assertEquals(200, captured.status());
    assertEquals("{\"success\":true,\"data\":{\"name\":\"ok\"}}", captured.body());
  }

  @Test
  @DisplayName("writeCreated uses 201")
  void writesCreated() {
    TestServlet servlet = new TestServlet();
    CapturedResponse captured = new CapturedResponse();

    servlet.created(captured.mock(), new Payload("new"));

    assertEquals(201, captured.status());
    assertTrue(captured.body().contains("\"success\":true"), captured.body());
  }

  @Test
  @DisplayName("an unsupported verb answers 405 in JSON, not Tomcat's HTML page")
  void unsupportedMethodIsJson405() {
    TestServlet servlet = new TestServlet();

    ApiException failure = assertThrows(ApiException.class,
        () -> servlet.doPost(TestHttp.request("POST", "/api/v1/health"), new CapturedResponse().mock()));

    assertEquals(405, failure.getStatus());
    assertEquals("METHOD_NOT_ALLOWED", failure.getError());
  }

  @Test
  @DisplayName("PATCH is dispatched by hand because Servlet 6.0's HttpServlet has no doPatch")
  void patchIsDispatched() {
    TestServlet servlet = new TestServlet();

    ApiException failure = assertThrows(ApiException.class,
        () -> servlet.service(TestHttp.request("PATCH", "/api/v1/health"), new CapturedResponse().mock()));

    assertEquals(405, failure.getStatus());
  }

  @Test
  @DisplayName("services() explains a half-finished startup with a 503")
  void servicesMissingMeans503() throws Exception {
    ServletContext context = mock(ServletContext.class);
    when(context.getAttribute(com.resumatch.common.startup.AppServices.ATTRIBUTE)).thenReturn(null);
    ServletConfig config = mock(ServletConfig.class);
    when(config.getServletContext()).thenReturn(context);
    TestServlet servlet = new TestServlet();
    servlet.init(config);

    ApiException failure = assertThrows(ApiException.class, servlet::currentServices);

    assertEquals(503, failure.getStatus());
  }
}
