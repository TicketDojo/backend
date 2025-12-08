package com.ticket.dojo.backdeepfamily.domain.user.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User.Role;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service("UserServiceImpl")
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public void join(UserLoginRequest req) {

        String email = req.getEmail();
        String password = req.getPassword();

        Boolean isExist = userRepository.existsByEmail(email);

        if(isExist) {
            return;
        }

        User data = new User();

        data.setEmail(email);
        data.setName(email); // email을 name으로도 사용
        data.setPassword(bCryptPasswordEncoder.encode(password));
        data.setRole(Role.USER);

        userRepository.save(data);
    }

}
