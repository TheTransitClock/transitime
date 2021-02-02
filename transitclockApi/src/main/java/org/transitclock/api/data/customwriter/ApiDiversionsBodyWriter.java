package org.transitclock.api.data.customwriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.transitclock.api.data.ApiDiversions;
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class ApiDiversionsBodyWriter implements MessageBodyWriter<ApiDiversions> {

	public ApiDiversionsBodyWriter() {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {

		  return type == ApiDiversions.class;
	}

	@Override
	public long getSize(ApiDiversions t, Class<?> type, Type genericType, Annotation[] annotations,
			MediaType mediaType) {

		return 0;
	}

	@Override
	public void writeTo(ApiDiversions t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
			throws IOException, WebApplicationException {
		 	Writer writer = new PrintWriter(entityStream);
	        writer.write("<html>");
	        writer.write("<body>");
	        writer.write("<h2>JAX-RS Message Body Writer Example</h2>");	       
	        writer.write("</body>");
	        writer.write("</html>");
	        writer.flush();
	        writer.close();		
	}

}
