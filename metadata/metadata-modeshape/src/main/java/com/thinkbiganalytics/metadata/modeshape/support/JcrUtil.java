/**
 *
 */
package com.thinkbiganalytics.metadata.modeshape.support;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.modeshape.jcr.api.JcrTools;

import com.thinkbiganalytics.classnameregistry.ClassNameChangeRegistry;
import com.thinkbiganalytics.metadata.modeshape.JcrMetadataAccess;
import com.thinkbiganalytics.metadata.modeshape.MetadataRepositoryException;
import com.thinkbiganalytics.metadata.modeshape.common.JcrObject;

/**
 * @author Sean Felten
 */
public class JcrUtil {


    /**
     * Checks whether the given mixin node type is in effect for the given node.
     *
     * @param node      the node
     * @param mixinType the mixin node type
     * @return <code>true</code> when the mixin node type is present, <code>false</code> instead.
     */
    public static boolean hasMixinType(Node node, String mixinType) throws RepositoryException {

        for (NodeType nodeType : node.getMixinNodeTypes()) {
            if (nodeType.getName().equals(mixinType)) {
                return true;
            }
        }
        NodeType[] types = node.getPrimaryNodeType().getSupertypes();
        if (types != null) {
            for (NodeType nt : types) {
                if (nt.getName().equals(mixinType)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isVersionable(JcrObject jcrObject) {
        return isVersionable(jcrObject.getNode());
    }

    public static boolean isVersionable(Node node) {
        String name = "";
        boolean versionable = false;
        try {
            name = node.getName();
            versionable = hasMixinType(node, "mix:versionable");
            return versionable;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to check if versionable for Node " + name, e);
        }
    }

    public static boolean isNodeType(Node node, String typeName) {
        try {
            return node.getPrimaryNodeType().isNodeType(typeName);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve the type of node: " + node, e);
        }
    }

    public static List<Node> getNodelist(Node parent, String property) {
        return StreamSupport
                        .stream(getInterableChildren(parent, property).spliterator(), false)
                        .collect(Collectors.toList());
    }
    
    public static boolean hasNode(Node parentNode, String name) {
        try {
            return parentNode.hasNode(name);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to check for the existence of the node named " + name, e);
        }
    }

    public static Node getNode(Node parentNode, String name) {
        try {
            return parentNode.getNode(name);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve the Node named " + name, e);
        }
    }
    
    public static Node createNode(Node parentNode, String name, String nodeType) {
        try {
            return parentNode.addNode(name, nodeType);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to create the Node named " + name, e);
        }
    }
    
    public static boolean removeNode(Node parentNode, String name) {
        try {
            if (parentNode.hasNode(name)) {
                parentNode.getNode(name).remove();
                return true;
            } else {
                return false;
            }
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve the Node named " + name, e);
        }
    }

    public static List<Node> getNodesOfType(Node parentNode, String nodeType) {
        try {
            List<Node> list = new ArrayList<>();
            NodeIterator itr = parentNode.getNodes();
            
            while (itr.hasNext()) {
                Node node = (Node) itr.next();
                if (node.getPrimaryNodeType().isNodeType(nodeType)) {
                    list.add(node);
                }
            }
            
            return list;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to create set of child nodes of type: " + nodeType, e);
        }
    }

    public static Iterable<Node> getInterableChildren(Node parent) {
        return getInterableChildren(parent, null);
    }
    
    public static Iterable<Node> getInterableChildren(Node parent, String name) {
        @SuppressWarnings("unchecked")
        Iterable<Node> itr = () -> { 
                try {
                    return name != null ? parent.getNodes(name) : parent.getNodes();
                } catch (RepositoryException e) {
                    throw new MetadataRepositoryException("Failed to retrieve the child nodes from:  " + parent, e);
                } 
            };
        return itr;
    }


    public static <T extends JcrObject> List<T> getChildrenMatchingNodeType(Node parentNode, String childNodeType, Class<T> type) {

        try {
            String
                query =
                "SELECT child.* from [" + parentNode.getPrimaryNodeType() + "] as parent inner join [" + childNodeType + "] as child ON ISCHILDNODE(child,parent) WHERE parent.[jcr:uuid]  = '"
                + parentNode.getIdentifier() + "'";
            return JcrQueryUtil.find(parentNode.getSession(), query, type);

        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to find Children matching type " + childNodeType, e);
        }

    }
    
    public static <T extends JcrObject> T toJcrObject(Node node, String nodeType, Class<T> type) {
        return toJcrObject(node, nodeType, new DefaultObjectTypeResolver<T>(type));
    }
    
    public static <T extends JcrObject> T toJcrObject(Node node, String nodeType, JcrObjectTypeResolver<T> typeResolver) {
        try {
            if (nodeType == null || node.isNodeType(nodeType)) {
                T entity = ConstructorUtils.invokeConstructor(typeResolver.resolve(node), node);
                return entity;
            } else {
                throw new MetadataRepositoryException("Unable to instanciate object of node type: " + nodeType);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException | RepositoryException e) {
            throw new MetadataRepositoryException("Unable to instanciate object from node: " + node, e);
        }
    }
    
    /**
     * get All Child nodes under a parentNode and create the wrapped JCRObject.
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, Class<T> type) {
        return getJcrObjects(parentNode, null, new DefaultObjectTypeResolver<T>(type));
    }

    /**
     * get All Child nodes under a parentNode matching the name and type, and create the wrapped JCRObject the second argument, name, can be null to get all the nodes under the parent
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, String name, Class<T> type) {
        return getJcrObjects(parentNode, name, null, new DefaultObjectTypeResolver<T>(type));
    }
    
    /**
     * get All Child nodes under a parentNode matching the type and create the wrapped JCRObject the second argument, name, can be null to get all the nodes under the parent
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, NodeType nodeType, Class<T> type) {
        return getJcrObjects(parentNode, nodeType, new DefaultObjectTypeResolver<T>(type));
    }
    
    /**
     * get All Child nodes under a parentNode matching the name and type, returning a wrapped JCRObject the second argument, name, can be null to get all the nodes under the parent
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, String name, NodeType nodeType, Class<T> type) {
        return getJcrObjects(parentNode, name, nodeType, new DefaultObjectTypeResolver<T>(type));
    }
    
    /**
     * get All Child nodes under a parentNode and create the wrapped JCRObject.
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, JcrObjectTypeResolver<T> typeResolver) {
        return getJcrObjects(parentNode, null, null, typeResolver);
    }
    
    /**
     * get All Child nodes under a parentNode of a certain type and create the wrapped JCRObject.
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, NodeType nodeType, JcrObjectTypeResolver<T> typeResolver) {
        return getJcrObjects(parentNode, null, nodeType, typeResolver);
    }

    /**
     * get All Child nodes under a parentNode and create the wrapped JCRObject the second argument, name, can be null to get all the nodes under the parent
     */
    public static <T extends JcrObject> List<T> getJcrObjects(Node parentNode, String name, NodeType nodeType, JcrObjectTypeResolver<T> typeResolver) {
        List<T> list = new ArrayList<>();
        try {

            javax.jcr.NodeIterator nodeItr = null;
            if (StringUtils.isBlank(name)) {
                nodeItr = parentNode.getNodes();
            } else {
                nodeItr = parentNode.getNodes(name);
            }
            if (nodeItr != null) {
                while (nodeItr.hasNext()) {
                    Node n = nodeItr.nextNode();
                    
                    if (nodeType == null || n.isNodeType(nodeType.getName())) {
                        T entity = ConstructorUtils.invokeConstructor(typeResolver.resolve(n), n);
                        list.add(entity);
                    }
                }
            }
        } catch (RepositoryException | InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new MetadataRepositoryException("Failed to retrieve the Node named" + name, e);
        }
        return list;
    }

    /**
     * Get a child node relative to the parentNode and create the Wrapper object
     */
    public static <T extends JcrObject> T getJcrObject(Node parentNode, String name, Class<T> type) {
        T entity = null;
        try {
            Node n = parentNode.getNode(name);
            entity = ConstructorUtils.invokeConstructor(type, n);
        } catch (RepositoryException | InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new MetadataRepositoryException("Failed to retrieve the Node named" + name, e);
        }
        return entity;
    }
    
    /**
     * Get or Create a node relative to the Parent Node; checking out the parent node as necessary.
     */
    public static Node getOrCreateNode(Node parentNode, String name, String nodeType, boolean forUpdate) {
        try {
            if (parentNode.hasNode(name)) {
                if (forUpdate) {
                    JcrMetadataAccess.ensureCheckoutNode(parentNode);
                }
                
                return parentNode.getNode(name);
            } else {
                JcrMetadataAccess.ensureCheckoutNode(parentNode);
                
                return parentNode.addNode(name, nodeType);
            }
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve the Node named" + name, e);
        }
    }
    
    /**
     * Get or Create a node relative to the Parent Node; checking out the parent node as necessary.
     */
    public static Node getOrCreateNode(Node parentNode, String name, String nodeType) {
        return getOrCreateNode(parentNode, name, nodeType, false);
    }

    /**
     * Get or Create a node relative to the Parent Node and return the Wrapper JcrObject
     */
    public static <T extends JcrObject> T getOrCreateNode(Node parentNode, String name, String nodeType, Class<T> type) {
        return getOrCreateNode(parentNode, name, nodeType, type, null);
    }

    /**
     * Get or Create a node relative to the Parent Node and return the Wrapper JcrObject
     */
    public static <T extends JcrObject> T getOrCreateNode(Node parentNode, String name, String nodeType, Class<T> type, Object... constructorArgs) {
        T entity = null;
        try {
            JcrTools tools = new JcrTools();

            //if versionable checkout
            //   if(isVersionable(parentNode)){
            //     JcrVersionUtil.checkout(parentNode);
            //  }
            Node n = tools.findOrCreateChild(parentNode, name, nodeType);
            entity = createJcrObject(n, type, constructorArgs);
            //save ??
            //   JcrVersionUtil.checkinRecursively(n);
            //  if(isVersionable(parentNode)){
            //       JcrVersionUtil.checkin(parentNode);
            //    }
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve the Node named" + name, e);
        }
        return entity;
    }

    public static <T extends Serializable> T getGenericJson(Node parent, String nodeName) {
       return getGenericJson(parent,nodeName,false);
    }

    public static <T extends Serializable> T getGenericJson(Node parent, String nodeName, boolean allowClassNotFound) {
        try {
            Node jsonNode = parent.getNode(nodeName);

            return getGenericJson(jsonNode,allowClassNotFound);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to deserialize generic JSON node", e);
        }
    }

    
    public static <T extends Serializable> T getGenericJson(Node jsonNode) {
      return getGenericJson(jsonNode,false);
    }


    public static <T extends Serializable> T getGenericJson(Node jsonNode, boolean allowClassNotFound) {
        try {
            String className = jsonNode.getProperty("tba:type").getString();
            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) ClassNameChangeRegistry.findClass(className);

            return JcrPropertyUtil.getJsonObject(jsonNode, "tba:json", type);
        } catch (RepositoryException | ClassNotFoundException | ClassCastException e) {
            if(e instanceof ClassNotFoundException && allowClassNotFound){
                //swallow this exception
                return null;
            }
            else {
                throw new MetadataRepositoryException("Failed to deserialize generic JSON property", e);
            }
        }
    }

    
    public static <T extends Serializable> void addGenericJson(Node parent, String nodeName, T object) {
        try {
            Node jsonNode = parent.addNode(nodeName, "tba:genericJson");
            
            JcrPropertyUtil.setProperty(jsonNode, "tba:type", object.getClass().getName());
            JcrPropertyUtil.setJsonObject(jsonNode, "tba:json", object);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to add a generic JSON node to the parent node: " + parent, e);
        }
    }
    
    /**
     * Create a new JcrObject (Wrapper Object) that invokes a constructor with at least parameter of type Node
     */
    public static <T extends JcrObject> T addJcrObject(Node parent, String name, String nodeType, Class<T> type) {
        return addJcrObject(parent, name, nodeType, type, new Object[0]);
    }
    
    /**
     * Create a new JcrObject (Wrapper Object) that invokes a constructor with at least parameter of type Node
     */
    public static <T extends JcrObject> T addJcrObject(Node parent, String name, String nodeType, Class<T> type, Object... constructorArgs) {
        try {
            Node child = parent.addNode(name, nodeType);
            return createJcrObject(child, type, constructorArgs);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to add new createJcrObject child node " + type, e);
        }
    }

    /**
     * Create a new JcrObject (Wrapper Object) that invokes a constructor with at least parameter of type Node
     */
    public static <T extends JcrObject> T createJcrObject(Node node, Class<T> type) {
        return createJcrObject(node, type, new Object[0]);
    }

    public static Map<String,Object> jcrObjectAsMap(JcrObject obj){
        String nodeName = obj.getNodeName();
        String path = obj.getPath();
        String identifier = null;
        try {
            identifier = obj.getObjectId();
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
        String type = obj.getTypeName();
        Map<String,Object> props = obj.getProperties();
        Map<String, Object> finalProps = new HashMap<>();
        if (props != null) {
            finalProps.putAll(finalProps);
        }
        finalProps.put("nodeName", nodeName);
        if (identifier != null) {
            finalProps.put("nodeIdentifier", identifier);
        }
        finalProps.put("nodePath", path);
        finalProps.put("nodeType", type);
        return finalProps;
    }

    public static Map<String, Object> nodeAsMap(Node obj) throws RepositoryException {

        String nodeName = obj.getName();
        String path = obj.getPath();
        String identifier = obj.getIdentifier();

        String type = obj.getPrimaryNodeType() != null ? obj.getPrimaryNodeType().getName() : "";
        Map<String, Object> props = JcrPropertyUtil.getProperties(obj);
        Map<String,Object> finalProps = new HashMap<>();
        if(props != null){
            finalProps.putAll(finalProps);
        }
        finalProps.put("nodeName",nodeName);
        if(identifier != null) {
            finalProps.put("nodeIdentifier", identifier);
        }
        finalProps.put("nodePath",path);
        finalProps.put("nodeType",type);
        return finalProps;
    }

    /**
     * Create a new JcrObject (Wrapper Object) that invokes a constructor with at least parameter of type Node
     */
    public static <T extends JcrObject> T createJcrObject(Node node, Class<T> type, Object... constructorArgs) {
        T obj = constructNodeObject(node, type, constructorArgs);
        if(JcrUtil.isVersionable(obj) && !node.isNew()){
            try {
                String versionName = JcrVersionUtil.getBaseVersion(node).getName();
                obj.setVersionName(versionName);
                obj.setVersionableIdentifier(JcrVersionUtil.getBaseVersion(node).getContainingHistory().getVersionableIdentifier());
            } catch (RepositoryException e) {
              //this is fine... versionName is a nice to have on the object
            }
        }
        return obj;
    }

    /**
     * Create a new Node Wrapper Object that invokes a constructor with at least parameter of type Node
     */
    public static <T extends Object> T constructNodeObject(Node node, Class<T> type, Object... constructorArgs) {
        T entity = null;
        try {
            if (constructorArgs != null) {
                constructorArgs = ArrayUtils.add(constructorArgs, 0, node);
            } else {
                constructorArgs = new Object[]{node};
            }

            entity = ConstructorUtils.invokeConstructor(type, constructorArgs);
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new MetadataRepositoryException("Failed to createJcrObject for node " + type, e);
        }
        return entity;
    }
    
    public static <T extends JcrObject> T getReferencedObject(Node node, String property, Class<T> type) {
        return getReferencedObject(node, property, new DefaultObjectTypeResolver<T>(type));
    }

    public static <T extends JcrObject> T getReferencedObject(Node node, String property, JcrObjectTypeResolver<T> typeResolver) {
        try {
            Property prop = node.getProperty(property);
            return createJcrObject(prop.getNode(), typeResolver.resolve(prop.getNode()));
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to dereference object of type using: " + typeResolver, e);
        }
        
    }

    /**
     * Creates an object set from the nodes of a same-name sibling property
     */
    public static <T extends JcrObject> Set<T> getPropertyObjectSet(Node parentNode, String property, Class<T> objClass) {
        try {
            Set<T> set = new HashSet<>();
            NodeIterator itr = parentNode.getNodes(property);
            while (itr.hasNext()) {
                Node objNode = (Node) itr.next();
                T obj = ConstructorUtils.invokeConstructor(objClass, objNode);
                set.add(obj);
            }
            return set;
        } catch (RepositoryException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new MetadataRepositoryException("Failed to create set of child objects from property: " + property, e);
        }
    }

    public static NodeType getNodeType(Session session, String typeName) {
        try {
            return session.getWorkspace().getNodeTypeManager().getNodeType(typeName);
        } catch (NoSuchNodeTypeException e) {
            throw new MetadataRepositoryException("No node type exits named: " + typeName, e);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve node type named: " + typeName, e);
        }
    }
    
    

    private static class DefaultObjectTypeResolver<T extends JcrObject> implements JcrObjectTypeResolver<T> {
        
        private final Class<? extends T> type;
        
        public DefaultObjectTypeResolver(Class<? extends T> type) {
            super();
            this.type = type;
        }

        @Override
        public Class<? extends T> resolve(Node node) {
            return this.type;
        }
    }
    
}
