package io.ten1010.coaster.groupcontroller.controller.clusterrole;

import org.javatuples.Pair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClusterRoleNameUtil {

    private static final String CLUSTER_ROLE_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN = Pattern.compile(CLUSTER_ROLE_NAME_PREFIX + "(.*)");
    private static final String CLUSTER_ROLE_BINDING_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN = Pattern.compile(CLUSTER_ROLE_BINDING_NAME_PREFIX + "(.*)");

    public ClusterRoleNameUtil() {
    }

    public String buildClusterRoleName(String groupName) {
        return CLUSTER_ROLE_NAME_PREFIX + groupName;
    }

    public Pair<Boolean, Matcher> checkResourceGroupClusterRoleNameFormat(String roleName) {
        Matcher matcher = RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN.matcher(roleName);
        return Pair.with(matcher.matches(), matcher);
    }

    public String buildClusterRoleBindingName(String groupName) {
        return CLUSTER_ROLE_BINDING_NAME_PREFIX + groupName;
    }

    public Pair<Boolean, Matcher> checkResourceGroupClusterRoleBindingNameFormat(String roleBindingName) {
        Matcher matcher = RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN.matcher(roleBindingName);
        return Pair.with(matcher.matches(), matcher);
    }

}
