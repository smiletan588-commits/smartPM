package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.LoginRequestDTO;
import com.smartpm.dto.RegisterRequestDTO;
import com.smartpm.entity.User;
import com.smartpm.service.UserService;
import com.smartpm.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public R<User> register(@RequestBody(required = false) RegisterRequestDTO request,
                            @RequestParam(required = false) String username,
                            @RequestParam(required = false) String password,
                            @RequestParam(required = false) String nickname,
                            @RequestParam(required = false) String identity) {
        String resolvedUsername = request == null ? username : request.getUsername();
        String resolvedPassword = request == null ? password : request.getPassword();
        String resolvedNickname = request == null ? nickname : request.getNickname();
        String resolvedIdentity = request == null ? identity : request.getIdentity();
        User user = userService.register(resolvedUsername, resolvedPassword, resolvedNickname, resolvedIdentity);
        user.setPassword(null);
        return R.ok(user);
    }

    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody(required = false) LoginRequestDTO request,
                            @RequestParam(required = false) String username,
                            @RequestParam(required = false) String password) {
        String resolvedUsername = request == null ? username : request.getUsername();
        String resolvedPassword = request == null ? password : request.getPassword();
        LoginVO vo = userService.login(resolvedUsername, resolvedPassword);
        return R.ok(vo);
    }

    @PutMapping("/identity")
    public R<Void> updateIdentity(@RequestParam String identity) {
        userService.updateIdentity(UserHolder.getUserId(), identity);
        return R.ok();
    }
}
