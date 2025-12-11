package com.ticket.dojo.backdeepfamily.domain.user.service;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;

public interface UserService {
    public void join(UserLoginRequest req);
}
