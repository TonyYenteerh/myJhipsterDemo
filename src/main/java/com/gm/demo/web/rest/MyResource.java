package com.gm.demo.web.rest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MyResource {

    @GetMapping("/hello")
    public String hello(Authentication authentication) {
        String username = authentication.getName();

        return "Hello : " + username;
    }
}
