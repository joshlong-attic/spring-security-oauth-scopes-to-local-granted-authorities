package com.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Bean
    JdbcUserDetailsManager jdbcUserDetailsManager(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }

    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapper(JdbcClient jdbcClient, JdbcUserDetailsManager jdbcUserDetailsManager) {
        return (authorities) -> {
            var mappedAuthorities = new HashSet<GrantedAuthority>();

            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidcUserAuthority) {


                    var userInfo = oidcUserAuthority.getUserInfo();
                    var username = userInfo.getSubject();

                    jdbcClient.sql("""
                                        insert into users (username, password, enabled) values (?,?,?)
                                        on conflict do nothing ;
                                    """)
                            .params(username, UUID.randomUUID(), true)
                            .update();

                    jdbcClient.sql("""
                                        insert into authorities  (username, authority) values (?,?)
                                        on conflict do nothing ;
                                    """)
                            .params(username, "USER")
                            .update();

                    // 
                    var userDetails = jdbcUserDetailsManager.loadUserByUsername(username);
                    var roles = userDetails.getAuthorities()
                            .stream()
                            .map(ga -> new SimpleGrantedAuthority("ROLE_" + ga.getAuthority()))
                            .toList();
                    mappedAuthorities.addAll(roles);
                } // 
                else if (authority instanceof OAuth2UserAuthority oauth2UserAuthority) {

                    //todo
                    // var userAttributes = oauth2UserAuthority.getAttributes();


                }
            });

            return mappedAuthorities;
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .authorizeHttpRequests(h -> h
                        .requestMatchers("/admin").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .build();
    }
}


@Controller
@ResponseBody
class SecuredController {

    @GetMapping("/admin")
    Map<String, String> admin(Principal principal) {
        return Map.of("message", "hello, admin " + principal.getName());
    }

    @GetMapping("/")
    Map<String, String> hello(Principal principal) {
        System.out.println(principal.getClass().getName());
        return Map.of("message", "hello, " + principal.getName());
    }
}