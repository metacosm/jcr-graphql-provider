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

import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.util.List;

/**
 * @author Christophe Laprun
 */
class NodeDataFetcher implements DataFetcher {
    private static Logger logger = LoggerFactory.getLogger(NodeDataFetcher.class);

    private final JCRQraphQLQueryProvider queryProvider;

    NodeDataFetcher(JCRQraphQLQueryProvider queryProvider) {
        this.queryProvider = queryProvider;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        final String id = environment.getArgument("id");
        final String path = environment.getArgument("path");

        if (id == null && path == null) {
            throw new IllegalArgumentException("Should provide at least a node path or identifier");
        }

        Session session = null;
        try {
            session = queryProvider.getRepository().login(new SimpleCredentials("root", "root1234".toCharArray()));
            Node node;
            if (id != null) {
                node = session.getNodeByIdentifier(id);
            } else {
                node = session.getNode(path);
            }

            GQLNode gqlNode = new GQLNode(node);

            final List<Field> fields = environment.getFields();
            for (Field field : fields) {

            }

            return gqlNode;
        } catch (RepositoryException e) {
            logger.error("Couldn't retrieve node", e);
            return null;
        } finally {
            if(session != null) {
                session.logout();
            }
        }
    }
}
