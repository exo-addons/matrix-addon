package io.meeds.chat;

import org.exoplatform.commons.api.portlet.GenericDispatchedViewPortlet;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import java.io.IOException;

public class ChatPortlet extends GenericDispatchedViewPortlet {
  private static final Log LOG                  = ExoLogger.getLogger(ChatPortlet.class);

  @Override
  protected void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
    super.doView(request, response);
  }
}
