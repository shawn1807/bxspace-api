package com.tsu.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.tsu.api.controller.NamespaceController;
import com.tsu.api.controller.UserProfileController;
import com.tsu.api.service.NamespaceService;
import com.tsu.auth.api.AuthProvider;
import com.tsu.auth.keycloak.KeycloakConfig;
import com.tsu.auth.keycloak.KeycloakUtils;
import com.tsu.auth.security.AppAuthenticationTokenConverter;
import com.tsu.auth.security.AppSecurityContextInitializer;
import com.tsu.auth.keycloak.service.KeycloakAuthService;
import com.tsu.entry.api.FileStoreProvider;
import com.tsu.entry.provider.filesystem.FileSystemStoreProvider;
import com.tsu.entry.provider.google.CloudStorageStoreProvider;
import com.tsu.namespace.helper.UserDbHelper;
import com.tsu.namespace.security.AdminContextInitializer;
import com.tsu.namespace.security.WebRequestContextInitializer;
import com.tsu.namespace.service.LoginService;
import com.tsu.namespace.service.UserService;
import com.tsu.namespace.service.impl.LoginServiceImpl;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

@Slf4j
@EnableConfigurationProperties({KeycloakConfig.class, GcsConfig.class})
@ComponentScan(basePackageClasses = {UserProfileController.class, NamespaceController.class, NamespaceService.class})
@Configuration
public class ApiConfig {


    @Bean
    public KeycloakAuthService keycloakAuthService(KeycloakConfig config,
                                                   UserService userService) {
        Keycloak keycloak = Keycloak.getInstance(config.getUrl(), config.getRealm(),
                config.getUsername(), config.getPassword(), config.getClientId(),
                config.getSecret(), null);
        return new KeycloakAuthService(config.getRealm(), keycloak, userService);
    }

    @Bean
    public LoginService loginService(UserDbHelper dbHelper, KeycloakAuthService keycloakAuthService) {
        LoginServiceImpl impl = new LoginServiceImpl(dbHelper);
        impl.register(AuthProvider.KEYCLOAK, keycloakAuthService);
        return impl;
    }

    @Bean
    public KeycloakUtils keycloakUtils(KeycloakConfig config) throws SSLException {
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)));
        return new KeycloakUtils(config, httpClient);
    }


    @Profile("!app-upgrade")
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    @Bean
    public AppSecurityContextInitializer initializer(HttpServletRequest request) {
        return new WebRequestContextInitializer(request);
    }


    @Profile("app-upgrade")
    @Bean
    public AppSecurityContextInitializer upgradeContextInitializer() {
        return new AdminContextInitializer();
    }

    @Profile("dev")
    @Bean
    public WebMvcConfigurer cors4Dev() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
                        .allowedHeaders("*")
//                        .allowCredentials(true)
                        .allowedMethods("*");
            }
        };
    }

    @Profile("dev")
    @Bean
    public FileStoreProvider bucketProvider() {
        return new FileSystemStoreProvider("local", "/tmp");
    }

    @Profile({"prod", "gcp"})
    @Bean
    public FileStoreProvider googleCloudStorage(GcsConfig config) throws IOException {
        GoogleCredentials credentials;
        if (StringUtils.hasText(config.getCredentialsPath())) {
            // Load credentials from file
            log.info("Loading GCS credentials from file: {}", config.getCredentialsPath());
            try (FileInputStream serviceAccountStream = new FileInputStream(
                    Paths.get(config.getCredentialsPath()).toFile())) {
                credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
            }
        } else {
            // Use Application Default Credentials (ADC)
            log.info("Using Application Default Credentials for GCS");
            credentials = GoogleCredentials.getApplicationDefault();
        }
        Storage storage = StorageOptions.newBuilder()
                .setProjectId(config.getProjectId())
                .setCredentials(credentials)
                .build()
                .getService();
        return new CloudStorageStoreProvider(config.getName(), storage);
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AppAuthenticationTokenConverter converter) throws Exception {
        http
                .authorizeHttpRequests((authorize) -> authorize
                        // Allow all OPTIONS requests for CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        // Public endpoints (without context path /api since it's the servlet context)
                        .requestMatchers("/login", "/login/**", "/auth/register/**", "/auth/register",
                                "/token", "/public/**", "/version",
                                "/login/social", "/login/social/**")
                        .permitAll()
                        // API documentation endpoints
                        .requestMatchers("/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/webjars/**", "/actuator/**")
                        .permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer((oauth2) -> oauth2
                        .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(converter))
                );
        return http.build();
    }


}
