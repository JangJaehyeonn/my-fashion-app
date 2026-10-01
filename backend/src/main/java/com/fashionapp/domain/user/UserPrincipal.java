package com.fashionapp.domain.user;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Getter
public class UserPrincipal implements OAuth2User, UserDetails {

    private final UUID id;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> attributes;

    private UserPrincipal(UUID id, String email) {
        this.id = id;
        this.email = email;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static UserPrincipal create(User user) {
        return new UserPrincipal(user.getId(), user.getEmail());
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        UserPrincipal principal = create(user);
        principal.attributes = attributes;
        return principal;
    }

    @Override public Map<String, Object> getAttributes() { return attributes; }
    @Override public String getName() { return String.valueOf(id); }
    @Override public String getPassword() { return null; }
    // Spring Security는 UserDetails 주체의 이름을 getUsername()으로 읽는다(OAuth2AuthorizedClient의 principalName).
    // 이메일 동의 항목이 없는 Kakao 사용자는 email이 null이라 "principalName cannot be empty"로 로그인이 실패하므로 id로 대체
    @Override public String getUsername() { return (email != null && !email.isBlank()) ? email : String.valueOf(id); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
