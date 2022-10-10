package io.ten1010.coaster.groupcontroller.controller.role;

import org.javatuples.Pair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoleNameUtil {

    private static final String ROLE_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_ROLE_NAME_PATTERN = Pattern.compile(ROLE_NAME_PREFIX + "(.*)");
    private static final String ROLE_BINDING_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN = Pattern.compile(ROLE_BINDING_NAME_PREFIX + "(.*)");

    public RoleNameUtil() {
    }

    public String buildRoleName(String groupName) {
        return ROLE_NAME_PREFIX + groupName;
    }

    public Pair<Boolean, Matcher> checkResourceGroupRoleNameFormat(String roleName) {
        Matcher matcher = RESOURCE_GROUP_ROLE_NAME_PATTERN.matcher(roleName);
        return Pair.with(matcher.matches(), matcher);
    }

    public String buildRoleBindingName(String groupName) {
        return ROLE_BINDING_NAME_PREFIX + groupName;
    }

    public Pair<Boolean, Matcher> checkResourceGroupRoleBindingNameFormat(String roleBindingName) {
        Matcher matcher = RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName);
        return Pair.with(matcher.matches(), matcher);
    }

}
