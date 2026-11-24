package ec.rehabelite.auth.web;

import ec.rehabelite.auth.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface UserWebMapper {

    @Mapping(target = "status", expression = "java(user.status())")
    @Mapping(target = "emailChangePending", expression = "java(user.getPendingEmail() != null)")
    UserResponse toResponse(User user);

}
