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

package org.stubby.yaml.stubs;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Alexander Zagniotov
 * @since 6/14/12, 1:09 AM
 */
public class StubResponse {

   private String status = null;
   private String body = null;
   private String latency = null;
   private Map<String, String> headers = new HashMap<String, String>();

   public StubResponse() {

   }

   public String getStatus() {
      return status;
   }

   public String getBody() {
      return body;
   }

   public final void setStatus(final String status) {
      this.status = status;
   }

   public final void setBody(final String body) {
      this.body = body;
   }

   public final void addHeader(final String param, final String value) {
      headers.put(param, value);
   }

   public Map<String, String> getHeaders() {
      return headers;
   }

   public void setHeaders(final Map<String, String> headers) {
      this.headers = headers;
   }

   public String getLatency() {
      return latency;
   }

   public void setLatency(final String latency) {
      this.latency = latency;
   }

   public boolean isConfigured() {
      return (status != null && body != null);
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof StubResponse)) return false;

      final StubResponse that = (StubResponse) o;

      if (!body.equals(that.body)) return false;
      if (!headers.equals(that.headers)) return false;
      if (!status.equals(that.status)) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = status.hashCode();
      result = 31 * result + body.hashCode();
      result = 31 * result + headers.hashCode();
      return result;
   }

   @Override
   public String toString() {
      return "StubResponse{" +
            "status='" + status + '\'' +
            ", body='" + body + '\'' +
            ", headers=" + headers +
            '}';
   }
}