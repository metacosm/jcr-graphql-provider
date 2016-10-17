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

import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.StringValue;
import graphql.schema.DataFetchingEnvironment;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.util.List;

/**
 * @author Christophe Laprun
 */
public class ChildrenDataFetcher extends JCRDataFetcher<GQLNode> {

    public ChildrenDataFetcher(JCRQraphQLQueryProvider queryProvider) {
        super(queryProvider);
    }

    @Override
    protected GQLNode perform(DataFetchingEnvironment environment, Session session) throws RepositoryException {
        GQLItems parent = (GQLItems) environment.getSource();
        final Node node = session.getNodeByIdentifier(parent.getParentId());

        final List<Field> fields = environment.getFields();
        for (Field field : fields) {
            final String name = JCRQraphQLQueryProvider.unescape(field.getName());


            final List<Argument> arguments = field.getArguments();
            if (!arguments.isEmpty()) {
                final Argument argument = arguments.get(0);
                final StringValue value = (StringValue) argument.getValue();
                final Node child = node.getNode(value.getValue());
                return new GQLNode(child);
            } else {
                return new GQLNode(node.getNode(name));
            }
        }

        return null;
    }
}
