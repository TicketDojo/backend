package com.ticket.dojo.backdeepfamily.domain.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.service.UserService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("")
    public String join(@RequestBody UserLoginRequest request) {
        userService.join(request);

        return "ok";
    }

}
