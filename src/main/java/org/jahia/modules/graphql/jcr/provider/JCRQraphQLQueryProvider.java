package org.jahia.modules.graphql.jcr.provider;

import graphql.schema.*;
import graphql.servlet.GraphQLQueryProvider;
import org.jahia.api.Constants;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;

/**
 * A GraphQL query provider to access DX underlying JCR repository.
 */
public class JCRQraphQLQueryProvider implements GraphQLQueryProvider {

    private static Logger logger = LoggerFactory.getLogger(JCRQraphQLQueryProvider.class);

    private static final Locale DEFAULT = Locale.getDefault();

    private Repository repository;
    private NodeTypeRegistry nodeTypeRegistry;
    private final Map<String, GraphQLOutputType> knownTypes = new ConcurrentHashMap<>();
    private final DataFetcher nodeFetcher = new NodeDataFetcher(this);
    private final TypeResolver itemResolver = new TypeResolver() {
        @Override
        public GraphQLObjectType getType(Object object) {
            GQLItem item = (GQLItem) object;
            final String typeName = item.getType();
            GraphQLOutputType type = knownTypes.get(typeName);
            if (type == null) {
                try {
                    final ExtendedNodeType nodeType = nodeTypeRegistry.getNodeType(typeName);
                    type = createGraphQLType(nodeType, typeName);
                    knownTypes.put(typeName, type);
                } catch (NoSuchNodeTypeException e) {
                    throw new IllegalArgumentException("Unknown type " + typeName);
                }
            }

            if (type instanceof GraphQLObjectType) {
                return (GraphQLObjectType) type;
            } else {
                throw new IllegalArgumentException("Couldn't resolve item " + item + " as a GraphQLObjectType");
            }
        }
    };

    private static final GraphQLFieldDefinition nameField = newFieldDefinition()
            .name("name")
            .type(GraphQLString)
            .build();
    private static final GraphQLFieldDefinition pathField = newFieldDefinition()
            .name("path")
            .type(GraphQLString)
            .build();
    private static final GraphQLFieldDefinition typeField = newFieldDefinition()
            .name("type")
            .type(GraphQLString)
            .build();

    @Override
    public GraphQLObjectType getQuery() {
        final GraphQLUnionType.Builder nodeTypeBuilder = newUnionType().name("node").typeResolver(itemResolver);
        final GraphQLObjectType.Builder typesBuilder = newObject()
                .name("nodes");

        try {
            final ExtendedNodeType type = nodeTypeRegistry.getNodeType("jnt:page");
            final String typeName = escape(type.getName());
            final GraphQLObjectType gqlType = createGraphQLType(type, typeName);

            nodeTypeBuilder.possibleType(gqlType);

            typesBuilder.field(newFieldDefinition()
                    .name(typeName)
                    .type(gqlType)
                    .build());

            knownTypes.put(typeName, gqlType);

            /*final NodeTypeIterator nodeTypes = nodeTypeRegistry.getPrimaryNodeTypes();
            while (nodeTypes.hasNext()) {
                final ExtendedNodeType type = (ExtendedNodeType) nodeTypes.nextNodeType();
                final String typeName = type.getName();

                if (typeName.equals("jnt:page") && !knownTypes.containsKey(typeName)) {
                    final GraphQLOutputType gqlType = createGraphQLType(type, typeName);

                    typesBuilder.field(newFieldDefinition()
                            .name(typeName)
                            .type(gqlType)
                            .build());

                    knownTypes.put(typeName, gqlType);
                }
            }*/

        } catch (RepositoryException e) {
            e.printStackTrace();
        }

        typesBuilder.field(newFieldDefinition()
                .name("node")
                .type(nodeTypeBuilder.build())
                .argument(newArgument().name("path").type(GraphQLString).build())
                .dataFetcher(nodeFetcher)
                .build());
        return typesBuilder.build();
    }

    private String escape(String name) {
        return name.replace(':', '_');
    }

