package io.ten1010.coaster.groupcontroller.controller.clusterrole;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClusterRoleNameUtil {

    private static final String CLUSTER_ROLE_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN = Pattern.compile(CLUSTER_ROLE_NAME_PREFIX + "(.*)");
    private static final String CLUSTER_ROLE_BINDING_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN = Pattern.compile(CLUSTER_ROLE_BINDING_NAME_PREFIX + "(.*)");

    public ClusterRoleNameUtil() {
    }

    public String buildResourceGroupClusterRoleName(String groupName) {
        return CLUSTER_ROLE_NAME_PREFIX + groupName;
    }

    public boolean isResourceGroupClusterRoleNameFormat(String roleName) {
        return RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN.matcher(roleName).matches();
    }

    public String getResourceGroupNameFromClusterRoleName(String roleName) {
        Matcher matcher = RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN.matcher(roleName);
        matcher.matches();
        return matcher.group(1);
    }

    public String buildResourceGroupClusterRoleBindingName(String groupName) {
        return CLUSTER_ROLE_BINDING_NAME_PREFIX + groupName;
    }

    public boolean isResourceGroupClusterRoleBindingNameFormat(String roleBindingName) {
        return RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName).matches();
    }

    public String getResourceGroupNameFromClusterRoleBindingName(String roleBindingName) {
        Matcher matcher = RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName);
        matcher.matches();
        return matcher.group(1);
    }

}
