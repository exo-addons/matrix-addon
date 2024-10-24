package io.meeds.chat.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.exoplatform.addons.matrix.services.MatrixConstants;
import org.exoplatform.addons.matrix.services.MatrixService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.web.filter.Filter;

import java.io.IOException;

public class MatrixAuthJWTFilter implements Filter {

  public MatrixAuthJWTFilter() {
  }

  /**
   * Do filter.
   *
   * @param request the request
   * @param response the response
   * @param chain the chain
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws ServletException the servlet exception
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    if (httpRequest.getRemoteUser() != null) {
      MatrixService matrixService = ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(MatrixService.class);
      String sessionToken = matrixService.getJWTSessionToken(httpRequest.getRemoteUser());
      Cookie cookie = new Cookie(MatrixConstants.MATRIX_JWT_COOKIE, sessionToken);
      cookie.setPath("/");
      cookie.setMaxAge(604800); // 7 days in seconds
      cookie.setHttpOnly(true);
      cookie.setSecure(request.isSecure());
      httpResponse.addCookie(cookie);
    } else {
      Cookie oldCookie = new Cookie(MatrixConstants.MATRIX_JWT_COOKIE, "");
      oldCookie.setMaxAge(0);
      httpResponse.addCookie(oldCookie);
    }

    chain.doFilter(request, response);
  }

}

