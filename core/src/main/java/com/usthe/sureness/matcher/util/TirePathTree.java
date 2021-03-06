package com.usthe.sureness.matcher.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Improved dictionary matching tree
 * support regular * **
 * the * can match zero or one directory
 * the ** can match zero or more directories
 * Match priority: Raw string > * > **
 * @author tomsun28
 * @date 19:25 2019-01-18
 */
public class TirePathTree {

    private static final Logger logger = LoggerFactory.getLogger(TirePathTree.class);

    private static final String NODE_TYPE_PATH_NODE = "pathNode";
    private static final String NODE_TYPE_MAY_PATH_END = "mayPathEnd";
    private static final String NODE_TYPE_METHOD = "methodNode";
    private static final String NODE_TYPE_FILTER_ROLES = "rolesNode";
    private static final String URL_PATH_SPLIT = "/";
    private static final String MATCH_ONE = "*";
    private static final String MATCH_ALL = "**";
    private static final int PATH_NODE_NUM_3 = 3;
    private static final int PATH_NODE_NUM_2 = 2;
    private static final Pattern PATH_SPLIT_PATTERN = Pattern.compile("/+");

    /**
     * root node
     */
    private volatile Node root;

    public TirePathTree() {
        this.root = new Node("root");
    }

    /**
     * build dictionary matching tree
     * @param paths resource path set
     */
    public synchronized void buildTree(Set<String> paths) {
        if (logger.isDebugEnabled()) {
            logger.debug("sureness - start buildTree...");
        }
        clearTree();
        for (String path : paths) {
            insertNode(path, this.root);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("sureness - buildTree finish");
        }
    }

    /**
     * rebuild and update dictionary matching tree
     * Concurrency type:RCU -- read copy update
     * Ensure that reading is not affected during reconstruction
     * @param paths resources paths set
     */
    public synchronized void rebuildTree(Set<String> paths) {
        if (logger.isDebugEnabled()) {
            logger.debug("sureness - start rebuildTree..., try rcu current way");
        }
        Node buildRoot = new Node("root");
        for (String path : paths) {
            insertNode(path, buildRoot);
        }
        this.root = buildRoot;
        if (logger.isDebugEnabled()) {
            logger.debug("sureness - rebuildTree finish");
        }
    }

    /**
     * clear dictionary matching tree
     */
    public void clearTree() {
        if (logger.isDebugEnabled()) {
            logger.debug("sureness - clearTree");
        }
        root.getChildren().clear();
    }

