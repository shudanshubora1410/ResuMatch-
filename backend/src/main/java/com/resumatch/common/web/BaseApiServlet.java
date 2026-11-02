package com.resumatch.common.web;

import com.resumatch.common.exception.ApiException;
import com.resumatch.common.json.Json;
import com.resumatch.common.startup.AppServices;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Common behaviour for every API servlet: the success envelope, body parsing and access to the
 * application services.
 *
 * <p>Servlets built on this class stay thin: parse the request, call a service, write the result.
 * Errors are thrown as {@link ApiException} and rendered by {@link ErrorHandlingFilter}.</p>
 */
public abstract class BaseApiServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  /** Request bodies in this project are small JSON documents; anything bigger is an attack. */
  private static final int MAX_BODY_BYTES = 64 * 1024;

  protected void writeOk(HttpServletResponse response, Object data) {
    Responses.writeJson(response, 200, ApiSuccess.of(data));
  }

  protected void writeCreated(HttpServletResponse response, Object data) {
    Responses.writeJson(response, 201, ApiSuccess.of(data));
  }

  protected void writeNoContent(HttpServletResponse response) {
    response.setStatus(204);
  }

  /** Reads and parses a JSON request body, enforcing a size limit. */
  protected <T> T readJson(HttpServletRequest request, Class<T> type) {
    return Json.fromJson(readBody(request), type);
  }

  private String readBody(HttpServletRequest request) {
    try (InputStream input = request.getInputStream()) {
      byte[] buffer = input.readNBytes(MAX_BODY_BYTES + 1);
      if (buffer.length == 0) {
        throw ApiException.badRequest("Request body is required.");
      }
      if (buffer.length > MAX_BODY_BYTES) {
        throw ApiException.payloadTooLarge("Request body is too large.");
      }
      return new String(buffer, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw ApiException.badRequest("Could not read the request body.");
    }
  }

  /*
   * Unsupported HTTP methods.
   *
   * HttpServlet's default answers 405 with Tomcat's HTML error page. Servlets in this project
   * override only the verbs they really support, so these defaults guarantee that any stray verb
   * still produces the standard JSON envelope. A subclass that needs the verb simply overrides it.
   */

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    throw methodNotAllowed(request);
  }

  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response) {
    throw methodNotAllowed(request);
  }

  /**
   * PATCH is not part of HttpServlet in Servlet 6.0 (it was added in 6.1), so we dispatch it
   * ourselves to keep the same JSON 405 behaviour - and to give later phases a clean hook for
   * partial updates such as changing an application's status.
   */
  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if ("PATCH".equalsIgnoreCase(request.getMethod())) {
      doPatch(request, response);
      return;
    }
    super.service(request, response);
  }

  protected void doPatch(HttpServletRequest request, HttpServletResponse response) {
    throw methodNotAllowed(request);
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
    throw methodNotAllowed(request);
  }

  private ApiException methodNotAllowed(HttpServletRequest request) {
    return ApiException.methodNotAllowed(
        request.getMethod() + " is not supported for " + request.getRequestURI());
  }

  /**
   * Services are created once by {@code AppContextListener}. If they are missing, startup did not
   * finish, and a 503 explains that far better than a NullPointerException.
   */
  protected AppServices services() {
    AppServices services = AppServices.from(getServletContext());
    if (services == null) {
      throw ApiException.serviceUnavailable("The application is still starting up. Please retry.");
    }
    return services;
  }
}
