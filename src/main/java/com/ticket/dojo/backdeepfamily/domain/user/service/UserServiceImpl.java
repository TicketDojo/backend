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

        // Builder 패턴으로 User 생성
        User data = User.builder()
                .email(email)
                .name(email) // email을 name으로도 사용
                .password(bCryptPasswordEncoder.encode(password))
                .role(Role.USER)
                .build();

        userRepository.save(data);
    }

}