    /**
     * Get the number of resources (URL+METHOD) in the current matching tree
     * @return int resource number
     */
    public int getResourceNum() {
        int resourceNum = 0;
        // Breadth First Search - bfs
        Queue<Node> resourceList = new LinkedList<>();
        resourceList.add(root);
        while (!resourceList.isEmpty()) {
            Node currentNode = resourceList.poll();
            if (NODE_TYPE_METHOD.equals(currentNode.nodeType)) {
                resourceNum ++;
            }
            if (currentNode.getChildren() != null && !currentNode.getChildren().isEmpty()) {
                resourceList.addAll(currentNode.getChildren().values());
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("sureness - current PathTree resource num is: {}", resourceNum);
        }
        return resourceNum;
    }

    /**
     * Use the resource path to match supported roles in tree
     * @param path   /api/v2/host/detail===get
     * @return java.lang.String roles eg: [role1,role2]
     */
    public String searchPathFilterRoles(String path) {
        if (path == null || "".equals(path) || !path.startsWith(URL_PATH_SPLIT)) {
            return null;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("sureness - searchPathFilterRoles, path is {}", path);
        }
        path = PATH_SPLIT_PATTERN.matcher(path).replaceAll("/");
        path = path.substring(1).toLowerCase();
        String[] tmp = path.split("===");
        if (tmp.length != PATH_NODE_NUM_2) {
            return null;
        }
        String[] urlPac = tmp[0].split("/");
        String method = tmp[1];

        // Pattern matching   * **
        Node current = root;
        return searchPathRoleInChildren(current, urlPac, -1, method);
    }


    /**
     * Find the leaf node of the corresponding branch from the current node
     * @param current current node
     * @param urlPac urlPath arr
     * @param currentFlow current Flow
     * @param method http method: post get delete put...
     * @return match return roles eg:[role,role2], else return null
     */
    private String searchPathRole(Node current, String[] urlPac, int currentFlow, String method) {
        if (current == null || urlPac == null || currentFlow >= urlPac.length
                || currentFlow < 0 || method == null || "".equals(method)) {
            return null;
        }

        // fast fail
        if (isNoMatchString(current.getData(), urlPac[currentFlow])) {
            return null;
        }
        if (currentFlow == urlPac.length - 1 && (NODE_TYPE_MAY_PATH_END.equals(current.getNodeType()))) {
            Node methodNode = current.getChildren().get(method);
            if (methodNode != null) {
                if (NODE_TYPE_METHOD.equals(methodNode.getNodeType())) {
                    return methodNode.getChildren().keySet().iterator().next();
                }
            } else {
                Node nextNode = current.getChildren().get(MATCH_ONE);
                if (nextNode == null) {
                    nextNode = current.getChildren().get(MATCH_ALL);
                }
                if (nextNode != null && NODE_TYPE_MAY_PATH_END.equals(nextNode.getNodeType())) {
                    methodNode = nextNode.getChildren().get(method);
                    if (methodNode != null && NODE_TYPE_METHOD.equals(methodNode.getNodeType())) {
                        return methodNode.getChildren().keySet().iterator().next();
                    }
                }
            }
        }

        String matchRole = null;
        if (current.getData().equals(urlPac[currentFlow])) {
            matchRole = searchPathRoleInChildren(current, urlPac, currentFlow, method);
            if (matchRole != null) {
                return matchRole;
            }
        }
        if (current.getData().equals(MATCH_ONE)) {
            matchRole = searchPathRoleInChildren(current, urlPac, currentFlow - 1, method);
            if (matchRole != null) {
                return matchRole;
            }
            matchRole = searchPathRoleInChildren(current, urlPac, currentFlow, method);
            if (matchRole != null) {
                return matchRole;
            }
        }
        if (current.getData().equals(MATCH_ALL)) {
            matchRole = searchPathRoleInChildren(current, urlPac, currentFlow - 1, method);
            if (matchRole != null) {
                return matchRole;
            }
            matchRole = searchPathRoleInChildren(current, urlPac, currentFlow, method);
            if (matchRole != null) {
                return matchRole;
            }
            matchRole = searchPathRole(current, urlPac, currentFlow + 1, method);
        }
        return matchRole;
    }


    /**
     * Match the next node from the current node
     * @param current current node
     * @param urlPac urlPath arr
     * @param currentFlow current flow
     * @param method http method
     * @return match return roles eg:[role,role2], else return null
     */
    private String searchPathRoleInChildren(Node current, String[] urlPac, int currentFlow,  String method) {
        if (current == null || urlPac == null || currentFlow >= urlPac.length - 1
                || currentFlow < -1 || method == null || "".equals(method)) {
            return null;
        }

        String matchRole = null;
        String nextUrlPac = urlPac[currentFlow + 1];
        if (current.getChildren().containsKey(nextUrlPac)) {
            matchRole = searchPathRole(current.getChildren().get(nextUrlPac), urlPac, currentFlow + 1, method);
            if (matchRole != null) {
                return matchRole;
            }
        }
        if (current.getChildren().containsKey(MATCH_ONE)) {
            Node matchOneNode = current.getChildren().get(MATCH_ONE);
            matchRole = searchPathRole(matchOneNode, urlPac, currentFlow + 1, method);
            if (matchRole != null) {
                return matchRole;
            }
        }
        if (current.getChildren().containsKey(MATCH_ALL)) {
            Node matchAllNode = current.getChildren().get(MATCH_ALL);
            matchRole = searchPathRole(matchAllNode, urlPac, currentFlow + 1, method);
        }
        return matchRole;
    }

    /**
     * Determine whether the pattern does not match pathNode
     * @param pattern pattern eg: * **
     * @param pathNode pathNode
     * @return match return true, else false
     */
    private boolean isNoMatchString(String pattern, String pathNode) {
        if (pattern == null && pathNode == null) {
            return false;
        }
        if (pattern == null || pathNode == null) {
            return true;
        }
        return !(pattern.equals(pathNode) || MATCH_ONE.equals(pattern)
                || MATCH_ALL.equals(pattern));
    }

    /**
     * insert pathNode
     * @param path path = /api/v1/host/detail===GET===[role2,role3,role4]
     */
    private void insertNode(String path, Node rootNode) {
        if (path == null || "".equals(path) || !path.startsWith(URL_PATH_SPLIT)) {
            return;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("sureness - begin insertNode, path is {}", path);
        }
        path = PATH_SPLIT_PATTERN.matcher(path).replaceAll("/");
        // remove the first /
        path = path.substring(1).toLowerCase();
        String[] tmp = path.split("===");
        if (tmp.length != PATH_NODE_NUM_3) {
            return;
        }
        String[] urlPac = tmp[0].split(URL_PATH_SPLIT);
        String method = tmp[1];
        String supportRoles = tmp[2];
        Node current = rootNode;
        Node pre = current;
        // start inserting URL node
        for (String urlData : urlPac) {
            if (!current.getChildren().containsKey(urlData)) {
                current.insertChild(urlData);
            }
            pre = current;
            current = current.getChildren().get(urlData);
        }
        if (MATCH_ONE.equals(current.getData()) || MATCH_ALL.equals(current.getData())) {
            // When the last one is * or **, it may match empty,
            // and the previous one may also be NODE_TYPE_MAY_PATH_END type
            pre.setNodeType(NODE_TYPE_MAY_PATH_END);
        }
        // set node type is NODE_TYPE_MAY_PATH_END
        current.setNodeType(NODE_TYPE_MAY_PATH_END);
        // start insert httpMethod method, if existed, not overwrite and modify the original configuration
        if (!current.getChildren().containsKey(method)) {
            current.insertChild(method, NODE_TYPE_METHOD);
        }
        current = current.getChildren().get(method);
        // Start inserting leaf nodes - supportRoles
        // each resource only mapping a left node, that is, at most one child node under supportRoles--httpMethod
        // if existed, not overwrite and modify the original configuration
        if (current.getChildren().isEmpty()) {
            current.insertChild(supportRoles, NODE_TYPE_FILTER_ROLES);
        }
    }

    /**
     * Dictionary matching tree node
     */
    private static class Node {

        /** current node type **/
        private String nodeType;

        /** current node data **/
        private String data;

        /** children nodes **/
        private Map<String, Node> children;

        private Node(String data, String nodeType) {
            this.data = data;
            this.nodeType = nodeType;
            this.children = new HashMap<>();
        }
        private Node(String data) {
            this.data = data;
            this.nodeType = NODE_TYPE_PATH_NODE;
            this.children = new HashMap<>();
        }

        private void insertChild(String data) {
            this.children.put(data, new Node(data));
        }

        private void insertChild(String data,String nodeType) {
            this.children.put(data, new Node(data, nodeType));
        }

        private String getNodeType() {
            return nodeType;
        }

        private void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        private String getData() {
            return data;
        }

        private void setData(String data) {
            this.data = data;
        }

        private Map<String, Node> getChildren() {
            return children;
        }

        private void setChildren(Map<String, Node> children) {
            this.children = children;
        }

    }
}
