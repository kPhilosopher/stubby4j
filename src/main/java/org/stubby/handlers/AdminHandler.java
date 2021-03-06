/*
A Java-based HTTP stub server

Copyright (C) 2012 Alexander Zagniotov, Isa Goksu and Eric Mrak

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.stubby.handlers;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpSchemes;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.stubby.database.DataStore;
import org.stubby.server.JettyOrchestrator;
import org.stubby.utils.HandlerUtils;
import org.stubby.utils.ReflectionUtils;
import org.stubby.yaml.YamlConsumer;
import org.stubby.yaml.stubs.StubHttpLifecycle;
import org.stubby.yaml.stubs.StubRequest;
import org.stubby.yaml.stubs.StubResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Zagniotov
 * @since 6/17/12, 11:25 PM
 */
public final class AdminHandler extends AbstractHandler {

   public static final String RESOURCE_PING = "/ping";
   public static final String RESOURCE_ENDPOINT_NEW = "/endpoint/new";

   private static final String HTML_TAG_TR_PARAMETIZED_TEMPLATE = "<tr><td width='185px' valign='top' align='left'><code>%s</code></td><td align='left'>%s</td></tr>";

   private final DataStore dataStore;
   private final JettyOrchestrator jettyOrchestrator;

   public AdminHandler(final DataStore dataStore, final JettyOrchestrator jettyOrchestrator) {
      this.dataStore = dataStore;
      this.jettyOrchestrator = jettyOrchestrator;
   }

   @Override
   public void handle(final String target,
                      final Request baseRequest,
                      final HttpServletRequest request,
                      final HttpServletResponse response) throws IOException, ServletException {

      baseRequest.setHandled(true);
      response.setContentType(MimeTypes.TEXT_HTML_UTF_8);
      response.setStatus(HttpStatus.OK_200);
      response.setHeader(HttpHeaders.SERVER, HandlerUtils.constructHeaderServerName());

      if (request.getPathInfo().equals(AdminHandler.RESOURCE_PING)) {
         handleGetOnPing(response);
         return;
      } else if (request.getPathInfo().equals(AdminHandler.RESOURCE_ENDPOINT_NEW)) {
         handlePostOnRegisteringNewEndpoint(request, response);
         return;
      }

      final String adminHandlerHtml = HandlerUtils.populateHtmlTemplate("index", request.getContextPath());
      response.getWriter().println(adminHandlerHtml);
   }

   private void handlePostOnRegisteringNewEndpoint(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
      if (!request.getMethod().equalsIgnoreCase("post")) {
         final String errorMessage = String.format("Method %s is not allowed on URI %s", request.getMethod(), request.getPathInfo());
         HandlerUtils.configureErrorResponse(response, HttpStatus.METHOD_NOT_ALLOWED_405, errorMessage);
         return;
      }

      final StubHttpLifecycle registered = addHttpCycleToDataStore(request);
      if (!registered.isComplete()) {
         final String errorMessage = String.format("Endpoint content provided is not complete, was given %s", registered);
         HandlerUtils.configureErrorResponse(response, HttpStatus.BAD_REQUEST_400, errorMessage);
         return;
      }

      final boolean isAdded = !dataStore.getStubHttpLifecycles().contains(registered)
            && dataStore.getStubHttpLifecycles().add(registered);
      if (!isAdded) {
         final String errorMessage = String.format("Endpoint already exists for provided parameters, was given %s", registered);
         HandlerUtils.configureErrorResponse(response, HttpStatus.CONFLICT_409, errorMessage);
         return;
      }

      response.setStatus(HttpStatus.CREATED_201);
      response.getWriter().println(String.format("Created endpoint %s", registered));
   }

   private StubHttpLifecycle addHttpCycleToDataStore(final HttpServletRequest request) {

      final StubRequest registeredStubRequest = new StubRequest();
      registeredStubRequest.setMethod(request.getParameter("method"));
      registeredStubRequest.setUrl(request.getParameter("url"));
      registeredStubRequest.setPostBody(request.getParameter("postBody"));

      final StubResponse registeredStubResponse = new StubResponse();
      registeredStubResponse.setBody(request.getParameter("body"));
      registeredStubResponse.setLatency(request.getParameter("latency"));
      registeredStubResponse.setStatus(request.getParameter("status"));

      setStubResponseHeaders(request, registeredStubResponse);
      return new StubHttpLifecycle(registeredStubRequest, registeredStubResponse);
   }

