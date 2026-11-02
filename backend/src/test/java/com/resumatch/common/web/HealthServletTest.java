package com.resumatch.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.resumatch.common.exception.ApiException;
import com.resumatch.common.startup.AppServices;
import com.resumatch.testsupport.CapturedResponse;
import com.resumatch.testsupport.TestHttp;
import com.resumatch.testsupport.TestServices;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthServletTest {

  private static HealthServlet servletWith(AppServices services) throws Exception {
    ServletContext context = mock(ServletContext.class);
    when(context.getAttribute(AppServices.ATTRIBUTE)).thenReturn(services);
    ServletConfig config = mock(ServletConfig.class);
    when(config.getServletContext()).thenReturn(context);
    HealthServlet servlet = new HealthServlet();
    servlet.init(config);
    return servlet;
  }

  @Test
  @DisplayName("GET /api/v1/health returns 200 and the health snapshot in the success envelope")
  void returnsSnapshot() throws Exception {
    HealthServlet servlet = servletWith(TestServices.healthy());
    CapturedResponse captured = new CapturedResponse();

    servlet.doGet(TestHttp.request("GET", "/api/v1/health"), captured.mock());

    assertEquals(200, captured.status());
    String body = captured.body();
    assertTrue(body.contains("\"success\":true"), body);
    assertTrue(body.contains("\"status\":\"UP\""), body);
    assertTrue(body.contains("\"version\":\"1.0.0-test\""), body);
    assertTrue(body.contains("\"migrations\":{\"status\":\"SUCCEEDED\"}"), body);
    assertEquals("no-store", captured.header("Cache-Control"));
  }

  @Test
  @DisplayName("a 503 is raised (and rendered by the filter) when startup has not finished")
  void unavailableBeforeStartup() throws Exception {
    HealthServlet servlet = servletWith(null);
    CapturedResponse captured = new CapturedResponse();

    ApiException failure = assertThrows(ApiException.class,
        () -> servlet.doGet(TestHttp.request("GET", "/api/v1/health"), captured.mock()));

    assertEquals(503, failure.getStatus());
  }
}
