package com.scaffy.backend.auth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

public class DynamicClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

	private final Map<String, ClientRegistration> staticRegistrations;
	private final OAuthInstanceRepository instanceRepository;

	public DynamicClientRegistrationRepository(
			Map<String, ClientRegistration> staticRegistrations,
			OAuthInstanceRepository instanceRepository) {
		this.staticRegistrations = staticRegistrations;
		this.instanceRepository = instanceRepository;
	}

	@Override
	public ClientRegistration findByRegistrationId(String registrationId) {
		if (registrationId == null) {
			return null;
		}
		ClientRegistration registration = staticRegistrations.get(registrationId);
		if (registration != null) {
			return registration;
		}
		if (!registrationId.startsWith("gitlab-")) {
			return null;
		}
		return instanceRepository.findByRegistrationId(registrationId)
				.map(instance -> GitLabClientRegistrations.build(
						instance.registrationId(),
						instance.baseUrl(),
						instance.clientId(),
						instance.clientSecret()))
				.orElse(null);
	}

	@Override
	public Iterator<ClientRegistration> iterator() {
		List<ClientRegistration> registrations = new ArrayList<>(staticRegistrations.values());
		instanceRepository.listPublic().forEach(summary -> {
			ClientRegistration registration = findByRegistrationId(summary.registrationId());
			if (registration != null) {
				registrations.add(registration);
			}
		});
		return registrations.iterator();
	}
}
