package org.linlinjava.announce.goods.infrastructure.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationManager;

@Configuration
//@EnableWebSecurity
//public class GoodsManagementSecurityConfig extends WebSecurityConfigurerAdapter {
public class GoodsManagementSecurityConfig  {


    /*@Bean
    public ResourceServerTokenServices tokenServices() {
        RemoteTokenServices tokenServices = new RemoteTokenServices();
        tokenServices.setClientId("orderprocessingapp");
        tokenServices.setClientSecret("orderprocessingappsecret");
        tokenServices.setCheckTokenEndpointUrl("http://localhost:8087/oauth/check_token");
        return tokenServices;
    }


    public AuthenticationManager authenticationManagerBean() {
        OAuth2AuthenticationManager authenticationManager = new OAuth2AuthenticationManager();
        authenticationManager.setTokenServices(tokenServices());
        return authenticationManager;
    }*/

}
