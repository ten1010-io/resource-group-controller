package io.ten1010.coaster.groupcontroller.controller.role;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoleNameUtil {

    private static final String ROLE_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_ROLE_NAME_PATTERN = Pattern.compile(ROLE_NAME_PREFIX + "(.*)");
    private static final String ROLE_BINDING_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN = Pattern.compile(ROLE_BINDING_NAME_PREFIX + "(.*)");

    public RoleNameUtil() {
    }

    public String buildResourceGroupRoleName(String groupName) {
        return ROLE_NAME_PREFIX + groupName;
    }

    public boolean isResourceGroupRoleNameFormat(String roleName) {
        return RESOURCE_GROUP_ROLE_NAME_PATTERN.matcher(roleName).matches();
    }

    public String getResourceGroupNameFromRoleName(String roleName) {
        Matcher matcher = RESOURCE_GROUP_ROLE_NAME_PATTERN.matcher(roleName);
        matcher.matches();
        return matcher.group(1);
    }

    public String buildResourceGroupRoleBindingName(String groupName) {
        return ROLE_BINDING_NAME_PREFIX + groupName;
    }

    public boolean isResourceGroupRoleBindingNameFormat(String roleBindingName) {
        return RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName).matches();
    }

    public String getResourceGroupNameFromRoleBindingName(String roleBindingName) {
        Matcher matcher = RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName);
        matcher.matches();
        return matcher.group(1);
    }

}
