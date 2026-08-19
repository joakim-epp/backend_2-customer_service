package com.pensionat.customer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Hands every non-API route to the React bundle so client-side routing survives a page reload.
 * Without this, opening /customers/3/edit directly would hit Spring's resource handler and 404.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/customers", "/customers/**"})
    public String spa() {
        return "forward:/index.html";
    }
}
