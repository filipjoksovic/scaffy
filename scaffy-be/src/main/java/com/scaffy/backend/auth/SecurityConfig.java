package com.scaffy.backend.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({ AuthProperties.class, AppProperties.class, OAuthClientProperties.class })
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			OAuthLoginSuccessHandler oauthLoginSuccessHandler,
			OAuthLoginFailureHandler oauthLoginFailureHandler,
			OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/auth/me",
								"/api/auth/connect/**",
								"/api/auth/connections/**",
								"/api/repositories/**",
								"/api/workspaces/**",
								"/api/init/favourites/**",
								"/api/init/history",
								"/api/recommend/finding/apply").authenticated()
						.anyRequest().permitAll())
				.oauth2Login(oauth -> oauth
						.userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
						.successHandler(oauthLoginSuccessHandler)
						.failureHandler(oauthLoginFailureHandler))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