   private void setStubResponseHeaders(final HttpServletRequest request, final StubResponse registeredStubResponse) {
      if (request.getParameter("responseHeaders") != null) {
         final Map<String, String> headers = new HashMap<String, String>();
         for (final String header : request.getParameter("responseHeaders").split(",")) {
            final String[] keyAndValueHeader = header.split("=");
            if (keyAndValueHeader.length != 2) {
               continue;
            }
            headers.put(keyAndValueHeader[0], keyAndValueHeader[1]);
         }
         registeredStubResponse.setHeaders(headers);
      }
   }

   private void handleGetOnPing(final HttpServletResponse response) throws IOException {
      try {
         response.getWriter().println(getConfigDataPresentation());
      } catch (Exception ex) {
         HandlerUtils.configureErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR_500, ex.toString());
      }
   }

   private String getConfigDataPresentation() throws Exception {

      final List<StubHttpLifecycle> stubHttpLifecycles = dataStore.getStubHttpLifecycles();

      final StringBuilder builder = new StringBuilder();
      builder.append(buildSystemStatusHtmlTable());
      builder.append("<br /><br />");

      final String requestCounterHtml = HandlerUtils.getHtmlResourceByName("snippet_request_response_tables");
      for (final StubHttpLifecycle stubHttpLifecycle : stubHttpLifecycles) {
         final StubRequest stubRequest = stubHttpLifecycle.getRequest();
         final StubResponse stubResponse = stubHttpLifecycle.getResponse();
         builder.append(buildPageBodyHtml(requestCounterHtml, "Request", ReflectionUtils.getProperties(stubRequest)));
         builder.append(buildPageBodyHtml(requestCounterHtml, "Response", ReflectionUtils.getProperties(stubResponse)));
         builder.append("<br /><br />");
      }
      return HandlerUtils.populateHtmlTemplate("ping", stubHttpLifecycles.size(), builder.toString());
   }

   private String buildSystemStatusHtmlTable() throws Exception {

      final StringBuilder builder = new StringBuilder();

      final String host = jettyOrchestrator.getCurrentHost();
      final int clientPort = jettyOrchestrator.getCurrentClientPort();
      final int adminPort = jettyOrchestrator.getCurrentAdminPort();

      builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "CLIENT PORT", clientPort));
      builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "ADMIN PORT", adminPort));

      if (jettyOrchestrator.isSslConfigured()) {
         builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "SSL PORT", JettyOrchestrator.DEFAULT_SSL_PORT));
      }

      builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "HOST", host));
      builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "CONFIGURATION", YamlConsumer.LOADED_CONFIG));

      final String endpointRegistration = HandlerUtils.linkifyRequestUrl(HttpSchemes.HTTP, RESOURCE_ENDPOINT_NEW, host, adminPort);
      builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "NEW ENDPOINT POST URI", endpointRegistration));

      final String systemStatusTable = HandlerUtils.getHtmlResourceByName("snippet_system_status_table");
      return String.format(systemStatusTable, builder.toString());
   }

   private String buildPageBodyHtml(final String requestCounterHtml, final String tableName, final Map<String, String> stubMemberFields) throws Exception {
      final StringBuilder builder = new StringBuilder();

      final String host = jettyOrchestrator.getCurrentHost();
      final int clientPort = jettyOrchestrator.getCurrentClientPort();

      for (final Map.Entry<String, String> keyValue : stubMemberFields.entrySet()) {
         Object value = keyValue.getValue();
         if (value != null) {
            value = HandlerUtils.escapeHtmlEntities(value.toString());
         }

         if (keyValue.getKey().equals("url")) {
            value = HandlerUtils.linkifyRequestUrl(HttpSchemes.HTTP, value, host, clientPort);

            if (jettyOrchestrator.isSslConfigured() && tableName.equalsIgnoreCase("request")) {
               final String sslUrl = HandlerUtils.linkifyRequestUrl(HttpSchemes.HTTPS, keyValue.getValue(), host, JettyOrchestrator.DEFAULT_SSL_PORT);
               builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, "SSL URL", sslUrl));
            }
         }
         builder.append(String.format(HTML_TAG_TR_PARAMETIZED_TEMPLATE, keyValue.getKey().toUpperCase(), value));
      }
      return String.format(requestCounterHtml, tableName, builder.toString());
   }
}