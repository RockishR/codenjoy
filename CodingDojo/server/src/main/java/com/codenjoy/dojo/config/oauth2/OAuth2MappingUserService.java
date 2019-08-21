package com.codenjoy.dojo.config.oauth2;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2018 - 2019 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

import com.codenjoy.dojo.config.AppProperties;
import com.codenjoy.dojo.config.meta.OAuth2Profile;
import com.codenjoy.dojo.services.ConfigProperties;
import com.codenjoy.dojo.services.dao.Registration;
import com.codenjoy.dojo.services.dao.Registration.User;
import com.codenjoy.dojo.services.hash.Hash;
import com.codenjoy.dojo.services.security.GameAuthorities;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * @author Igor_Petrov@epam.com
 * Created at 5/15/2019
 */
@RequiredArgsConstructor
@Component
@OAuth2Profile
public class OAuth2MappingUserService extends DefaultOAuth2UserService {

    private final PasswordEncoder passwordEncoder;
    private final Registration registration;
    private final ConfigProperties confProperties;
    private final AppProperties appProperties;

    @Value("${registration.pasword.autogen-length}")
    private int autoGenPasswordLen;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Optional<User> applicationUser = registration
                .getUserByEmail((String) oAuth2User.getAttributes().get("email"));

        return applicationUser.orElseGet(() -> registerNewUser(oAuth2User));
    }

  private User registerNewUser(OAuth2User oAuth2User) {
    Map<String, Object> attributes = oAuth2User.getAttributes();
    String email = (String) attributes.get("email");

    String playerName = extractUserName(attributes, email);

    String autoGeneratedPassword = passwordEncoder.encode(randomAlphanumeric(autoGenPasswordLen));

    GameAuthorities authorities = appProperties.isSsoAdmin(email)
        ? GameAuthorities.ADMIN
        : GameAuthorities.USER;

    User newlyRegisteredUser = registration.register(Hash.getRandomId(), email, playerName,
        autoGeneratedPassword, "{}", authorities.roles());

    if (!confProperties.isEmailVerificationNeeded()) {
      registration.approve(newlyRegisteredUser.getCode());
    }

    return newlyRegisteredUser;
  }

  @SuppressWarnings("unchecked")
  private String extractUserName(Map<String, Object> attributes, String email) {
    Object name = attributes.get("name");
    String playerName = null;
    if (name instanceof String) {
      playerName = (String) name;
    } else if (name instanceof List) {
      List<Object> namesClaim = (List<Object>) name;
      if (!CollectionUtils.isEmpty(namesClaim)) {
        playerName = namesClaim.get(0).toString();
      }
    } else {
      playerName = email;
    }
    return playerName;
  }
}
