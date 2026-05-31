package com.scaffy.backend.repository;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

import javax.net.ssl.SSLException;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns low-level network failures into clear, actionable errors. Self-hosted Git instances are
 * frequently behind a VPN or private network: the user's browser can reach them, but the Scaffy
 * server (which does the token exchange and all API calls) may not. These messages say so.
 */
final class ProviderHttpErrors {

	private ProviderHttpErrors() {
	}

	static ResponseStatusException unreachable(String provider, String host, IOException ex) {
		String target = host == null || host.isBlank() ? provider : provider + " (" + host + ")";
		if (ex instanceof UnknownHostException) {
			return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The Scaffy server could not resolve " + target + " (DNS). A private or self-hosted "
							+ "instance must be resolvable and reachable from the Scaffy server, not just your browser.");
		}
		if (ex instanceof HttpTimeoutException) {
			return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
					"The Scaffy server timed out connecting to " + target + ". If it is behind a VPN or "
							+ "firewall, run Scaffy where it can reach the instance.");
		}
		if (ex instanceof ConnectException || ex instanceof NoRouteToHostException) {
			return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The Scaffy server could not reach " + target + " (connection refused / no route). A "
							+ "self-hosted instance behind a VPN must be reachable from the Scaffy server, not just your browser.");
		}
		if (ex instanceof SSLException) {
			return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"TLS error connecting to " + target + ". The instance may use a self-signed certificate "
							+ "or a private CA that the Scaffy server does not trust.");
		}
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
				"The Scaffy server could not reach " + target + ".");
	}
}
