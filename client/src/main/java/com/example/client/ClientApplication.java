package com.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;
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
    GrantedAuthoritiesMapper userAuthoritiesMapper(JdbcUserDetailsManager userDetailsManager) {
        return (authorities) -> {
            var mappedAuthorities = new HashSet<GrantedAuthority>();

            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                    var userInfo = oidcUserAuthority.getUserInfo();
                    var username = userInfo.getSubject();
                    var userDetails = userDetailsManager.loadUserByUsername(username);
                    mappedAuthorities.addAll(userDetails.getAuthorities());
                } else if (authority instanceof OAuth2UserAuthority oauth2UserAuthority) {

                    //todo
                    // var userAttributes = oauth2UserAuthority.getAttributes();


                }
            });

            return mappedAuthorities;
        };
    }
}

@Component
class AuthenticationListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final JdbcClient jdbcClient;

    AuthenticationListener(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        var username = event.getAuthentication().getName();
        var password = UUID.randomUUID().toString();
        var enabled = true;
        this.jdbcClient.sql("""
                            insert into users (username, password, enabled) values (?,?,?)
                            on conflict do nothing ;
                        """)
                .params(username, password, enabled)
                .update();

        this.jdbcClient.sql("""
                            insert into authorities  (username, authority) values (?,?)
                            on conflict do nothing ;
                        """)
                .params(username, "USER")
                .update();


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
        var user = SecurityContextHolder
                .getContextHolderStrategy()
                .getContext()
                .getAuthentication();
        System.out.println("authentication [" + user.getName() + ":" + user.getAuthorities() + "]");
        return Map.of("message", "hello, " + principal.getName());
    }
}