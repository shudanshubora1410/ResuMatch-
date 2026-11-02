package com.resumatch.common.web;

import com.resumatch.common.json.Json;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the container's error pages (declared in {@code web.xml}).
 *
 * <p>Why this class exists: when nothing is mapped to a URL, Tomcat answers with its own HTML
 * page, and {@code sendError} bypasses servlet filters, so a filter cannot rewrite it afterwards.
 * An {@code <error-page>} declaration is the supported hook. It also handles real 500s from the
 * container (for example an out-of-memory guard) that never reach our services.</p>
 *
 * <p>API paths get the standard JSON envelope; everything else gets a small branded HTML page.
 * The exception message is deliberately not shown to the user - it is already in the log.</p>
 */
public final class ErrorPageServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(ErrorPageServlet.class);

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // All verbs are handled identically here: this is the error page, not an API endpoint.
    render(request, response);
  }

  private void render(HttpServletRequest request, HttpServletResponse response) {
    int status = statusCode(request);
    String originalPath = stringAttribute(request,
        jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());

    if (originalPath != null && originalPath.startsWith("/api/")) {
      writeJsonError(response, status, originalPath);
      return;
    }
    writeHtmlPage(response, status);
  }

  private void writeJsonError(HttpServletResponse response, int status, String path) {
    String error = switch (status) {
      case 404 -> "NOT_FOUND";
      case 403 -> "FORBIDDEN";
      case 405 -> "METHOD_NOT_ALLOWED";
      default -> "INTERNAL_ERROR";
    };
    String message = status == 404
        ? "No API endpoint matches " + path
        : "The request could not be completed. Please try again.";

    try {
      response.setStatus(status);
      response.setContentType("application/json; charset=UTF-8");
      response.setHeader("Cache-Control", "no-store");
      response.getWriter().write(Json.toJson(
          ErrorResponse.of(status, error, message, path, Map.of())));
      response.getWriter().flush();
    } catch (IOException ex) {
      log.debug("Could not write the JSON error page: {}", ex.getMessage());
    }
  }

  /**
   * Minimal self-contained page: inline CSS so it renders even if the stylesheet cannot load, and
   * the same footer branding as the rest of the site.
   */
  private void writeHtmlPage(HttpServletResponse response, int status) {
    String title = status == 404 ? "Page not found" : "Something went wrong";
    String detail = status == 404
        ? "That page does not exist. It may belong to a later phase of the project."
        : "The server could not complete that request. Please try again in a moment.";

    String html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>%d - ResuMatch</title>
          <style>
            body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:-apple-system,
            BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;background:#f6f8fc;color:#0f172a}
            main{max-width:32rem;padding:2rem;text-align:center}
            .mark{display:inline-grid;place-items:center;width:44px;height:44px;border-radius:12px;
            background:linear-gradient(135deg,#4f46e5,#4338ca);color:#fff;font-weight:800}
            h1{font-size:1.6rem;margin:1rem 0 .4rem}
            p{color:#52607a;line-height:1.6;margin:.4rem 0}
            a{color:#4338ca;font-weight:600}
            footer{margin-top:2rem;font-size:.8rem;color:#7c879c}
          </style>
        </head>
        <body>
          <main>
            <span class="mark" aria-hidden="true">RM</span>
            <h1>%d - %s</h1>
            <p>%s</p>
            <p><a href="/">Back to ResuMatch</a> &middot;
               <a href="mailto:support.resumatch@gmail.com">support.resumatch@gmail.com</a></p>
            <footer>&copy; 2026 ResuMatch. Built by Shudanshu Bora</footer>
          </main>
        </body>
        </html>
        """.formatted(status, status, title, detail);

    try {
      response.setStatus(status);
      response.setContentType("text/html; charset=UTF-8");
      response.getWriter().write(html);
      response.getWriter().flush();
    } catch (IOException ex) {
      log.debug("Could not write the HTML error page: {}", ex.getMessage());
    }
  }

  private int statusCode(HttpServletRequest request) {
    Object value = request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE);
    if (value instanceof Integer code && code >= 400) {
      return code;
    }
    return 500;
  }

  private String stringAttribute(HttpServletRequest request, String name, String fallback) {
    Object value = request.getAttribute(name);
    return value instanceof String text ? text : fallback;
  }
}
