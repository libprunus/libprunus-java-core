package com.example.backend.catalog.controller.web;

import org.libprunus.core.error.ApiErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

// Scoped to the web package so libprunus' JSON @RestControllerAdvice doesn't render browser
// navigations as JSON; detail() is the only web throw site, always NOT_FOUND.
@ControllerAdvice(basePackages = "com.example.backend.catalog.controller.web")
class WebErrorAdvice {

    @ExceptionHandler(ApiErrorException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(ApiErrorException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }
}
