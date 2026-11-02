package com.resumatch.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ServletContext} mock that actually stores attributes.
 *
 * <p>Mockito mocks forget attributes by default, which makes "does startup publish its services?"
 * impossible to assert. This keeps a real map behind the mock.
 */
public final class StubServletContext {

  private StubServletContext() {
    // Utility class.
  }

  public static ServletContext create() {
    ServletContext context = mock(ServletContext.class);
    Map<String, Object> attributes = new HashMap<>();

    when(context.getAttribute(anyString()))
        .thenAnswer(invocation -> attributes.get(invocation.<String>getArgument(0)));
    doAnswer(invocation -> {
      attributes.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(context).setAttribute(anyString(), any());
    doAnswer(invocation -> {
      attributes.remove(invocation.<String>getArgument(0));
      return null;
    }).when(context).removeAttribute(anyString());
    when(context.getContextPath()).thenReturn("");
    return context;
  }
}
