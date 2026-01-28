package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
@EnableCaching
class ResourceServerConfiguration {

  @Bean
  fun filterChain(http: HttpSecurity): SecurityFilterChain {
    http {
      sessionManagement { SessionCreationPolicy.STATELESS }
      headers { frameOptions { sameOrigin = true } }
      csrf { disable() }
      authorizeHttpRequests {
        listOf(
          "/webjars/**",
          "/favicon.ico",
          "/health/**",
          "/info",
          "/swagger-resources/**",
          "/v3/api-docs/**",
          "/swagger-ui/**",
          "/swagger-ui.html",
          "/h2-console/**",
          "/some-url-not-found",
        ).forEach { authorize(it, permitAll) }
        authorize(anyRequest, authenticated)
      }
      oauth2ResourceServer { jwt { jwtAuthenticationConverter = AuthAwareTokenConverter() } }
    }

    return http.build()
  }

  @Bean
  fun cacheManager(): CacheManager {
    return org.springframework.cache.caffeine.CaffeineCacheManager()
  }
}
