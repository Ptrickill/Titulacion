package ec.rehabelite.auth.web;

import ec.rehabelite.auth.domain.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface UserWebMapper {

    @Mapping(target = "status", expression = "java(user.status())")
    UserResponse toResponse(User user);

    List<UserResponse> toResponses(List<User> users);

}
