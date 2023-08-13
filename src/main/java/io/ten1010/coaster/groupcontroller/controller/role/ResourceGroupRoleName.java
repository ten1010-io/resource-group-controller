package io.ten1010.coaster.groupcontroller.controller.role;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceGroupRoleName {

    public static boolean isResourceGroupRoleName(String roleName) {
        return RESOURCE_GROUP_ROLE_NAME_PATTERN.matcher(roleName).matches();
    }

    public static ResourceGroupRoleName fromRoleName(String roleName) {
        Matcher matcher = RESOURCE_GROUP_ROLE_NAME_PATTERN.matcher(roleName);
        matcher.matches();
        return new ResourceGroupRoleName(matcher.group(1));
    }

    private static final String ROLE_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_ROLE_NAME_PATTERN = Pattern.compile(ROLE_NAME_PREFIX + "(.*)");

    @Getter
    private String resourceGroupName;

    public ResourceGroupRoleName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public String getName() {
        return ROLE_NAME_PREFIX + this.resourceGroupName;
    }

}
