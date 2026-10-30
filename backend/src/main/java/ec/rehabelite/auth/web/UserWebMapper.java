package ec.rehabelite.auth.web;

import ec.rehabelite.auth.domain.User;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
interface UserWebMapper {

    UserResponse toResponse(User user);

    List<UserResponse> toResponses(List<User> users);

}
