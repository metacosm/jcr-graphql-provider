/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 * http://www.jahia.com
 *
 * Copyright (C) 2002-2016 Jahia Solutions Group SA. All rights reserved.
 *
 * THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 * 1/GPL OR 2/JSEL
 *
 * 1/ GPL
 * ==================================================================================
 *
 * IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * 2/ JSEL - Commercial and Supported Versions of the program
 * ===================================================================================
 *
 * IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 * Alternatively, commercial and supported versions of the program - also known as
 * Enterprise Distributions - must be used in accordance with the terms and conditions
 * contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.graphql.jcr.provider;

import graphql.schema.*;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;

/**
 * @author Christophe Laprun
 */
public class GraphQLNodeRegistry {
    private static final GraphQLEnumType WORKSPACES_ENUM = GraphQLEnumType.newEnum().name("workspaces").value("DEFAULT")
            .value("LIVE")
            .build();
    public static final String QUERY_NAME = "nodes";
    private static Logger logger = LoggerFactory.getLogger(JCRQraphQLQueryProvider.class);
    private static Matcher VALID_NAME = Pattern.compile("^[_a-zA-Z][_a-zA-Z0-9]*$").matcher("");

    private static final Locale DEFAULT = Locale.getDefault();

    private JCRSessionFactory repository;
    private NodeTypeRegistry nodeTypeRegistry;
    private static final Map<String, GraphQLOutputType> knownTypes = new ConcurrentHashMap<>();
    private final Set<String> unresolved = new HashSet<>();
    private final DataFetcher nodeFetcher = new NodeDataFetcher(this);
    private final DataFetcher childrenFetcher = new ChildrenDataFetcher(this);
    private final DataFetcher propertiesFetcher = new PropertiesDataFetcher(this);
    private static final TypeResolver itemResolver = new TypeResolver() {
        @Override
        public GraphQLObjectType getType(Object object) {
            GQLItem item = (GQLItem) object;
            final String typeName = item.getType();
            GraphQLOutputType type = knownTypes.get(escape(typeName));

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
    private static final GraphQLFieldDefinition idField = newFieldDefinition()
            .name("id")
            .type(GraphQLID)
            .build();
    private static final GraphQLInterfaceType genericType = GraphQLInterfaceType.newInterface()
            .name("generic")
            .typeResolver(itemResolver)
            .field(nameField)
            .field(pathField)
            .field(typeField)
            .field(idField)
            .description("A generic interface to minimal node information")
            .build();

    public void setRepository(JCRSessionFactory repository) {
        this.repository = repository;
    }

    public JCRSessionFactory getRepository() {
        return repository;
    }

    public void setNodeTypeRegistry(NodeTypeRegistry nodeTypeRegistry) {
        this.nodeTypeRegistry = nodeTypeRegistry;
    }

    GraphQLObjectType getQuery() {
        final long start = System.currentTimeMillis();
        final GraphQLUnionType.Builder nodeTypeBuilder = newUnionType().name("node").typeResolver(itemResolver);
        final GraphQLObjectType.Builder typesBuilder = newObject()
                .name(QUERY_NAME);

        final NodeTypeRegistry.JahiaNodeTypeIterator nodeTypes = nodeTypeRegistry.getAllNodeTypes();
        for (ExtendedNodeType type : nodeTypes) {
            final String typeName = escape(type.getName());
            if (!knownTypes.containsKey(typeName)) {
                final GraphQLObjectType gqlType = createGraphQLType(type, typeName);

                nodeTypeBuilder.possibleType(gqlType);

                typesBuilder.field(newFieldDefinition()
                        .name(typeName)
                        .argument(newArgument().name("path").type(GraphQLString).build())
                        .argument(newArgument().name("id").type(GraphQLID).build())
                        .argument(newArgument().name("ws").type(WORKSPACES_ENUM).build())
                        .argument(newArgument().name("lang").type(GraphQLString).build())
                        .dataFetcher(nodeFetcher)
                        .type(gqlType)
                        .build());

                knownTypes.put(typeName, gqlType);
            } else {
                logger.debug("Already generated {}", typeName);
            }
        }

        typesBuilder.field(newFieldDefinition()
                .name("node")
                .type(nodeTypeBuilder.build())
                .argument(newArgument().name("path").type(GraphQLString).build())
                .argument(newArgument().name("id").type(GraphQLID).build())
                .argument(newArgument().name("ws").type(WORKSPACES_ENUM).build())
                .argument(newArgument().name("lang").type(GraphQLString).build())
                .dataFetcher(nodeFetcher)
                .build());
        final GraphQLObjectType types = typesBuilder.build();

        long duration = System.currentTimeMillis() - start;
        logger.info("Generated " + knownTypes.size() + " types in " + duration + " ms");
        return types;
    }

    private static boolean validateNames() {
        return SettingsBean.getInstance().isDevelopmentMode();
    }

    static String escape(String name) {
        name = name.replace(":", "__").replace(".", "___");
        if (validateNames() && !VALID_NAME.reset(name).matches()) {
            logger.error("Invalid name: " + name);
        }
        return name;
    }

    static String unescape(String name) {
        return name.replace("___", ".").replace("__", ":");
    }

    private GraphQLObjectType createGraphQLType(ExtendedNodeType type, String typeName) {
        final String escapedTypeName = escape(typeName);
        logger.debug("Creating {}", escapedTypeName);
        unresolved.add(escapedTypeName);

        final NodeDefinition[] children = type.getChildNodeDefinitions();
        final PropertyDefinition[] properties = type.getPropertyDefinitions();

        final List<GraphQLFieldDefinition> fields = new ArrayList<>(2);
        if (children.length > 0) {
            final Set<String> multipleChildTypes = new HashSet<>(children.length);
            final GraphQLFieldDefinition.Builder childrenField = newFieldDefinition().name("children");
            final GraphQLObjectType.Builder childrenType = newObject().name(escapedTypeName + "Children");
            for (NodeDefinition child : children) {
                final String childName = child.getName();

                if (!"*".equals(childName)) {
                    final String escapedChildName = escape(childName);
                    final String childTypeName = getChildTypeName(child);
                    GraphQLOutputType gqlChildType = getExistingTypeOrRef(childTypeName);
                    childrenType.field(newFieldDefinition()
                            .name(escapedChildName)
                            .type(gqlChildType)
                            .dataFetcher(childrenFetcher)
                            .build());
                } else {
                    final String childTypeName = getChildTypeName(child);
                    if (!multipleChildTypes.contains(childTypeName)) {
                        final String escapedChildTypeName = escape(childTypeName);
                        childrenType.field(
                                newFieldDefinition()
                                        .name(escapedChildTypeName)
                                        .type(getExistingTypeOrRef(childTypeName))
                                        .dataFetcher(childrenFetcher)
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
                final String propName = property.getName();
                final int propertyType = property.getRequiredType();
                final boolean multiple = property.isMultiple();
                if (!"*".equals(propName)) {
                    final String escapedPropName = escape(propName);
                    propertiesType.field(newFieldDefinition()
                            .name(escapedPropName)
                            .dataFetcher(propertiesFetcher)
                            .type(getGraphQLType(propertyType, multiple))
                            .build());
                } else {
                    final String propertyTypeName = PropertyType.nameFromValue(propertyType);
                    final String fieldName = propertyTypeName + "Properties";
                    if (!multiplePropertyTypes.contains(fieldName)) {
                        propertiesType.field(
                                newFieldDefinition()
                                        .name(fieldName)
                                        .type(getGraphQLType(propertyType, false))
                                        .dataFetcher(propertiesFetcher)
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
        fields.add(idField);

        final String description = type.getDescription(DEFAULT);

        final GraphQLObjectType objectType = new GraphQLObjectType(escapedTypeName, description, fields, Collections
                .singletonList(genericType));

        unresolved.remove(escapedTypeName);

        return objectType;
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

    private GraphQLOutputType getExistingTypeOrRef(String unescapedChildTypeName) {
        final String escapedChildName = escape(unescapedChildTypeName);
        GraphQLOutputType gqlChildType = knownTypes.get(escapedChildName);
        if (gqlChildType == null) {
            if (unresolved.contains(escapedChildName)) {
                gqlChildType = new GraphQLTypeReference(escapedChildName);
            } else {
                try {
                    final ExtendedNodeType childType = nodeTypeRegistry.getNodeType(unescapedChildTypeName);
                    gqlChildType = createGraphQLType(childType, unescapedChildTypeName);
                    knownTypes.put(escapedChildName, gqlChildType);
                } catch (NoSuchNodeTypeException e) {
                    throw new RuntimeException(e);
                }
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
}