    private GraphQLObjectType createGraphQLType(ExtendedNodeType type, String typeName) {
        final String escapedTypeName = escape(typeName);
        logger.info("Creating " + escapedTypeName);

        final NodeDefinition[] children = type.getChildNodeDefinitions();
        final PropertyDefinition[] properties = type.getPropertyDefinitions();

        final List<GraphQLFieldDefinition> fields = new ArrayList<>(2);
        if (children.length > 0) {
            final Set<String> multipleChildTypes = new HashSet<>(children.length);
            final GraphQLFieldDefinition.Builder childrenField = newFieldDefinition().name("children");
            final GraphQLObjectType.Builder childrenType = newObject().name(escapedTypeName + "Children");
            for (NodeDefinition child : children) {
                final String childName = escape(child.getName());

                if (!"*".equals(childName)) {
                    final String childTypeName = getChildTypeName(child);
                    GraphQLOutputType gqlChildType = getExistingTypeOrRef(childTypeName);
                    childrenType.field(newFieldDefinition()
                            .name(childName)
                            .type(gqlChildType)
                            .build());
                } else {
                    final String childTypeName = getChildTypeName(child);
                    if (!multipleChildTypes.contains(childTypeName)) {
                        final String escapedChildTypeName = escape(childTypeName);
                        logger.info("Creating children/" + escapedChildTypeName + "(name) for type " + typeName);
                        childrenType.field(
                                newFieldDefinition()
                                        .name(escapedChildTypeName)
                                        .type(getExistingTypeOrRef(childTypeName))
                                        .argument(newArgument()
                                                .name("name")
                                                .type(GraphQLString)
                                                .build())
                                        .build()
                        );
                        multipleChildTypes.add(childTypeName);
                    }
                }
            }
            childrenField.type(childrenType.build());
            fields.add(childrenField.build());
        }

        if (properties.length > 0) {
            final Set<String> multiplePropertyTypes = new HashSet<>(properties.length);
            final GraphQLFieldDefinition.Builder propertiesField = newFieldDefinition().name("properties");
            final GraphQLObjectType.Builder propertiesType = newObject().name(escapedTypeName + "Properties");
            for (PropertyDefinition property : properties) {
                final String propertyName = escape(property.getName());
                final int propertyType = property.getRequiredType();
                final boolean multiple = property.isMultiple();
                if (!"*".equals(propertyName)) {
                    propertiesType.field(newFieldDefinition()
                            .name(propertyName)
                            .type(getGraphQLType(propertyType, multiple))
                            .build());
                } else {
                    final String propertyTypeName = PropertyType.nameFromValue(propertyType);
                    final String fieldName = propertyTypeName + "Properties";
                    if (!multiplePropertyTypes.contains(fieldName)) {
                        logger.info("Creating " + fieldName + "(name) for type " + typeName);
                        propertiesType.field(
                                newFieldDefinition()
                                        .name(fieldName)
                                        .type(getGraphQLType(propertyType, multiple))
                                        .argument(newArgument()
                                                .name("name")
                                                .type(GraphQLString)
                                                .build())
                                        .build()
                        );
                        multiplePropertyTypes.add(fieldName);
                    }
                }
            }
            propertiesField.type(propertiesType.build());
            fields.add(propertiesField.build());
        }

        fields.add(nameField);
        fields.add(pathField);
        fields.add(typeField);
        fields.add(newFieldDefinition()
                .name("id")
                .type(GraphQLID)
                .build());

        final String description = type.getDescription(DEFAULT);

        /*if (type.isAbstract()) {
            return new GraphQLInterfaceType(typeName, description, fields, itemResolver);
        } else {*/
        final Set<ExtendedNodeType> superTypes = type.getSupertypeSet();
        List<GraphQLInterfaceType> interfaces = new ArrayList<>(superTypes.size());
            /*for (ExtendedNodeType superType : superTypes) {
                final GraphQLOutputType typeOrRef = getExistingTypeOrRef(superType.getName());
                if (typeOrRef instanceof GraphQLInterfaceType) {
                    interfaces.add((GraphQLInterfaceType) typeOrRef);
                }
            }*/

        return new GraphQLObjectType(escapedTypeName, description, fields, interfaces);
//        }
    }

    private String getChildTypeName(NodeDefinition child) {
        String childTypeName = child.getDefaultPrimaryTypeName();
        if (childTypeName == null) {
            final String[] primaryTypeNames = child.getRequiredPrimaryTypeNames();
            if (primaryTypeNames.length > 1) {
                // todo: do something here
                logger.warn("Multiple primary types (" + primaryTypeNames +
                        ") for child " + child.getName() + " of type "
                        + child.getDeclaringNodeType().getName());
                childTypeName = Constants.NT_BASE;
            } else {
                childTypeName = primaryTypeNames[0];
            }
        }
        return childTypeName;
    }

    private GraphQLOutputType getExistingTypeOrRef(String childTypeName) {
        GraphQLOutputType gqlChildType = knownTypes.get(childTypeName);
        if (gqlChildType == null) {
            try {
                final ExtendedNodeType childType = nodeTypeRegistry.getNodeType(childTypeName);
                gqlChildType = createGraphQLType(childType, childTypeName);
                knownTypes.put(childTypeName, gqlChildType);
            } catch (NoSuchNodeTypeException e) {
                throw new RuntimeException(e);
            }
        }
        return gqlChildType;
    }

    private GraphQLOutputType getGraphQLType(int jcrPropertyType, boolean multiValued) {
        GraphQLScalarType type;
        switch (jcrPropertyType) {
            case PropertyType.BOOLEAN:
                type = GraphQLBoolean;
                break;
            case PropertyType.DATE:
            case PropertyType.DECIMAL:
            case PropertyType.LONG:
                type = GraphQLLong;
                break;
            case PropertyType.DOUBLE:
                type = GraphQLFloat;
                break;
            case PropertyType.BINARY:
            case PropertyType.NAME:
            case PropertyType.PATH:
            case PropertyType.REFERENCE:
            case PropertyType.STRING:
            case PropertyType.UNDEFINED:
            case PropertyType.URI:
            case PropertyType.WEAKREFERENCE:
                type = GraphQLString;
                break;
            default:
                logger.warn("Couldn't find equivalent GraphQL type for "
                        + PropertyType.nameFromValue(jcrPropertyType)
                        + " property type will use string type instead!");
                type = GraphQLString;
        }

        return multiValued ? new GraphQLList(type) : type;
    }

    @Override
    public Object context() {
        Session session = null;
        try {
            session = getRepository().login(new SimpleCredentials("root", "root1234".toCharArray()));
            Node node = session.getNode("/");
            return new GQLNode(node);
        } catch (RepositoryException e) {
            logger.error("Couldn't retrieve root node", e);
            return null;
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setNodeTypeRegistry(NodeTypeRegistry nodeTypeRegistry) {
        this.nodeTypeRegistry = nodeTypeRegistry;
    }

}
