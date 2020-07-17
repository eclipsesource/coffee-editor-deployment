package org.eclipse.emfcloud.coffee.internal.example.k8s;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.osgi.service.component.annotations.Component;

@Provider
@Component(name = "CORSFilter", service = ContainerResponseFilter.class, immediate = true)
public class CORSFilter implements ContainerResponseFilter {

	private static final Set<String> ALLOWED_ORIGINS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(//
			"http://35.246.187.143", //
			"http://35.246.187.143/", //
			"https://35.246.187.143", //
			"https://35.246.187.143/", //
			"http://eclipsesource.com/coffee-editor", //
			"http://eclipsesource.com/coffee-editor/", //
			"http://www.eclipsesource.com/coffee-editor", //
			"http://www.eclipsesource.com/coffee-editor/", //
			"https://eclipsesource.com/coffee-editor", //
			"https://eclipsesource.com/coffee-editor/", //
			"https://www.eclipsesource.com/coffee-editor", //
			"https://www.eclipsesource.com/coffee-editor/")));

	@Override
	public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext cres)
			throws IOException {
		String origin = requestContext.getHeaders().getFirst("Origin");
		if (!allowedOrigin(origin)) {
			return;
		}
		cres.getHeaders().add("Access-Control-Allow-Origin", origin);
		cres.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
		cres.getHeaders().add("Access-Control-Allow-Credentials", "true");
		cres.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
		cres.getHeaders().add("Access-Control-Max-Age", "1209600");
	}

	private boolean allowedOrigin(String origin) {
		if (origin == null) {
			return false;
		}
		return ALLOWED_ORIGINS.contains(origin.toLowerCase());
	}

}
