package org.jahia.modules.graphql.jcr.provider;

import graphql.schema.GraphQLObjectType;
import graphql.servlet.GraphQLQueryProvider;

/**
 * A GraphQL query provider to access DX underlying JCR repository.
 */
public class JCRGraphQLQueryProvider implements GraphQLQueryProvider {

    private GraphQLNodeRegistry registry;

    @Override
    public GraphQLObjectType getQuery() {
        return registry.getQuery();
    }


    @Override
    public Object context() {
        return GQLNode.ROOT;
    }

    @Override
    public String getName() {
        return GraphQLNodeRegistry.QUERY_NAME;
    }


    public void setRegistry(GraphQLNodeRegistry registry) {
        this.registry = registry;
    }
}
