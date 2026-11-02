package com.resumatch.common.web;

import com.resumatch.common.health.HealthService;
import com.resumatch.common.startup.AppServices;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code GET /api/v1/health} - used by Render's health check, the Dockerfile HEALTHCHECK and the
 * frontend "is the API awake?" indicator.
 *
 * <p>Always answers 200 (unless startup failed) with a {@code status} field of UP or DEGRADED,
 * for the reason documented in {@link com.resumatch.common.health.HealthSnapshot}.</p>
 */
public final class HealthServlet extends BaseApiServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    AppServices services = services();
    writeOk(response, HealthService.snapshot(services));
  }

  /** HEAD is answered by the servlet container as GET-without-body; explicit for clarity. */
  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response) {
    doGet(request, response);
  }
}
