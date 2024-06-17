package io.ten1010.groupcontroller.controller.cluster.rolebinding;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceGroupRoleBindingName {

    public static boolean isResourceGroupRoleBindingName(String roleBindingName) {
        return RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName).matches();
    }

    public static ResourceGroupRoleBindingName fromRoleBindingName(String roleBindingName) {
        Matcher matcher = RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName);
        matcher.matches();
        return new ResourceGroupRoleBindingName(matcher.group(1));
    }

    private static final String ROLE_BINDING_NAME_PREFIX = "resource-group-controller.resource-group.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_ROLE_BINDING_NAME_PATTERN = Pattern.compile(ROLE_BINDING_NAME_PREFIX + "(.*)");

    @Getter
    private String resourceGroupName;

    public ResourceGroupRoleBindingName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public String getName() {
        return ROLE_BINDING_NAME_PREFIX + this.resourceGroupName;
    }

}
