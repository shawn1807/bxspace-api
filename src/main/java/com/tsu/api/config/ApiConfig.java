package com.tsu.api.config;

import com.tsu.api.builder.MoneyContextBuilder;
import com.tsu.api.controller.NamespaceController;
import com.tsu.api.controller.UserProfileController;
import com.tsu.api.dto.KeycloakConfig;
import com.tsu.api.service.*;
import com.tsu.api.service.impl.FileSystemBucketProvider;
import com.tsu.api.utils.KeycloakUtils;
import com.tsu.namespace.service.UserService;
import com.tsu.auth.api.AuthProvider;
import com.tsu.auth.security.AppAuthenticationTokenConverter;
import com.tsu.namespace.service.LoginService;
import com.tsu.auth.security.AppSecurityContextInitializer;
import io.ipgeolocation.sdk.api.IPGeolocationAPI;
import io.ipgeolocation.sdk.invoker.ApiClient;
import io.ipgeolocation.sdk.invoker.auth.ApiKeyAuth;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Slf4j
@EnableConfigurationProperties(KeycloakConfig.class)
@ComponentScan(basePackageClasses = {UserProfileController.class, PendingTaskService.class, NamespaceController.class, NamespaceService.class})
@Import(com.tsu.workspace.config.OAuth2Config.class)
@Configuration
public class ApiConfig {


    @Value("${ipgeolocation.token:}")
    private String ipGeoLocationToken;

    @Value("${ipinfo.token:}")
    private String ipinfoToken;

    @Bean
    public KeycloakAuthService keycloakAuthService(KeycloakConfig config,
                                                    UserService userService,
                                                    GeolocationService geolocationService) {
        Keycloak keycloak = Keycloak.getInstance(config.getUrl(), config.getRealm(),
                config.getUsername(), config.getPassword(), config.getClientId(),
                config.getSecret(), null);
        return new KeycloakAuthService(config.getRealm(), keycloak, userService, geolocationService);
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
        return new KeycloakUtils(config,httpClient);
    }



    @Bean
    public GeolocationService geoLocationService() {
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)));
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.ipinfo.io/lite")
                .clientConnector(new ReactorClientHttpConnector(httpClient))

                .build();
        ApiClient client = io.ipgeolocation.sdk.invoker.Configuration.getDefaultApiClient();
        client.setBasePath("https://api.ipgeolocation.io/v2");
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) client.getAuthentication("ApiKeyAuth");
        apiKeyAuth.setApiKey(ipGeoLocationToken);
        IPGeolocationAPI api = new IPGeolocationAPI(client);
        return new GeolocationService(api, webClient);
    }


    @Profile("!app-upgrade")
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    @Bean
    public AppSecurityContextInitializer initializer(HttpServletRequest request) {
        return new WebRequestContextInitializer(request);
    }

    @Bean
    public MoneyContextBuilder moneyContextBuilder() {
        return new MoneyContextBuilder();
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
    public AppBucketProvider bucketProvider(){
        return new FileSystemBucketProvider();
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
